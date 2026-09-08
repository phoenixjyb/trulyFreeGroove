package com.trulyfreemusic.opengroove.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
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

    @Test fun chineseDirectoryFiltersCanCombineCountryAndLanguage() {
        val parameters = radioSearchParameters(
            name = " 香港電台 ",
            countryCode = "hk",
            tag = null,
            language = "Cantonese",
            offset = -4,
        )

        assertEquals("香港電台", parameters["name"])
        assertEquals("HK", parameters["countrycode"])
        assertEquals("cantonese", parameters["language"])
        assertEquals("0", parameters["offset"])
        assertEquals("true", parameters["hidebroken"])
    }

    @Test fun chineseStationMetadataIsPreservedFromTheDirectory() {
        val payload = JSONArray(
            """[{
              "stationuuid":"hk-1",
              "name":"香港電台第一台",
              "url_resolved":"https://radio.example/hk.mp3",
              "country":"Hong Kong",
              "countrycode":"HK",
              "language":"cantonese,china",
              "tags":"news,talk",
              "codec":"MP3",
              "lastcheckok":1
            }]""",
        )

        val parsed = RadioBrowserCatalog().parseStations(payload).single()

        assertEquals("香港電台第一台", parsed.name)
        assertEquals("HK", parsed.countryCode)
        assertEquals("cantonese,china", parsed.language)
        assertTrue(parsed.isOnline)
    }
}
