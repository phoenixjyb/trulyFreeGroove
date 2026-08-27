package com.trulyfreemusic.opengroove.library

import com.trulyfreemusic.opengroove.model.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyLibraryMigrationTest {
    @Test fun playlistJsonRetainsThePlaybackAuthorizationBoundary() {
        val payload = """{
          "Favourites": [{
            "id":"commons:1","title":"Song","artist":"Artist","album":"CC BY",
            "durationSeconds":90,"artworkUrl":"","providerName":"Wikimedia Commons",
            "streamUrl":"https://upload.wikimedia.org/song.ogg",
            "sourceUrl":"https://commons.wikimedia.org/wiki/File:Song.ogg",
            "licenseUrl":"https://creativecommons.org/licenses/by/4.0/",
            "playbackMode":"DIRECT_AUTHORIZED"
          }]
        }"""
        val track = decodeLegacyPlaylists(payload).getValue("Favourites").single()
        assertEquals(PlaybackMode.DIRECT_AUTHORIZED, track.playbackMode)
        assertTrue(track.isDirectPlaybackAllowed())
    }

    @Test fun malformedLegacyStationIsSkippedWithoutLosingValidStations() {
        val payload = """[
          {"id":"ok","name":"Station","streamUrl":"https://radio.example/live","tags":["news"]},
          {"id":"bad","name":"Broken","streamUrl":"file:///tmp/audio"}
        ]"""
        val stations = decodeLegacyStations(payload)
        assertEquals(1, stations.size)
        assertEquals("ok", stations.single().id)
    }
}
