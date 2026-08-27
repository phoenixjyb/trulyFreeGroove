import Combine
import Foundation

@MainActor
final class RadioViewModel: ObservableObject {
    enum ListMode: String, CaseIterable, Identifiable {
        case discover = "Discover"
        case saved = "Saved"
        var id: Self { self }
    }

    @Published var query = ""
    @Published var mode: ListMode = .discover
    @Published private(set) var stations: [RadioStation] = []
    @Published private(set) var countries: [RadioCountry] = []
    @Published private(set) var selectedCountry: RadioCountry?
    @Published private(set) var selectedTag: String?
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private let directory = RadioDirectory()

    func start() async {
        guard stations.isEmpty else { return }
        async let stationsLoad: Void = search()
        async let countriesLoad: Void = loadCountries()
        _ = await (stationsLoad, countriesLoad)
    }

    func search() async {
        isLoading = true
        errorMessage = nil
        do {
            stations = try await directory.search(
                name: query,
                countryCode: selectedCountry?.code,
                tag: selectedTag
            )
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func select(country: RadioCountry?) async {
        selectedCountry = country
        selectedTag = nil
        await search()
    }

    func select(tag: String?) async {
        selectedCountry = nil
        selectedTag = tag
        await search()
    }

    func registerClick(_ station: RadioStation) {
        Task { await directory.registerClick(stationID: station.id) }
    }

    private func loadCountries() async {
        countries = (try? await directory.countries()) ?? []
    }
}
