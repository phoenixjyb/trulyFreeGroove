import Foundation

enum RadioDirectoryError: LocalizedError, Sendable {
    case unavailable
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .unavailable: "Could not reach the radio directory."
        case .invalidResponse: "The radio directory returned an invalid response."
        }
    }
}

struct RadioDirectory: Sendable {
    static let pageSize = 60

    private let hosts = [
        "all.api.radio-browser.info",
        "de1.api.radio-browser.info",
        "at1.api.radio-browser.info",
    ]

    func search(
        name: String = "",
        countryCode: String? = nil,
        tag: String? = nil,
        offset: Int = 0
    ) async throws -> [RadioStation] {
        var query = [
            URLQueryItem(name: "hidebroken", value: "true"),
            URLQueryItem(name: "order", value: "clickcount"),
            URLQueryItem(name: "reverse", value: "true"),
            URLQueryItem(name: "limit", value: String(Self.pageSize)),
            URLQueryItem(name: "offset", value: String(max(0, offset))),
        ]
        if !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            query.append(URLQueryItem(name: "name", value: name))
        }
        if let countryCode, !countryCode.isEmpty {
            query.append(URLQueryItem(name: "countrycode", value: countryCode))
        }
        if let tag, !tag.isEmpty {
            query.append(URLQueryItem(name: "tag", value: tag.lowercased()))
        }

        let data = try await request(path: "/json/stations/search", query: query)
        return try decodeStations(data)
    }

    func countries() async throws -> [RadioCountry] {
        let query = [
            URLQueryItem(name: "order", value: "stationcount"),
            URLQueryItem(name: "reverse", value: "true"),
            URLQueryItem(name: "hidebroken", value: "true"),
            URLQueryItem(name: "limit", value: "250"),
        ]
        let data = try await request(path: "/json/countries", query: query)
        return try JSONDecoder().decode([RadioBrowserCountry].self, from: data).compactMap { item in
            guard item.isoCode.count == 2, !item.name.isEmpty else { return nil }
            return RadioCountry(name: item.name, code: item.isoCode.uppercased(), stationCount: item.stationCount)
        }
    }

    func registerClick(stationID: String) async {
        let safeID = stationID.filter { $0.isLetter || $0.isNumber || $0 == "-" }
        guard !safeID.isEmpty else { return }
        _ = try? await request(path: "/json/url/\(safeID)", query: [])
    }

    func decodeStations(_ data: Data) throws -> [RadioStation] {
        let payload = try JSONDecoder().decode([RadioBrowserStation].self, from: data)
        var seen = Set<String>()
        return payload.compactMap { item in
            let streamText = item.resolvedURL.isEmpty ? item.url : item.resolvedURL
            guard
                item.lastCheckOK == 1,
                let streamURL = URL(string: streamText),
                !item.stationUUID.isEmpty,
                !item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                seen.insert(item.stationUUID).inserted
            else { return nil }

            let station = RadioStation(
                id: item.stationUUID,
                name: item.name.trimmingCharacters(in: .whitespacesAndNewlines),
                streamURL: streamURL,
                homepageURL: URL(string: item.homepage),
                faviconURL: URL(string: item.favicon),
                country: item.country,
                countryCode: item.countryCode,
                language: item.language,
                tags: item.tags.split(separator: ",").map(String.init).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.prefix(6).map { $0 },
                codec: item.codec,
                bitrate: item.bitrate,
                votes: item.votes,
                isOnline: true,
                lastCheckedAt: item.lastCheckedAt,
                isHLS: item.hls == 1
            )
            return station.isPlayable ? station : nil
        }
    }

    private func request(path: String, query: [URLQueryItem]) async throws -> Data {
        for host in hosts {
            var components = URLComponents()
            components.scheme = "https"
            components.host = host
            components.path = path
            components.queryItems = query
            guard let url = components.url else { continue }

            var request = URLRequest(url: url)
            request.timeoutInterval = 18
            request.setValue("application/json", forHTTPHeaderField: "Accept")
            request.setValue("OpenGroove/0.1 iOS personal internet radio app", forHTTPHeaderField: "User-Agent")

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                guard let http = response as? HTTPURLResponse else { throw RadioDirectoryError.invalidResponse }
                guard 200..<300 ~= http.statusCode else { continue }
                return data
            } catch {
                continue
            }
        }
        throw RadioDirectoryError.unavailable
    }
}

private struct RadioBrowserStation: Decodable {
    let stationUUID: String
    let name: String
    let url: String
    let resolvedURL: String
    let homepage: String
    let favicon: String
    let country: String
    let countryCode: String
    let language: String
    let tags: String
    let codec: String
    let bitrate: Int
    let votes: Int
    let lastCheckOK: Int
    let lastCheckedAt: String
    let hls: Int

    enum CodingKeys: String, CodingKey {
        case stationUUID = "stationuuid"
        case name, url, homepage, favicon, country, language, tags, codec, bitrate, votes, hls
        case resolvedURL = "url_resolved"
        case countryCode = "countrycode"
        case lastCheckOK = "lastcheckok"
        case lastCheckedAt = "lastchecktime_iso8601"
    }
}

private struct RadioBrowserCountry: Decodable {
    let name: String
    let isoCode: String
    let stationCount: Int

    enum CodingKeys: String, CodingKey {
        case name
        case isoCode = "iso_3166_1"
        case stationCount = "stationcount"
    }
}
