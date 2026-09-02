package com.trulyfreemusic.opengroove.playback

import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicQueuePersistenceTest {
    @Test fun roundTripRetainsQueuePositionAndPlaybackModes() {
        val snapshot = MusicQueueSnapshot(
            tracks = listOf(track("one"), track("two")),
            currentIndex = 1,
            positionMs = 42_500L,
            shuffleEnabled = true,
            repeatMode = 2,
        )

        val restored = decodeMusicQueueSnapshot(encodeMusicQueueSnapshot(snapshot)!!)

        assertEquals(listOf("one", "two"), restored?.tracks?.map(Track::id))
        assertEquals(1, restored?.currentIndex)
        assertEquals(42_500L, restored?.positionMs)
        assertTrue(restored?.shuffleEnabled == true)
        assertEquals(2, restored?.repeatMode)
    }

    @Test fun restoreFailsClosedAndRemapsTheCurrentTrack() {
        val snapshot = MusicQueueSnapshot(
            tracks = listOf(
                track("external", playbackMode = PlaybackMode.EXTERNAL_ONLY),
                track("allowed"),
            ),
            currentIndex = 1,
            positionMs = 1_000L,
            shuffleEnabled = false,
            repeatMode = 0,
        )

        val restored = decodeMusicQueueSnapshot(encodeMusicQueueSnapshot(snapshot)!!)

        assertEquals(listOf("allowed"), restored?.tracks?.map(Track::id))
        assertEquals(0, restored?.currentIndex)
        assertFalse(restored?.shuffleEnabled ?: true)
    }

    @Test fun duplicateTracksRetainTheExactCurrentQueuePosition() {
        val duplicate = track("same")
        val snapshot = MusicQueueSnapshot(
            tracks = listOf(duplicate, duplicate),
            currentIndex = 1,
            positionMs = 8_000L,
            shuffleEnabled = false,
            repeatMode = 0,
        )

        val restored = decodeMusicQueueSnapshot(encodeMusicQueueSnapshot(snapshot)!!)

        assertEquals(2, restored?.tracks?.size)
        assertEquals(1, restored?.currentIndex)
    }

    @Test fun malformedOrUnlicensedQueuesAreNotRestored() {
        assertNull(decodeMusicQueueSnapshot("not-json"))
        assertNull(
            encodeMusicQueueSnapshot(
                MusicQueueSnapshot(
                    tracks = listOf(track("external", playbackMode = PlaybackMode.EXTERNAL_ONLY)),
                    currentIndex = 0,
                    positionMs = 0L,
                    shuffleEnabled = false,
                    repeatMode = 0,
                ),
            ),
        )
    }

    private fun track(id: String, playbackMode: PlaybackMode = PlaybackMode.DIRECT_AUTHORIZED) = Track(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        durationSeconds = 120,
        artworkUrl = "https://example.com/$id.jpg",
        providerName = "Test Commons",
        streamUrl = "https://example.com/$id.ogg",
        sourceUrl = "https://example.com/source/$id",
        licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
        playbackMode = playbackMode,
    )
}
