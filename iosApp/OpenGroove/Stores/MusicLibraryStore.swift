import Combine
import Foundation

@MainActor
final class MusicLibraryStore: ObservableObject {
    @Published private(set) var playlists: [MusicPlaylist] = []

    private let defaults: UserDefaults
    private let storageKey = "music_playlists_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        guard let data = defaults.data(forKey: storageKey),
              let stored = try? JSONDecoder().decode([MusicPlaylist].self, from: data) else { return }
        playlists = stored
    }

    @discardableResult
    func createPlaylist(named name: String) -> Bool {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty, cleanName.count <= 50,
              !playlists.contains(where: { $0.name.localizedCaseInsensitiveCompare(cleanName) == .orderedSame })
        else { return false }
        playlists.append(MusicPlaylist(name: cleanName))
        persist()
        return true
    }

    func add(_ track: MusicTrack, to playlistID: UUID) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }),
              !playlists[index].tracks.contains(where: { $0.id == track.id }) else { return }
        playlists[index].tracks.append(track)
        persist()
    }

    func remove(_ track: MusicTrack, from playlistID: UUID) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }) else { return }
        playlists[index].tracks.removeAll { $0.id == track.id }
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(playlists) else { return }
        defaults.set(data, forKey: storageKey)
    }
}
