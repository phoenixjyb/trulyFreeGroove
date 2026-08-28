package com.trulyfreemusic.opengroove.model

import com.trulyfreemusic.opengroove.SharedPlaybackPolicy

enum class PlaybackMode {
    DIRECT_AUTHORIZED,
    EXTERNAL_ONLY,
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val artworkUrl: String,
    val providerName: String,
    val streamUrl: String,
    val sourceUrl: String,
    val licenseUrl: String,
    val playbackMode: PlaybackMode,
) {
    fun isDirectPlaybackAllowed(): Boolean =
        SharedPlaybackPolicy.isDirectTrackPlaybackAllowed(playbackMode, streamUrl, licenseUrl)
}
