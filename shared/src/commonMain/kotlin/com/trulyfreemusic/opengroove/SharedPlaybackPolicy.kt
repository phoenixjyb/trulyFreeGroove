package com.trulyfreemusic.opengroove

import com.trulyfreemusic.opengroove.model.PlaybackMode

/** The playback boundary used by both platform applications. */
object SharedPlaybackPolicy {
    fun isRadioStreamAllowed(id: String, name: String, streamUrl: String): Boolean =
        id.isNotBlank() && name.isNotBlank() && streamUrl.isPublicHttpUrl()

    fun isPodcastStreamAllowed(title: String, audioUrl: String): Boolean =
        title.isNotBlank() && audioUrl.isPublicHttpUrl()

    fun isDirectTrackPlaybackAllowed(
        playbackMode: PlaybackMode,
        streamUrl: String,
        licenseUrl: String,
    ): Boolean = when (playbackMode) {
        PlaybackMode.DIRECT_AUTHORIZED -> streamUrl.startsWith("https://") && licenseUrl.isPublicHttpUrl()
        PlaybackMode.HOME_LIBRARY -> Regex("^home-music://[a-f0-9]{32}/[^/?#]+$").matches(streamUrl)
        PlaybackMode.EXTERNAL_ONLY -> false
    }

    private fun String.isPublicHttpUrl(): Boolean =
        startsWith("https://") || startsWith("http://")
}
