package com.trulyfreemusic.opengroove.podcast

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PodcastEpisodeTest {
    private val episode = PodcastEpisode(
        episodeId = "episode-1",
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
        publishedAt = 0,
        durationMs = 60_000,
    )

    @Test
    fun authorizedPublisherUrlIsPlayable() = assertTrue(episode.isPlayable())

    @Test
    fun searchMatchesEnglishChineseAndCantoneseMetadata() {
        assertTrue(episode.matchesLibraryQuery("廣東話"))
        assertTrue(episode.matchesLibraryQuery("全球"))
        assertTrue(episode.matchesLibraryQuery("jane"))
        assertFalse(episode.matchesLibraryQuery("technology"))
    }
}
