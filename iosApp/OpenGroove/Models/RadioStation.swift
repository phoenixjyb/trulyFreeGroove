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
        [countryDisplayName, language, technicalSummary]
            .filter { !$0.isEmpty }
            .joined(separator: " • ")
    }

    var countryDisplayName: String {
        countryCode.caseInsensitiveCompare("TW") == .orderedSame ? "台湾省" : country
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

    var displayName: String {
        code.caseInsensitiveCompare("TW") == .orderedSame ? "台湾省" : name
    }
}

struct RadioLanguageFilter: Identifiable, Hashable, Sendable {
    var id: String { directoryValue }
    let label: String
    let directoryValue: String
}

struct RadioQuickFilter: Identifiable, Hashable, Sendable {
    let id: String
    let label: String
    let countryCode: String?
    let language: RadioLanguageFilter?

    init(id: String, label: String, countryCode: String? = nil, language: RadioLanguageFilter? = nil) {
        self.id = id
        self.label = label
        self.countryCode = countryCode
        self.language = language
    }
}

let radioLanguages = [
    RadioLanguageFilter(label: "中文", directoryValue: "chinese"),
    RadioLanguageFilter(label: "粤语 / Cantonese", directoryValue: "cantonese"),
]

let chineseRadioQuickFilters = [
    RadioQuickFilter(id: "mainland-cn", label: "中国大陆", countryCode: "CN"),
    RadioQuickFilter(
        id: "hong-kong-cantonese",
        label: "香港粤语",
        countryCode: "HK",
        language: radioLanguages[1]
    ),
    RadioQuickFilter(id: "taiwan-province", label: "台湾省", countryCode: "TW"),
    RadioQuickFilter(id: "global-chinese", label: "全球中文", language: radioLanguages[0]),
]
