package com.trulyfreemusic.opengroove.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RadioStationTest {
    private fun station(id: String = "station-id", url: String = "https://radio.example/live") = RadioStation(
        id = id,
        name = "Example Radio",
        streamUrl = url,
        homepageUrl = "https://radio.example",
        faviconUrl = "",
        country = "Canada",
        countryCode = "CA",
        language = "English",
        tags = listOf("jazz"),
        codec = "MP3",
        bitrate = 128,
        votes = 10,
    )

    @Test fun httpsAndHttpStreamsArePlayable() {
        assertTrue(station().isPlayable())
        assertTrue(station(url = "http://radio.example/live").isPlayable())
    }

    @Test fun unsupportedSchemesOrMissingIdentityAreRejected() {
        assertFalse(station(url = "file:///tmp/music.mp3").isPlayable())
        assertFalse(station(id = "").isPlayable())
    }

    @Test fun directoryStatusDoesNotPretendToBeAPlaybackGuarantee() {
        assertTrue(station().copy(isOnline = false).isPlayable())
    }

    @Test fun recentOnlineCheckIsDescribedWithoutClaimingVerifiedLiveContent() {
        val checked = station().copy(
            isOnline = true,
            lastCheckedAt = "2026-08-27T12:00:00Z",
        )
        assertEquals(
            "Online when checked • 3h ago",
            stationAvailabilityLine(checked, Instant.parse("2026-08-27T15:45:00Z")),
        )
    }

    @Test fun missingCheckTimeStillReportsEvidenceScope() {
        assertEquals("Offline when checked", stationAvailabilityLine(station().copy(isOnline = false)))
    }
}
