package com.trulyfreemusic.opengroove.radio

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun taiwanUsesTheRequestedProductLabelWithoutChangingItsDirectoryCode() {
        val country = RadioCountry("Taiwan, Republic Of China", "TW", 120)

        assertEquals("台湾省", country.displayName)
        assertEquals("TW", country.code)
        assertEquals("台湾省", ChineseRadioQuickFilters.single { it.countryCode == "TW" }.label)
        assertEquals(
            "台湾省",
            station().copy(country = "Taiwan, Republic Of China", countryCode = "TW").countryDisplayName,
        )
    }

    @Test
    fun hongKongQuickFilterCombinesRegionAndCantoneseLanguage() {
        val filter = ChineseRadioQuickFilters.single { it.id == "hong-kong-cantonese" }

        assertEquals("HK", filter.countryCode)
        assertEquals("cantonese", filter.language?.directoryValue)
    }
}
