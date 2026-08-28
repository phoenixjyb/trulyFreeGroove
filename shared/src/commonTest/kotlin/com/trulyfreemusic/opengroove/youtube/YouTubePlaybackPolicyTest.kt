package com.trulyfreemusic.opengroove.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubePlaybackPolicyTest {
    @Test
    fun onlyEmbeddableCanonicalVideoIdsAreWatchable() {
        assertTrue(YouTubePlaybackPolicy.isWatchable("M7lc1UVf-VE", embeddable = true))
        assertFalse(YouTubePlaybackPolicy.isWatchable("M7lc1UVf-VE", embeddable = false))
        assertFalse(YouTubePlaybackPolicy.isWatchable("not a video id", embeddable = true))
    }

    @Test
    fun watchUrlsAreOfficialHttpsReferences() {
        assertEquals(
            "https://www.youtube.com/watch?v=M7lc1UVf-VE",
            YouTubePlaybackPolicy.watchUrl("M7lc1UVf-VE"),
        )
        assertNull(YouTubePlaybackPolicy.watchUrl("../../audio"))
    }
}
