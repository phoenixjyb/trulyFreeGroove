import Foundation

enum MusicCatalogError: LocalizedError, Sendable {
    case invalidResponse
    case unavailable

    var errorDescription: String? {
        switch self {
        case .invalidResponse: "Wikimedia Commons returned invalid music metadata."
        case .unavailable: "Could not reach Wikimedia Commons."
        }
    }
}

struct WikimediaMusicCatalog: Sendable {
    func search(query: String, language: MusicSearchLanguage) async throws -> [MusicTrack] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let languageHint: String
        switch language {
        case .all: languageHint = "music"
        case .english: languageHint = "English music"
        case .chinese: languageHint = "Chinese music"
        case .cantonese: languageHint = "Cantonese music 粤语"
        }
        let terms: String
        if cleanQuery.isEmpty {
            terms = languageHint
        } else {
            switch language {
            case .all: terms = cleanQuery
            case .english: terms = "\(cleanQuery) English"
            case .chinese: terms = "\(cleanQuery) Chinese"
            case .cantonese: terms = "\(cleanQuery) Cantonese 粤语"
            }
        }

        var components = URLComponents(string: "https://commons.wikimedia.org/w/api.php")
        components?.queryItems = [
            URLQueryItem(name: "action", value: "query"),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "generator", value: "search"),
            URLQueryItem(name: "gsrsearch", value: "\(terms) filetype:audio"),
            URLQueryItem(name: "gsrnamespace", value: "6"),
            URLQueryItem(name: "gsrlimit", value: "20"),
            URLQueryItem(name: "prop", value: "imageinfo|info"),
            URLQueryItem(name: "iiprop", value: "url|mime|extmetadata"),
            URLQueryItem(name: "inprop", value: "url"),
        ]
        guard let url = components?.url else { throw MusicCatalogError.invalidResponse }
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("OpenGroove/0.3 iOS lawful music discovery", forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
                throw MusicCatalogError.unavailable
            }
            return try decode(data)
        } catch let error as MusicCatalogError {
            throw error
        } catch {
            throw MusicCatalogError.unavailable
        }
    }

    func decode(_ data: Data) throws -> [MusicTrack] {
        let root = try JSONDecoder().decode(CommonsResponse.self, from: data)
        guard root.error == nil else { throw MusicCatalogError.invalidResponse }
        return root.query?.pages.values.compactMap { page in
            guard let info = page.imageInfo?.first else { return nil }
            let mime = info.mime.lowercased()
            guard mime.hasPrefix("audio/") || mime == "application/ogg" else { return nil }
            guard
                let streamURL = URL(string: info.url), streamURL.scheme?.lowercased() == "https",
                let sourceURL = URL(string: page.canonicalURL ?? info.descriptionURL ?? ""),
                sourceURL.isPublicHTTPURL
            else { return nil }
            let metadata = info.extMetadata ?? [:]
            let licenseName = metadata["LicenseShortName"]?.value.nonEmpty ??
                metadata["UsageTerms"]?.value.nonEmpty
            guard let licenseName else { return nil }
            let licenseURL = URL(string: metadata["LicenseUrl"]?.value ?? "") ?? sourceURL
            guard licenseURL.isPublicHTTPURL else { return nil }
            let rawTitle = metadata["ObjectName"]?.value.nonEmpty ?? page.title
            let title = rawTitle
                .replacingOccurrences(of: "File:", with: "", options: [.anchored])
                .deletingPathExtension
            return MusicTrack(
                id: "commons:\(page.pageID)",
                title: title,
                artist: cleanMusicText(metadata["Artist"]?.value ?? "").nonEmpty ?? "Unknown creator",
                album: licenseName,
                duration: 0,
                artworkURL: nil,
                providerName: "Wikimedia Commons",
                streamURL: streamURL,
                sourceURL: sourceURL,
                licenseURL: licenseURL,
                playbackMode: .directAuthorized
            )
        } ?? []
    }
}

private struct CommonsResponse: Decodable {
    let query: CommonsQuery?
    let error: CommonsAPIError?
}

private struct CommonsQuery: Decodable {
    let pages: [String: CommonsPage]
}

private struct CommonsAPIError: Decodable {
    let info: String?
}

private struct CommonsPage: Decodable {
    let pageID: Int
    let title: String
    let canonicalURL: String?
    let imageInfo: [CommonsImageInfo]?

    enum CodingKeys: String, CodingKey {
        case pageID = "pageid"
        case title
        case canonicalURL = "canonicalurl"
        case imageInfo = "imageinfo"
    }
}

private struct CommonsImageInfo: Decodable {
    let url: String
    let descriptionURL: String?
    let mime: String
    let extMetadata: [String: CommonsMetadataValue]?

    enum CodingKeys: String, CodingKey {
        case url
        case descriptionURL = "descriptionurl"
        case mime
        case extMetadata = "extmetadata"
    }
}

private struct CommonsMetadataValue: Decodable {
    let value: String
}

private func cleanMusicText(_ value: String) -> String {
    value
        .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
        .replacingOccurrences(of: "&nbsp;", with: " ")
        .replacingOccurrences(of: "&amp;", with: "&")
        .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)
}

private extension String {
    var nonEmpty: String? {
        let clean = trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? nil : clean
    }

    var deletingPathExtension: String {
        (self as NSString).deletingPathExtension
    }
}
