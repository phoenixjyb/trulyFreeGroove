package com.trulyfreemusic.opengroove.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastPlaybackSettingsTest {
    @Test fun supportedPlaybackSpeedsStayWithinMediaControllerBounds() {
        assertEquals(listOf(0.75f, 1f, 1.25f, 1.5f, 2f), PodcastPlaybackSpeeds)
        assertTrue(PodcastPlaybackSpeeds.all { it in 0.5f..2f })
    }

    @Test fun sleepTimerConvertsOnlySupportedChoices() {
        assertEquals(0L, podcastSleepDurationMs(null))
        assertEquals(900_000L, podcastSleepDurationMs(15))
        assertEquals(3_600_000L, podcastSleepDurationMs(60))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedSleepTimerIsRejected() {
        podcastSleepDurationMs(10)
    }
}
