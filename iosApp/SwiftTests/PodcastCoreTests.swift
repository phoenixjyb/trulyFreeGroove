import Foundation
import Testing
#if canImport(OpenGrooveIOSCore)
@testable import OpenGrooveIOSCore
#elseif canImport(OpenGroove)
@testable import OpenGroove
#endif

@Test
func podcastCatalogKeepsPublisherFeedAndOmitsPromotionalArtwork() throws {
    let payload = """
    {
      "resultCount": 2,
      "results": [
        {
          "collectionId": 42,
          "collectionName": "香港故事 Hong Kong Stories",
          "artistName": "Publisher",
          "feedUrl": "https://publisher.example/hk.xml",
          "collectionViewUrl": "https://podcasts.apple.com/show/42",
          "primaryGenreName": "Society & Culture",
          "country": "HKG",
          "artworkUrl600": "https://promotional.example/copied-art.jpg"
        },
        {
          "collectionName": "Unsafe feed",
          "feedUrl": "file:///private/feed.xml"
        }
      ]
    }
    """.data(using: .utf8)!

    let shows = try PodcastCatalog().decodeSearchResults(payload)
    let show = try #require(shows.first)
    #expect(shows.count == 1)
    #expect(show.title == "香港故事 Hong Kong Stories")
    #expect(show.feedURL.absoluteString == "https://publisher.example/hk.xml")
    #expect(show.artworkURL == nil)
}

@Test
func publisherRSSParsesMultilingualEpisodeMetadata() throws {
    let payload = """
    <?xml version="1.0" encoding="UTF-8"?>
    <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
      <channel>
        <title>廣東話天地 Cantonese World</title>
        <link>https://publisher.example/show</link>
        <description><![CDATA[Publisher-owned <b>description</b>]]></description>
        <itunes:author>香港電台</itunes:author>
        <itunes:image href="https://publisher.example/art.jpg" />
        <item>
          <guid>episode-001</guid>
          <title>第一集 — Hello Hong Kong</title>
          <description><![CDATA[今集內容 <b>Episode notes</b>]]></description>
          <pubDate>Thu, 28 Aug 2026 08:30:00 +0800</pubDate>
          <itunes:duration>01:02:03</itunes:duration>
          <enclosure url="https://publisher.example/audio/001.mp3" type="audio/mpeg" />
        </item>
      </channel>
    </rss>
    """.data(using: .utf8)!
    let feedURL = try #require(URL(string: "https://publisher.example/feed.xml"))

    let feed = try PodcastFeedDirectory().parse(payload, feedURL: feedURL)
    let episode = try #require(feed.episodes.first)
    #expect(feed.show.title == "廣東話天地 Cantonese World")
    #expect(feed.show.author == "香港電台")
    #expect(feed.show.artworkURL?.absoluteString == "https://publisher.example/art.jpg")
    #expect(episode.title == "第一集 — Hello Hong Kong")
    #expect(episode.audioURL.absoluteString == "https://publisher.example/audio/001.mp3")
    #expect(episode.duration == 3_723)
    #expect(episode.description == "今集內容 Episode notes")
}

@Test
func feedWithoutPublicAudioFailsClosed() throws {
    let payload = """
    <rss><channel><title>Unsafe</title><item><title>Local file</title>
    <enclosure url="file:///private/episode.mp3" type="audio/mpeg" />
    </item></channel></rss>
    """.data(using: .utf8)!
    let feedURL = try #require(URL(string: "https://publisher.example/feed.xml"))

    #expect(throws: PodcastFeedError.noPlayableEpisodes) {
        try PodcastFeedDirectory().parse(payload, feedURL: feedURL)
    }
}

@Test
func publisherRSSPreservesTextInsideNestedMarkup() throws {
    let payload = """
    <rss><channel><title>Nested Notes</title><item><title>Episode</title>
    <description>Hello <b>world</b> after</description>
    <enclosure url="https://publisher.example/episode.mp3" type="audio/mpeg" />
    </item></channel></rss>
    """.data(using: .utf8)!
    let feedURL = try #require(URL(string: "https://publisher.example/feed.xml"))

    let feed = try PodcastFeedDirectory().parse(payload, feedURL: feedURL)
    #expect(feed.episodes.first?.description == "Hello world after")
}

@MainActor
@Test
func podcastProgressAndSubscriptionPersist() throws {
    let suiteName = "PodcastCoreTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let feedURL = try #require(URL(string: "https://publisher.example/feed.xml"))
    let audioURL = try #require(URL(string: "https://publisher.example/episode.mp3"))
    let show = PodcastShow(
        catalogID: "", title: "Publisher Show", author: "Publisher", description: "",
        feedURL: feedURL, artworkURL: nil, websiteURL: nil, genre: "", country: ""
    )
    let episode = PodcastEpisode(
        id: "episode-1", feedURL: feedURL, guid: "episode-1", title: "Episode",
        showTitle: show.title, author: show.author, description: "", audioURL: audioURL,
        websiteURL: nil, artworkURL: nil, mimeType: "audio/mpeg", publishedAt: nil,
        duration: 600, position: 0, completed: false
    )

    let firstStore = PodcastLibraryStore(defaults: defaults)
    firstStore.upsert(PodcastFeed(show: show, episodes: [episode]), subscribed: true)
    firstStore.updateProgress(episodeID: episode.id, feedURL: feedURL, position: 125, duration: 600)

    let restoredStore = PodcastLibraryStore(defaults: defaults)
    let restored = try #require(restoredStore.cachedEpisodes(for: feedURL).first)
    #expect(restoredStore.subscriptions == [show])
    #expect(restored.position == 125)
    #expect(!restored.completed)
}

@Test
func podcastControlsMatchAndroidContract() {
    #expect(PodcastPlaybackSettings.speeds == [0.75, 1, 1.25, 1.5, 2])
    #expect(PodcastPlaybackSettings.sleepTimerMinutes == [15, 30, 45, 60])
}

@Test
func podcastDurationRejectsMalformedOrOutOfRangeSegments() {
    #expect(parsePodcastDuration("01:02:03") == 3_723)
    #expect(parsePodcastDuration("90.5") == 90.5)
    #expect(parsePodcastDuration("1:bad:03") == 0)
    #expect(parsePodcastDuration("1::03") == 0)
    #expect(parsePodcastDuration("00:61") == 0)
    #expect(parsePodcastDuration("1:60:00") == 0)
    #expect(parsePodcastDuration("-5") == 0)
}

@MainActor
@Test
func podcastCacheBoundsUnsubscribedFeedsAndEpisodeCounts() throws {
    let suiteName = "PodcastCacheTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let store = PodcastLibraryStore(defaults: defaults)

    for feedIndex in 0..<12 {
        let feedURL = try #require(URL(string: "https://publisher.example/\(feedIndex).xml"))
        let show = PodcastShow(
            catalogID: "", title: "Show \(feedIndex)", author: "Publisher", description: "",
            feedURL: feedURL, artworkURL: nil, websiteURL: nil, genre: "", country: ""
        )
        let episodeCount = feedIndex == 11 ? 260 : 1
        let episodes = try (0..<episodeCount).map { episodeIndex in
            PodcastEpisode(
                id: "\(feedIndex)-\(episodeIndex)", feedURL: feedURL, guid: "\(episodeIndex)",
                title: "Episode \(episodeIndex)", showTitle: show.title, author: show.author,
                description: "", audioURL: try #require(URL(
                    string: "https://publisher.example/audio/\(feedIndex)-\(episodeIndex).mp3"
                )), websiteURL: nil, artworkURL: nil, mimeType: "audio/mpeg",
                publishedAt: nil, duration: 60, position: 0, completed: false
            )
        }
        store.upsert(PodcastFeed(show: show, episodes: episodes))
    }

    let oldestFeed = try #require(URL(string: "https://publisher.example/0.xml"))
    let newestFeed = try #require(URL(string: "https://publisher.example/11.xml"))
    #expect(store.episodesByFeed.count == 10)
    #expect(store.cachedEpisodes(for: oldestFeed).isEmpty)
    #expect(store.cachedEpisodes(for: newestFeed).count == 250)

    let restored = PodcastLibraryStore(defaults: defaults)
    #expect(restored.episodesByFeed.count == 10)
    #expect(restored.cachedEpisodes(for: newestFeed).count == 250)
}
