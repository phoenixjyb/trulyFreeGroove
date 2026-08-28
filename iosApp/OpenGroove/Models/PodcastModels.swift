import Foundation

struct PodcastShow: Identifiable, Codable, Hashable, Sendable {
    let catalogID: String
    let title: String
    let author: String
    let description: String
    let feedURL: URL
    let artworkURL: URL?
    let websiteURL: URL?
    let genre: String
    let country: String

    var id: String { feedURL.absoluteString }
    var isValid: Bool { !title.isEmpty && feedURL.isPublicHTTPURL }
}

struct PodcastEpisode: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let feedURL: URL
    let guid: String
    let title: String
    let showTitle: String
    let author: String
    let description: String
    let audioURL: URL
    let websiteURL: URL?
    let artworkURL: URL?
    let mimeType: String
    let publishedAt: Date?
    var duration: TimeInterval
    var position: TimeInterval
    var completed: Bool

    var isPlayable: Bool { !title.isEmpty && audioURL.isPublicHTTPURL }

    func matches(_ query: String) -> Bool {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return true }
        return [title, showTitle, author, description].contains {
            $0.localizedCaseInsensitiveContains(cleanQuery)
        }
    }
}

struct PodcastFeed: Sendable {
    let show: PodcastShow
    let episodes: [PodcastEpisode]
}

enum PodcastSearchLanguage: String, CaseIterable, Identifiable, Sendable {
    case all = "All"
    case english = "English"
    case chinese = "中文"
    case cantonese = "廣東話"

    var id: Self { self }

    var countryCode: String {
        switch self {
        case .chinese: "CN"
        case .cantonese: "HK"
        case .all, .english: "US"
        }
    }
}

enum PodcastPlaybackSettings {
    static let speeds: [Float] = [0.75, 1, 1.25, 1.5, 2]
    static let sleepTimerMinutes = [15, 30, 45, 60]
}

extension URL {
    var isPublicHTTPURL: Bool {
        guard let scheme = scheme?.lowercased() else { return false }
        return scheme == "http" || scheme == "https"
    }
}
