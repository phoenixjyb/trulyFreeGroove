import Foundation

enum MusicPlaybackMode: String, Codable, Sendable {
    case directAuthorized
    case externalOnly
}

struct MusicTrack: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let title: String
    let artist: String
    let album: String
    let duration: TimeInterval
    let artworkURL: URL?
    let providerName: String
    let streamURL: URL
    let sourceURL: URL
    let licenseURL: URL
    let playbackMode: MusicPlaybackMode

    var isDirectPlaybackCandidate: Bool {
        playbackMode == .directAuthorized &&
            streamURL.scheme?.lowercased() == "https" &&
            licenseURL.isPublicHTTPURL
    }
}

struct MusicPlaylist: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var name: String
    var tracks: [MusicTrack]

    init(id: UUID = UUID(), name: String, tracks: [MusicTrack] = []) {
        self.id = id
        self.name = name
        self.tracks = tracks
    }
}

enum MusicSearchLanguage: String, CaseIterable, Identifiable, Sendable {
    case all = "All"
    case english = "English"
    case chinese = "国语 / 中文"
    case cantonese = "粤语 / Cantonese"

    var id: Self { self }

    var searchHint: String {
        switch self {
        case .all: ""
        case .english: "English"
        case .chinese: "中文"
        case .cantonese: "粤语 Cantonese"
        }
    }
}
