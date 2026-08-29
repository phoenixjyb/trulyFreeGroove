package com.trulyfreemusic.opengroove.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeCatalogTest {
    private val catalog = YouTubeCatalog("test-key", "com.example.app", "AA11")

    @Test
    fun searchParsingKeepsOnlyCanonicalUniqueVideoIds() {
        val payload = """
            {"items":[
              {"id":{"videoId":"M7lc1UVf-VE"}},
              {"id":{"videoId":"bad id"}},
              {"id":{"videoId":"M7lc1UVf-VE"}},
              {"id":{"channelId":"channel"}}
            ]}
        """.trimIndent()

        assertEquals(listOf("M7lc1UVf-VE"), catalog.parseVideoIds(payload))
    }

    @Test
    fun videoDetailsPreserveOfficialMetadataAndRejectNonEmbeddableItems() {
        val payload = """
            {"items":[
              {
                "id":"M7lc1UVf-VE",
                "snippet":{
                  "title":"A &amp; B",
                  "channelTitle":"Creator &quot;One&quot;",
                  "publishedAt":"2026-01-02T03:04:05Z",
                  "liveBroadcastContent":"live",
                  "thumbnails":{"high":{"url":"https://i.ytimg.com/vi/M7lc1UVf-VE/hqdefault.jpg"}}
                },
                "contentDetails":{"duration":"PT1H2M3S"},
                "status":{"embeddable":true,"madeForKids":false}
              },
              {
                "id":"abcdefghijk",
                "snippet":{
                  "title":"Blocked",
                  "channelTitle":"Creator",
                  "thumbnails":{"default":{"url":"https://i.ytimg.com/blocked.jpg"}}
                },
                "contentDetails":{"duration":"PT2M"},
                "status":{"embeddable":false}
              }
            ]}
        """.trimIndent()

        val video = catalog.parseVideos(payload, refreshedAtMs = 123L).single()

        assertEquals("A & B", video.title)
        assertEquals("Creator \"One\"", video.channelTitle)
        assertEquals(3_723, video.durationSeconds)
        assertEquals(123L, video.metadataRefreshedAtMs)
        assertFalse(video.madeForKids ?: true)
        assertTrue(video.isLive)
        assertTrue(video.isWatchable())
    }

    @Test
    fun isoDurationsFailClosed() {
        assertEquals(90, parseYouTubeDuration("PT1M30S"))
        assertEquals(86_400, parseYouTubeDuration("P1D"))
        assertEquals(0, parseYouTubeDuration("1:30"))
        assertEquals(0, parseYouTubeDuration("PT"))
        assertEquals(0, parseYouTubeDuration(""))
    }
}
