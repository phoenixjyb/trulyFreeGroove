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
    ): Boolean = playbackMode == PlaybackMode.DIRECT_AUTHORIZED &&
        streamUrl.startsWith("https://") && licenseUrl.isPublicHttpUrl()

    private fun String.isPublicHttpUrl(): Boolean =
        startsWith("https://") || startsWith("http://")
}
