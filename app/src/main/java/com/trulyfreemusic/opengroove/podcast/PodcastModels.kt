package com.trulyfreemusic.opengroove.podcast

import com.trulyfreemusic.opengroove.data.SearchLanguage
import java.security.MessageDigest

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
    fun isValid(): Boolean =
        title.isNotBlank() && feedUrl.isHttpUrl()
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
    fun isPlayable(): Boolean = title.isNotBlank() && audioUrl.isHttpUrl()
}

data class PodcastFeed(
    val show: PodcastShow,
    val episodes: List<PodcastEpisode>,
)

data class PodcastUiState(
    val query: String = "",
    val libraryQuery: String = "",
    val language: SearchLanguage = SearchLanguage.ALL,
    val results: List<PodcastShow> = emptyList(),
    val selectedShow: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingFeed: Boolean = false,
    val error: String? = null,
)

internal fun PodcastEpisode.matchesLibraryQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    return cleanQuery.isBlank() || listOf(title, showTitle, author, description).any {
        it.contains(cleanQuery, ignoreCase = true)
    }
}

internal fun stableEpisodeId(feedUrl: String, guid: String, audioUrl: String): String {
    val identity = "$feedUrl\u001F${guid.ifBlank { audioUrl }}"
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")

internal fun parsePodcastDuration(value: String): Long {
    val clean = value.trim()
    if (clean.isBlank()) return 0L
    val parts = clean.split(':').mapNotNull(String::toLongOrNull)
    val seconds = when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> return 0L
    }
    return seconds.coerceAtLeast(0L) * 1_000L
}
