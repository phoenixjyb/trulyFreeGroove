package com.trulyfreemusic.opengroove.podcast

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastCatalogTest {
    @Test fun durationSupportsSecondsMinutesAndHours() {
        assertEquals(45_000L, parsePodcastDuration("45"))
        assertEquals(754_000L, parsePodcastDuration("12:34"))
        assertEquals(3_723_000L, parsePodcastDuration("1:02:03"))
        assertEquals(90_500L, parsePodcastDuration("90.5"))
        assertEquals(0L, parsePodcastDuration("unknown"))
        assertEquals(0L, parsePodcastDuration("1:bad:03"))
        assertEquals(0L, parsePodcastDuration("1::03"))
        assertEquals(0L, parsePodcastDuration("00:61"))
        assertEquals(0L, parsePodcastDuration("1:60:00"))
    }

    @Test fun episodeIdentityIsStableAndFeedScoped() {
        val first = stableEpisodeId("https://example.com/feed", "episode-1", "https://cdn.example/one.mp3")
        assertEquals(first, stableEpisodeId("https://example.com/feed", "episode-1", "https://cdn.example/two.mp3"))
        assertFalse(first == stableEpisodeId("https://other.example/feed", "episode-1", "https://cdn.example/one.mp3"))
    }

    @Test fun appleDiscoveryKeepsMetadataButDoesNotReusePromoArtwork() {
        val root = JSONObject(
            """{
              "results": [{
                "collectionId": 42,
                "collectionName": "A Cantonese Show",
                "artistName": "Publisher",
                "feedUrl": "https://publisher.example/feed.xml",
                "artworkUrl600": "https://is1-ssl.mzstatic.com/art.jpg",
                "collectionViewUrl": "https://podcasts.apple.com/show/id42",
                "primaryGenreName": "News",
                "country": "HKG"
              }]
            }""",
        )
        val show = ApplePodcastCatalog().parseResults(root).single()
        assertEquals("A Cantonese Show", show.title)
        assertEquals("", show.artworkUrl)
        assertEquals("https://publisher.example/feed.xml", show.feedUrl)
    }

    @Test fun publisherRssProducesPlayableEpisodesAndPublisherArtwork() {
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Publisher Show</title>
                <link>https://publisher.example/show</link>
                <description>A &amp; B</description>
                <itunes:author>Publisher</itunes:author>
                <itunes:image href="https://publisher.example/art.jpg"/>
                <item>
                  <guid>episode-one</guid>
                  <title>First episode</title>
                  <pubDate>Wed, 27 Aug 2026 10:00:00 GMT</pubDate>
                  <itunes:duration>12:34</itunes:duration>
                  <description><![CDATA[<p>Hello listeners</p>]]></description>
                  <enclosure url="https://publisher.example/episode.mp3" type="audio/mpeg"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val feed = PodcastFeedCatalog().parse(
            xml.byteInputStream(),
            "https://publisher.example/feed.xml",
        )
        assertEquals("Publisher Show", feed.show.title)
        assertEquals("https://publisher.example/art.jpg", feed.show.artworkUrl)
        assertEquals(754_000L, feed.episodes.single().durationMs)
        assertEquals("Hello listeners", feed.episodes.single().description)
        assertTrue(feed.episodes.single().isPlayable())
    }

    @Test fun publisherRssPreservesTextInsideNestedMarkup() {
        val xml = """
            <rss><channel><title>Nested Notes</title><item><title>Episode</title>
            <description>Hello <b>world</b> after</description>
            <enclosure url="https://publisher.example/episode.mp3" type="audio/mpeg"/>
            </item></channel></rss>
        """.trimIndent()

        val feed = PodcastFeedCatalog().parse(
            xml.byteInputStream(),
            "https://publisher.example/feed.xml",
        )

        assertEquals("Hello world after", feed.episodes.single().description)
    }

    @Test fun librarySearchMatchesEpisodeShowHostAndMultilingualText() {
        val episode = PodcastEpisode(
            episodeId = "episode-one",
            feedUrl = "https://publisher.example/feed.xml",
            guid = "one",
            title = "今日廣東話",
            showTitle = "Global News 全球新聞",
            author = "Jane Reporter",
            description = "A daily current affairs briefing",
            audioUrl = "https://publisher.example/episode.mp3",
            websiteUrl = "",
            artworkUrl = "",
            mimeType = "audio/mpeg",
            publishedAt = 0L,
            durationMs = 60_000L,
        )

        assertTrue(episode.matchesLibraryQuery("廣東話"))
        assertTrue(episode.matchesLibraryQuery("全球"))
        assertTrue(episode.matchesLibraryQuery("jane"))
        assertTrue(episode.matchesLibraryQuery("CURRENT AFFAIRS"))
        assertTrue(episode.matchesLibraryQuery("  "))
        assertFalse(episode.matchesLibraryQuery("technology"))
    }
}
