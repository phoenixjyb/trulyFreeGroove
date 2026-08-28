package com.trulyfreemusic.opengroove.podcast

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PodcastProgressPolicyTest {
    @Test
    fun shortEpisodesRequireNinetyPercent() {
        assertFalse(PodcastProgressPolicy.isCompleted(positionMs = 0L, durationMs = 20_000L))
        assertFalse(PodcastProgressPolicy.isCompleted(positionMs = 17_999L, durationMs = 20_000L))
        assertTrue(PodcastProgressPolicy.isCompleted(positionMs = 18_000L, durationMs = 20_000L))
    }

    @Test
    fun longEpisodesCompleteInsideFinalThirtySeconds() {
        assertFalse(PodcastProgressPolicy.isCompleted(positionMs = 569_999L, durationMs = 600_000L))
        assertTrue(PodcastProgressPolicy.isCompleted(positionMs = 570_000L, durationMs = 600_000L))
    }

    @Test
    fun unknownDurationNeverCompletes() {
        assertFalse(PodcastProgressPolicy.isCompleted(positionMs = 10_000L, durationMs = 0L))
    }
}
