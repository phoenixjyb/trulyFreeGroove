package com.trulyfreemusic.opengroove

import com.trulyfreemusic.opengroove.model.PlaybackMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPlaybackPolicyTest {
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
