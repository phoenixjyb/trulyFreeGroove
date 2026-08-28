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
    private var searchGeneration = 0

    func search() async {
        searchGeneration += 1
        let generation = searchGeneration
        isLoading = true
        errorMessage = nil
        defer {
            if generation == searchGeneration { isLoading = false }
        }
        let querySnapshot = query
        let languageSnapshot = language
        do {
            let freshResults = try await catalog.search(query: querySnapshot, language: languageSnapshot)
            guard generation == searchGeneration, !Task.isCancelled else { return }
            results = freshResults
        } catch {
            guard generation == searchGeneration, !Task.isCancelled else { return }
            results = []
            errorMessage = error.localizedDescription
        }
    }
}
