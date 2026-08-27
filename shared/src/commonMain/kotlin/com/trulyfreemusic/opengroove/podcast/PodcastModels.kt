package com.trulyfreemusic.opengroove.podcast

import com.trulyfreemusic.opengroove.SharedPlaybackPolicy

data class PodcastShow(
    val catalogId: String,
    val title: String,
    val author: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String,
    val websiteUrl: String,
    val genre: String,
    val country: String,
    val subscribed: Boolean = false,
) {
    fun isValid(): Boolean = title.isNotBlank() && feedUrl.isHttpUrl()
}

data class PodcastEpisode(
    val episodeId: String,
    val feedUrl: String,
    val guid: String,
    val title: String,
    val showTitle: String,
    val author: String,
    val description: String,
    val audioUrl: String,
    val websiteUrl: String,
    val artworkUrl: String,
    val mimeType: String,
    val publishedAt: Long,
    val durationMs: Long,
    val positionMs: Long = 0L,
    val completed: Boolean = false,
) {
    fun isPlayable(): Boolean = SharedPlaybackPolicy.isPodcastStreamAllowed(title, audioUrl)

    fun matchesLibraryQuery(query: String): Boolean {
        val cleanQuery = query.trim()
        return cleanQuery.isBlank() || listOf(title, showTitle, author, description).any {
            it.contains(cleanQuery, ignoreCase = true)
        }
    }
}

data class PodcastFeed(
    val show: PodcastShow,
    val episodes: List<PodcastEpisode>,
)

fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")
