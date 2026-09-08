import Foundation

struct MusicQueueSnapshot: Codable, Equatable, Sendable {
    static let maximumTrackCount = 500

    let tracks: [MusicTrack]
    let currentIndex: Int
    let position: TimeInterval
    let shuffleEnabled: Bool
    let repeatMode: MusicRepeatMode

    func validated() -> MusicQueueSnapshot? {
        let limitedTracks = Array(tracks.prefix(Self.maximumTrackCount))
        let playableTracks = limitedTracks.enumerated().filter { $0.element.isDirectPlaybackCandidate }
        guard !playableTracks.isEmpty else { return nil }

        let requestedIndex = min(max(currentIndex, 0), limitedTracks.count - 1)
        let mappedIndex = playableTracks.firstIndex(where: { $0.offset == requestedIndex })
            ?? min(requestedIndex, playableTracks.count - 1)
        return MusicQueueSnapshot(
            tracks: playableTracks.map(\.element),
            currentIndex: mappedIndex,
            position: max(position, 0),
            shuffleEnabled: shuffleEnabled,
            repeatMode: repeatMode
        )
    }
}

struct MusicQueueStore {
    private let defaults: UserDefaults
    private let storageKey = "music_queue_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> MusicQueueSnapshot? {
        guard
            let data = defaults.data(forKey: storageKey),
            let snapshot = try? JSONDecoder().decode(MusicQueueSnapshot.self, from: data),
            let validated = snapshot.validated()
        else { return nil }
        return validated
    }

    func save(_ snapshot: MusicQueueSnapshot) {
        guard
            let validated = snapshot.validated(),
            let data = try? JSONEncoder().encode(validated)
        else {
            clear()
            return
        }
        defaults.set(data, forKey: storageKey)
    }

    func clear() {
        defaults.removeObject(forKey: storageKey)
    }
}
