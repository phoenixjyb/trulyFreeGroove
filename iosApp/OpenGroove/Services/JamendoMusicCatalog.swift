import Foundation

struct JamendoMusicCatalog: Sendable {
    let clientID: String

    init?(clientID: String) {
        let cleanID = clientID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanID.isEmpty, !cleanID.hasPrefix("$(") else { return nil }
        self.clientID = cleanID
    }

    func search(query: String, language: MusicSearchLanguage) async throws -> [MusicTrack] {
        var components = URLComponents(string: "https://api.jamendo.com/v3.0/tracks/")
        var items = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "limit", value: "30"),
            URLQueryItem(name: "imagesize", value: "300"),
            URLQueryItem(name: "audioformat", value: "mp31"),
            URLQueryItem(name: "type", value: "single albumtrack"),
        ]
        switch language {
        case .english: items.append(URLQueryItem(name: "lang", value: "en"))
        case .chinese, .cantonese: items.append(URLQueryItem(name: "lang", value: "zh"))
        case .all: break
        }
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let catalogQuery = language == .cantonese
            ? [cleanQuery, language.searchHint].filter { !$0.isEmpty }.joined(separator: " ")
            : cleanQuery
        if catalogQuery.isEmpty {
            items.append(URLQueryItem(name: "featured", value: "1"))
            items.append(URLQueryItem(name: "groupby", value: "artist_id"))
        } else {
            items.append(URLQueryItem(name: "search", value: catalogQuery))
        }
        components?.queryItems = items
        guard let url = components?.url else { throw MusicCatalogError.invalidResponse }

        var request = URLRequest(url: url)
        request.timeoutInterval = 18
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("OpenGroove/0.3 iOS", forHTTPHeaderField: "User-Agent")
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
        let payload = try JSONDecoder().decode(JamendoResponse.self, from: data)
        if payload.headers?.status == "failed" { throw MusicCatalogError.invalidResponse }
        return payload.results?.compactMap { item in
            guard
                let streamURL = URL(string: item.audio), streamURL.scheme?.lowercased() == "https",
                let licenseURL = URL(string: item.licenseCCURL.replacingOccurrences(of: "http://", with: "https://")),
                licenseURL.isPublicHTTPURL,
                let sourceURL = URL(string: item.shareURL.nonEmpty ?? item.shortURL ?? ""),
                sourceURL.isPublicHTTPURL
            else { return nil }
            return MusicTrack(
                id: "jamendo:\(item.id)",
                title: item.name.nonEmpty ?? "Untitled",
                artist: item.artistName.nonEmpty ?? "Unknown artist",
                album: item.albumName ?? "",
                duration: TimeInterval(max(item.duration ?? 0, 0)),
                artworkURL: URL(string: item.image.nonEmpty ?? item.albumImage ?? ""),
                providerName: "Jamendo",
                streamURL: streamURL,
                sourceURL: sourceURL,
                licenseURL: licenseURL,
                playbackMode: .directAuthorized
            )
        } ?? []
    }
}

private struct JamendoResponse: Decodable {
    let headers: JamendoHeaders?
    let results: [JamendoItem]?
}

private struct JamendoHeaders: Decodable {
    let status: String?
}

private struct JamendoItem: Decodable {
    let id: String
    let name: String
    let artistName: String
    let albumName: String?
    let duration: Int?
    let image: String
    let albumImage: String?
    let audio: String
    let shareURL: String
    let shortURL: String?
    let licenseCCURL: String

    enum CodingKeys: String, CodingKey {
        case id, name, duration, image, audio
        case artistName = "artist_name"
        case albumName = "album_name"
        case albumImage = "album_image"
        case shareURL = "shareurl"
        case shortURL = "shorturl"
        case licenseCCURL = "license_ccurl"
    }
}

private extension String {
    var nonEmpty: String? {
        let clean = trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? nil : clean
    }
}
