package com.trulyfreemusic.opengroove.podcast

/** Shared completion semantics used by platform persistence adapters. */
object PodcastProgressPolicy {
    private const val END_WINDOW_MS = 30_000L

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val ninetyPercent = durationMs - durationMs / 10L
        val finalWindow = (durationMs - END_WINDOW_MS).coerceAtLeast(0L)
        val completionThreshold = maxOf(ninetyPercent, finalWindow)
        return positionMs.coerceAtLeast(0L) >= completionThreshold
    }
}
