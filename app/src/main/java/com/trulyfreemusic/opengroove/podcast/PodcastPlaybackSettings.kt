package com.trulyfreemusic.opengroove.podcast

val PodcastPlaybackSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
val PodcastSleepTimerMinutes = listOf(15, 30, 45, 60)

fun podcastSleepDurationMs(minutes: Int?): Long {
    if (minutes == null) return 0L
    require(minutes in PodcastSleepTimerMinutes) { "Unsupported podcast sleep timer." }
    return minutes * 60_000L
}
