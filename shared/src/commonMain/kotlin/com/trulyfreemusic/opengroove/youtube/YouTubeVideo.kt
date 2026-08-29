package com.trulyfreemusic.opengroove.youtube

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val publishedAtMs: Long,
    val embeddable: Boolean,
    val madeForKids: Boolean?,
    val isLive: Boolean,
    val metadataRefreshedAtMs: Long,
) {
    fun isWatchable(): Boolean = YouTubePlaybackPolicy.isWatchable(videoId, embeddable)

    fun watchUrl(): String? = YouTubePlaybackPolicy.watchUrl(videoId)
}

/** Fail-closed reference rules for official YouTube embedded playback. */
object YouTubePlaybackPolicy {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun isCanonicalVideoId(videoId: String): Boolean = videoIdPattern.matches(videoId)

    fun isWatchable(videoId: String, embeddable: Boolean): Boolean =
        embeddable && isCanonicalVideoId(videoId)

    fun watchUrl(videoId: String): String? =
        videoId.takeIf(::isCanonicalVideoId)?.let { "https://www.youtube.com/watch?v=$it" }
}
