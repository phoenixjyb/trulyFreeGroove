import Combine
import Foundation

@MainActor
final class RecentStationStore: ObservableObject {
    @Published private(set) var stations: [RadioStation] = []

    private let defaults: UserDefaults
    private let storageKey = "recent_radio_stations_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        guard let data = defaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([RadioStation].self, from: data) else { return }
        stations = Array(decoded.prefix(20))
    }

    func record(_ station: RadioStation) {
        stations.removeAll { $0.id == station.id }
        stations.insert(station, at: 0)
        stations = Array(stations.prefix(20))
        persist()
    }

    func clear() {
        stations.removeAll()
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(stations) else { return }
        defaults.set(data, forKey: storageKey)
    }
}
