package com.trulyfreemusic.opengroove.radio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioStationTest {
    private fun station(url: String = "https://radio.example/live.m3u8") = RadioStation(
        id = "station-1",
        name = "OpenGroove Radio",
        streamUrl = url,
        homepageUrl = "https://radio.example",
        faviconUrl = "",
        country = "United Kingdom",
        countryCode = "GB",
        language = "English",
        tags = listOf("news"),
        codec = "AAC",
        bitrate = 128,
        votes = 10,
    )

    @Test
    fun publicHttpAndHttpsStreamsArePlayable() {
        assertTrue(station().isPlayable())
        assertTrue(station("http://radio.example/live.mp3").isPlayable())
    }

    @Test
    fun unsupportedSchemesFailClosed() = assertFalse(station("file:///private/audio.mp3").isPlayable())
}
