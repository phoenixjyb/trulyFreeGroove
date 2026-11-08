package com.trulyfreemusic.opengroove

import com.trulyfreemusic.opengroove.model.PlaybackMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPlaybackPolicyTest {
    @Test fun privateLibraryRequiresAnOpaqueReferenceAndCannotAuthorizeWebStreams() {
        assertTrue(SharedPlaybackPolicy.isDirectTrackPlaybackAllowed(PlaybackMode.HOME_LIBRARY,
            "home-music://0123456789abcdef0123456789abcdef/track", ""))
        for (url in listOf("http://192.168.1.2/audio.mp3", "https://youtube.example/audio", "home-music://bad/track", "home-music://0123456789abcdef0123456789abcdef/track?t=secret")) {
            assertFalse(SharedPlaybackPolicy.isDirectTrackPlaybackAllowed(PlaybackMode.HOME_LIBRARY, url, ""))
        }
    }

    @Test
    fun commercialPlatformHandoffsCannotBecomeDirectStreams() {
        assertFalse(
            SharedPlaybackPolicy.isDirectTrackPlaybackAllowed(
                PlaybackMode.EXTERNAL_ONLY,
                "https://youtube.example/audio",
                "https://youtube.example/terms",
            ),
        )
    }

    @Test
    fun directMusicRequiresHttpsAndLicenseEvidence() {
        assertTrue(
            SharedPlaybackPolicy.isDirectTrackPlaybackAllowed(
                PlaybackMode.DIRECT_AUTHORIZED,
                "https://media.example/song.mp3",
                "https://creativecommons.org/licenses/by/4.0/",
            ),
        )
        assertFalse(
            SharedPlaybackPolicy.isDirectTrackPlaybackAllowed(
                PlaybackMode.DIRECT_AUTHORIZED,
                "http://media.example/song.mp3",
                "https://creativecommons.org/licenses/by/4.0/",
            ),
        )
    }
}
