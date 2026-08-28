import Combine
import Foundation

@MainActor
final class PodcastViewModel: ObservableObject {
    enum Mode: String, CaseIterable, Identifiable {
        case search = "Search"
        case subscriptions = "Subscribed"
        case unplayed = "Unplayed"
        var id: Self { self }
    }

    @Published var mode: Mode = .search
    @Published var query = ""
    @Published var language: PodcastSearchLanguage = .all
    @Published private(set) var results: [PodcastShow] = []
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private let catalog = PodcastCatalog()

    func search() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            results = try await catalog.search(query: query, language: language)
        } catch {
            results = []
            errorMessage = error.localizedDescription
        }
    }
}
