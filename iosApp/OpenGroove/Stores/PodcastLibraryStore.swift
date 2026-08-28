import Combine
import Foundation

@MainActor
final class PodcastLibraryStore: ObservableObject {
    @Published private(set) var subscriptions: [PodcastShow] = []
    @Published private(set) var episodesByFeed: [String: [PodcastEpisode]] = [:]

    private let defaults: UserDefaults
    private let storageKey = "podcast_library_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        guard
            let data = defaults.data(forKey: storageKey),
            let state = try? JSONDecoder().decode(StoredState.self, from: data)
        else { return }
        subscriptions = state.subscriptions
        episodesByFeed = state.episodesByFeed
    }

    var unplayedEpisodes: [PodcastEpisode] {
        let subscribedFeeds = Set(subscriptions.map { $0.feedURL.absoluteString })
        return episodesByFeed
            .filter { subscribedFeeds.contains($0.key) }
            .flatMap(\.value)
            .filter { !$0.completed }
            .sorted {
                if ($0.position > 0) != ($1.position > 0) { return $0.position > 0 }
                return ($0.publishedAt ?? .distantPast) > ($1.publishedAt ?? .distantPast)
            }
            .prefix(200)
            .map { $0 }
    }

    func isSubscribed(_ show: PodcastShow) -> Bool {
        subscriptions.contains { $0.feedURL == show.feedURL }
    }

    func cachedEpisodes(for feedURL: URL) -> [PodcastEpisode] {
        episodesByFeed[feedURL.absoluteString] ?? []
    }

    func upsert(_ feed: PodcastFeed, subscribed: Bool? = nil) {
        let key = feed.show.feedURL.absoluteString
        let existingEpisodes = Dictionary(
            uniqueKeysWithValues: cachedEpisodes(for: feed.show.feedURL).map { ($0.id, $0) }
        )
        episodesByFeed[key] = feed.episodes.map { fresh in
            guard let existing = existingEpisodes[fresh.id] else { return fresh }
            var merged = fresh
            merged.position = existing.position
            merged.completed = existing.completed
            if merged.duration <= 0 { merged.duration = existing.duration }
            return merged
        }
        let shouldSubscribe = subscribed ?? isSubscribed(feed.show)
        if shouldSubscribe {
            subscriptions.removeAll { $0.feedURL == feed.show.feedURL }
            subscriptions.insert(feed.show, at: 0)
        }
        persist()
    }

    func setSubscribed(_ show: PodcastShow, subscribed: Bool) {
        subscriptions.removeAll { $0.feedURL == show.feedURL }
        if subscribed { subscriptions.insert(show, at: 0) }
        persist()
    }

    func togglePlayed(_ episode: PodcastEpisode) {
        mutateEpisode(episode.id, feedURL: episode.feedURL) { stored in
            stored.completed.toggle()
            stored.position = 0
        }
    }

    func updateProgress(
        episodeID: String,
        feedURL: URL,
        position: TimeInterval,
        duration: TimeInterval
    ) {
        mutateEpisode(episodeID, feedURL: feedURL) { stored in
            let safeDuration = max(max(duration, stored.duration), 0)
            let completed = safeDuration > 0 && position >= safeDuration - 30
            stored.duration = safeDuration
            stored.position = completed ? 0 : max(position, 0)
            stored.completed = completed
        }
    }

    @discardableResult
    func refreshSubscribedFeeds() async -> Int {
        let directory = PodcastFeedDirectory()
        let snapshot = subscriptions
        var refreshed = 0
        for show in snapshot {
            guard let feed = try? await directory.load(feedURL: show.feedURL, fallback: show) else { continue }
            upsert(feed, subscribed: true)
            refreshed += 1
        }
        return refreshed
    }

    private func mutateEpisode(
        _ episodeID: String,
        feedURL: URL,
        mutation: (inout PodcastEpisode) -> Void
    ) {
        let key = feedURL.absoluteString
        guard var episodes = episodesByFeed[key],
              let index = episodes.firstIndex(where: { $0.id == episodeID }) else { return }
        mutation(&episodes[index])
        episodesByFeed[key] = episodes
        persist()
    }

    private func persist() {
        let state = StoredState(subscriptions: subscriptions, episodesByFeed: episodesByFeed)
        guard let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: storageKey)
    }
}

private struct StoredState: Codable {
    let subscriptions: [PodcastShow]
    let episodesByFeed: [String: [PodcastEpisode]]
}
