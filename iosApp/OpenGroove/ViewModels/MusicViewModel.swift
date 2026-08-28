import Combine
import Foundation

@MainActor
final class MusicViewModel: ObservableObject {
    @Published var query = ""
    @Published var language: MusicSearchLanguage = .all
    @Published private(set) var tracks: [MusicTrack] = []
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private let catalog = WikimediaMusicCatalog()
    private let jamendoCatalog: JamendoMusicCatalog?
    private var started = false
    private var searchGeneration = 0

    var jamendoConfigured: Bool { jamendoCatalog != nil }

    init(jamendoClientID: String? = Bundle.main.object(forInfoDictionaryKey: "JamendoClientID") as? String) {
        jamendoCatalog = jamendoClientID.flatMap(JamendoMusicCatalog.init(clientID:))
    }

    func start() async {
        guard !started else { return }
        started = true
        await search()
    }

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
        var attempts: [MusicCatalogAttempt] = []
        await withTaskGroup(of: MusicCatalogAttempt.self) { group in
            group.addTask { [catalog] in
                do { return .success(try await catalog.search(query: querySnapshot, language: languageSnapshot)) }
                catch { return .failure(error.localizedDescription) }
            }
            if let jamendoCatalog {
                group.addTask {
                    do { return .success(try await jamendoCatalog.search(query: querySnapshot, language: languageSnapshot)) }
                    catch { return .failure(error.localizedDescription) }
                }
            }
            for await attempt in group { attempts.append(attempt) }
        }
        guard generation == searchGeneration, !Task.isCancelled else { return }
        tracks = attempts.flatMap(\.tracks).reduce(into: []) { result, track in
            if !result.contains(where: { $0.providerName == track.providerName && $0.sourceURL == track.sourceURL }) {
                result.append(track)
            }
        }
        if tracks.isEmpty {
            errorMessage = attempts.compactMap(\.errorMessage).first ?? "No licensed tracks found. Try another search."
        }
    }
}

private enum MusicCatalogAttempt: Sendable {
    case success([MusicTrack])
    case failure(String)

    var tracks: [MusicTrack] {
        if case let .success(tracks) = self { tracks } else { [] }
    }

    var errorMessage: String? {
        if case let .failure(message) = self { message } else { nil }
    }
}
