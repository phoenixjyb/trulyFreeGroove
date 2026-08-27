package com.trulyfreemusic.opengroove.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackTest {
    private fun track(
        mode: PlaybackMode = PlaybackMode.DIRECT_AUTHORIZED,
        stream: String = "https://media.example/song.mp3",
        license: String = "https://creativecommons.org/licenses/by/4.0/",
    ) = Track(
        id = "provider:1",
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationSeconds = 123,
        artworkUrl = "",
        providerName = "Provider",
        streamUrl = stream,
        sourceUrl = "https://example.com/song",
        licenseUrl = license,
        playbackMode = mode,
    )

    @Test
    fun authorizedHttpsTrackCanPlay() = assertTrue(track().isDirectPlaybackAllowed())

    @Test
    fun externalProviderCannotBePlayedDirectly() =
        assertFalse(track(mode = PlaybackMode.EXTERNAL_ONLY).isDirectPlaybackAllowed())

    @Test
    fun cleartextOrUnlicensedTrackCannotPlay() {
        assertFalse(track(stream = "http://media.example/song.mp3").isDirectPlaybackAllowed())
        assertFalse(track(license = "").isDirectPlaybackAllowed())
    }
}
