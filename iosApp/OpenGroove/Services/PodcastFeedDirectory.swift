import CryptoKit
import Foundation

enum PodcastFeedError: LocalizedError, Sendable {
    case invalidURL
    case unavailable
    case invalidFeed
    case noPlayableEpisodes
    case feedTooLarge

    var errorDescription: String? {
        switch self {
        case .invalidURL: "The podcast feed must use HTTP or HTTPS."
        case .unavailable: "Could not reach the publisher's podcast feed."
        case .invalidFeed: "The publisher returned an invalid RSS or Atom feed."
        case .noPlayableEpisodes: "No playable audio episodes were found in this feed."
        case .feedTooLarge: "This podcast feed is too large to process safely."
        }
    }
}

struct PodcastFeedDirectory: Sendable {
    func load(feedURL: URL, fallback: PodcastShow? = nil) async throws -> PodcastFeed {
        guard feedURL.isPublicHTTPURL else { throw PodcastFeedError.invalidURL }
        var request = URLRequest(url: feedURL)
        request.timeoutInterval = 22
        request.setValue(
            "application/rss+xml, application/atom+xml, application/xml, text/xml",
            forHTTPHeaderField: "Accept"
        )
        request.setValue("OpenGroove/0.2 iOS publisher feed reader", forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
                throw PodcastFeedError.unavailable
            }
            guard data.count <= 8 * 1_024 * 1_024 else { throw PodcastFeedError.feedTooLarge }
            return try parse(data, feedURL: feedURL, fallback: fallback)
        } catch let error as PodcastFeedError {
            throw error
        } catch {
            throw PodcastFeedError.unavailable
        }
    }

    func parse(_ data: Data, feedURL: URL, fallback: PodcastShow? = nil) throws -> PodcastFeed {
        let delegate = PodcastXMLDelegate(feedURL: feedURL, fallback: fallback)
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else { throw PodcastFeedError.invalidFeed }
        return try delegate.feed()
    }
}

private final class PodcastXMLDelegate: NSObject, XMLParserDelegate {
    private let feedURL: URL
    private let fallback: PodcastShow?
    private var textStack: [String] = []
    private var inEpisode = false

    private var channelTitle = ""
    private var channelAuthor = ""
    private var channelDescription = ""
    private var channelArtworkURL: URL?
    private var channelWebsiteURL: URL?
    private var currentEpisode = EpisodeBuilder()
    private var episodes: [PodcastEpisode] = []

    init(feedURL: URL, fallback: PodcastShow?) {
        self.feedURL = feedURL
        self.fallback = fallback
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let element = normalized(qName ?? elementName)
        textStack.append("")
        if element == "item" || element == "entry" {
            inEpisode = true
            currentEpisode = EpisodeBuilder()
            return
        }

        if inEpisode {
            switch element {
            case "enclosure":
                currentEpisode.audioURL = publicURL(attributeDict["url"])
                currentEpisode.mimeType = attributeDict["type"] ?? ""
            case "link":
                let href = publicURL(attributeDict["href"])
                let relation = attributeDict["rel"]?.lowercased() ?? ""
                let type = attributeDict["type"]?.lowercased() ?? ""
                if relation == "enclosure" || type.hasPrefix("audio/") {
                    currentEpisode.audioURL = href
                    currentEpisode.mimeType = attributeDict["type"] ?? currentEpisode.mimeType
                } else if let href {
                    currentEpisode.websiteURL = href
                }
            case "image", "thumbnail":
                currentEpisode.artworkURL = publicURL(attributeDict["href"] ?? attributeDict["url"])
            default:
                break
            }
        } else {
            switch element {
            case "link":
                if let href = publicURL(attributeDict["href"]) { channelWebsiteURL = href }
            case "image":
                if let artwork = publicURL(attributeDict["href"] ?? attributeDict["url"]) {
                    channelArtworkURL = artwork
                }
            default:
                break
            }
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        for index in textStack.indices { textStack[index] += string }
    }

    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) {
        let text = String(data: CDATABlock, encoding: .utf8) ?? ""
        for index in textStack.indices { textStack[index] += text }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let name = normalized(qName ?? elementName)
        let text = (textStack.popLast() ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

        if (name == "item" || name == "entry") && inEpisode {
            if episodes.count < 250,
               let episode = currentEpisode.build(
                   feedURL: feedURL,
                   showTitle: channelTitle.ifEmpty(fallback?.title ?? "Podcast"),
                   showAuthor: channelAuthor.ifEmpty(fallback?.author ?? ""),
                   showArtworkURL: channelArtworkURL ?? fallback?.artworkURL
               ) {
                episodes.append(episode)
            }
            inEpisode = false
            return
        }

        if inEpisode {
            switch name {
            case "title": currentEpisode.title = text
            case "guid", "id": currentEpisode.guid = text
            case "description", "summary", "encoded": currentEpisode.description = cleanPodcastText(text)
            case "author", "creator": currentEpisode.author = text
            case "pubdate", "published", "updated": currentEpisode.publishedAt = parsePodcastDate(text)
            case "duration": currentEpisode.duration = parsePodcastDuration(text)
            case "link":
                if currentEpisode.websiteURL == nil { currentEpisode.websiteURL = publicURL(text) }
            default: break
            }
        } else {
            switch name {
            case "title": if channelTitle.isEmpty { channelTitle = text }
            case "author", "creator": if channelAuthor.isEmpty { channelAuthor = text }
            case "description", "subtitle":
                if channelDescription.isEmpty { channelDescription = cleanPodcastText(text) }
            case "link": if channelWebsiteURL == nil { channelWebsiteURL = publicURL(text) }
            case "url": if channelArtworkURL == nil { channelArtworkURL = publicURL(text) }
            default: break
            }
        }
    }

    func feed() throws -> PodcastFeed {
        var seen = Set<String>()
        let uniqueEpisodes = episodes.filter { seen.insert($0.id).inserted }
        guard !uniqueEpisodes.isEmpty else { throw PodcastFeedError.noPlayableEpisodes }
        let show = PodcastShow(
            catalogID: fallback?.catalogID ?? "",
            title: channelTitle.ifEmpty(fallback?.title ?? "Podcast"),
            author: channelAuthor.ifEmpty(fallback?.author ?? ""),
            description: channelDescription.ifEmpty(fallback?.description ?? ""),
            feedURL: feedURL,
            artworkURL: channelArtworkURL ?? fallback?.artworkURL,
            websiteURL: channelWebsiteURL ?? fallback?.websiteURL,
            genre: fallback?.genre ?? "",
            country: fallback?.country ?? ""
        )
        return PodcastFeed(show: show, episodes: uniqueEpisodes)
    }

    private func normalized(_ name: String) -> String {
        name.split(separator: ":").last.map(String.init)?.lowercased() ?? name.lowercased()
    }

    private func publicURL(_ value: String?) -> URL? {
        guard let value, let url = URL(string: value.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.isPublicHTTPURL else { return nil }
        return url
    }
}

private struct EpisodeBuilder {
    var guid = ""
    var title = ""
    var author = ""
    var description = ""
    var audioURL: URL?
    var websiteURL: URL?
    var artworkURL: URL?
    var mimeType = ""
    var publishedAt: Date?
    var duration: TimeInterval = 0

    func build(
        feedURL: URL,
        showTitle: String,
        showAuthor: String,
        showArtworkURL: URL?
    ) -> PodcastEpisode? {
        guard !title.isEmpty, let audioURL, audioURL.isPublicHTTPURL else { return nil }
        return PodcastEpisode(
            id: stableEpisodeID(feedURL: feedURL, guid: guid, audioURL: audioURL),
            feedURL: feedURL,
            guid: guid,
            title: title,
            showTitle: showTitle,
            author: author.ifEmpty(showAuthor),
            description: description,
            audioURL: audioURL,
            websiteURL: websiteURL,
            artworkURL: artworkURL ?? showArtworkURL,
            mimeType: mimeType,
            publishedAt: publishedAt,
            duration: duration,
            position: 0,
            completed: false
        )
    }
}

private func stableEpisodeID(feedURL: URL, guid: String, audioURL: URL) -> String {
    let identity = feedURL.absoluteString + "\u{001F}" + (guid.isEmpty ? audioURL.absoluteString : guid)
    return SHA256.hash(data: Data(identity.utf8)).map { String(format: "%02x", $0) }.joined()
}

func parsePodcastDuration(_ value: String) -> TimeInterval {
    let rawParts = value.trimmingCharacters(in: .whitespacesAndNewlines)
        .split(separator: ":", omittingEmptySubsequences: false)
    guard (1...3).contains(rawParts.count) else { return 0 }
    var parts: [Double] = []
    for rawPart in rawParts {
        guard let part = Double(rawPart), part.isFinite, part >= 0 else { return 0 }
        parts.append(part)
    }
    if parts.count >= 2, parts.last! >= 60 { return 0 }
    if parts.count == 3, parts[1] >= 60 { return 0 }
    let seconds: Double
    switch parts.count {
    case 1: seconds = parts[0]
    case 2: seconds = parts[0] * 60 + parts[1]
    case 3: seconds = parts[0] * 3600 + parts[1] * 60 + parts[2]
    default: return 0
    }
    return seconds.isFinite ? seconds : 0
}

private func parsePodcastDate(_ value: String) -> Date? {
    let iso = ISO8601DateFormatter()
    if let date = iso.date(from: value) { return date }
    for format in [
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm Z",
        "EEE, d MMM yyyy HH:mm Z",
    ] {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = format
        if let date = formatter.date(from: value) { return date }
    }
    return nil
}

private func cleanPodcastText(_ value: String) -> String {
    value
        .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
        .replacingOccurrences(of: "&nbsp;", with: " ")
        .replacingOccurrences(of: "&amp;", with: "&")
        .replacingOccurrences(of: "&lt;", with: "<")
        .replacingOccurrences(of: "&gt;", with: ">")
        .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .prefix(4_000)
        .description
}

private extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}
