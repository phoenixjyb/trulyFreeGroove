import Foundation

struct RadioStation: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let name: String
    let streamURL: URL
    let homepageURL: URL?
    let faviconURL: URL?
    let country: String
    let countryCode: String
    let language: String
    let tags: [String]
    let codec: String
    let bitrate: Int
    let votes: Int
    let isOnline: Bool
    let lastCheckedAt: String
    let isHLS: Bool

    var isPlayable: Bool {
        !id.isEmpty && !name.isEmpty && ["http", "https"].contains(streamURL.scheme?.lowercased())
    }

    var subtitle: String {
        [country, language, technicalSummary]
            .filter { !$0.isEmpty }
            .joined(separator: " • ")
    }

    var technicalSummary: String {
        var details: [String] = []
        if isHLS { details.append("HLS") }
        if !codec.isEmpty { details.append(codec.uppercased()) }
        if bitrate > 0 { details.append("\(bitrate) kbps") }
        return details.joined(separator: " • ")
    }
}

struct RadioCountry: Identifiable, Hashable, Sendable {
    var id: String { code }
    let name: String
    let code: String
    let stationCount: Int
}
