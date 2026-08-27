import Combine
import Foundation

@MainActor
final class SavedStationStore: ObservableObject {
    @Published private(set) var stations: [RadioStation]

    private let defaults: UserDefaults
    private let storageKey = "saved_radio_stations_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if
            let data = defaults.data(forKey: storageKey),
            let decoded = try? JSONDecoder().decode([RadioStation].self, from: data)
        {
            stations = decoded
        } else {
            stations = []
        }
    }

    func contains(_ station: RadioStation) -> Bool {
        stations.contains { $0.id == station.id }
    }

    func toggle(_ station: RadioStation) {
        if let index = stations.firstIndex(where: { $0.id == station.id }) {
            stations.remove(at: index)
        } else {
            stations.insert(station, at: 0)
        }
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(stations) else { return }
        defaults.set(data, forKey: storageKey)
    }
}
