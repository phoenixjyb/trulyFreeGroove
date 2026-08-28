import Foundation

enum PodcastCatalogError: LocalizedError, Sendable {
    case emptyQuery
    case invalidResponse
    case unavailable

    var errorDescription: String? {
        switch self {
        case .emptyQuery: "Enter a podcast, host, or topic."
        case .invalidResponse: "The podcast directory returned an invalid response."
        case .unavailable: "Could not reach the podcast directory."
        }
    }
}

struct PodcastCatalog: Sendable {
    func search(
        query: String,
        language: PodcastSearchLanguage,
        limit: Int = 30
    ) async throws -> [PodcastShow] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { throw PodcastCatalogError.emptyQuery }

        var components = URLComponents(string: "https://itunes.apple.com/search")
        components?.queryItems = [
            URLQueryItem(name: "media", value: "podcast"),
            URLQueryItem(name: "entity", value: "podcast"),
            URLQueryItem(name: "term", value: cleanQuery),
            URLQueryItem(name: "country", value: language.countryCode),
            URLQueryItem(name: "limit", value: String(min(max(limit, 1), 50))),
            URLQueryItem(name: "explicit", value: "No"),
        ]
        guard let url = components?.url else { throw PodcastCatalogError.invalidResponse }

        var request = URLRequest(url: url)
        request.timeoutInterval = 18
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("OpenGroove/0.2 iOS podcast discovery", forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
                throw PodcastCatalogError.invalidResponse
            }
            return try decodeSearchResults(data)
        } catch let error as PodcastCatalogError {
            throw error
        } catch {
            throw PodcastCatalogError.unavailable
        }
    }

    func decodeSearchResults(_ data: Data) throws -> [PodcastShow] {
        let response = try JSONDecoder().decode(SearchResponse.self, from: data)
        var seen = Set<String>()
        return response.results.compactMap { item in
            guard
                let feedURL = URL(string: item.feedURL ?? ""),
                feedURL.isPublicHTTPURL,
                let title = item.collectionName?.trimmingCharacters(in: .whitespacesAndNewlines),
                !title.isEmpty,
                seen.insert(feedURL.absoluteString).inserted
            else { return nil }

            return PodcastShow(
                catalogID: item.collectionID.map(String.init) ?? "",
                title: title,
                author: item.artistName ?? "",
                description: "",
                feedURL: feedURL,
                artworkURL: nil,
                websiteURL: URL(string: item.collectionViewURL ?? ""),
                genre: item.primaryGenreName ?? "",
                country: item.country ?? ""
            )
        }
    }
}

private struct SearchResponse: Decodable {
    let results: [SearchItem]
}

private struct SearchItem: Decodable {
    let collectionID: Int64?
    let collectionName: String?
    let artistName: String?
    let feedURL: String?
    let collectionViewURL: String?
    let primaryGenreName: String?
    let country: String?

    enum CodingKeys: String, CodingKey {
        case collectionID = "collectionId"
        case collectionName
        case artistName
        case feedURL = "feedUrl"
        case collectionViewURL = "collectionViewUrl"
        case primaryGenreName
        case country
    }
}
