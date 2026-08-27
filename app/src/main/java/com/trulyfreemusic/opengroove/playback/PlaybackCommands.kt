package com.trulyfreemusic.opengroove.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

object PlaybackCommands {
    const val ACTION_SET_SLEEP_TIMER = "com.trulyfreemusic.opengroove.SET_SLEEP_TIMER"
    const val ACTION_GET_SLEEP_TIMER = "com.trulyfreemusic.opengroove.GET_SLEEP_TIMER"
    const val EXTRA_DURATION_MS = "duration_ms"
    const val EXTRA_END_AT_MS = "end_at_ms"

    val setSleepTimer = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)
    val getSleepTimer = SessionCommand(ACTION_GET_SLEEP_TIMER, Bundle.EMPTY)

    fun timerArguments(durationMs: Long) = Bundle().apply {
        putLong(EXTRA_DURATION_MS, durationMs.coerceAtLeast(0L))
    }
}
