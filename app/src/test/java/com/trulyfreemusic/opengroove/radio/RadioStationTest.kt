package com.trulyfreemusic.opengroove.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
