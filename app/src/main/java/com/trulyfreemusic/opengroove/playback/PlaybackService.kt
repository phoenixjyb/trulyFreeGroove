package com.trulyfreemusic.opengroove.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerEndAtMs = 0L
    private val sleepTimerAction = Runnable {
        exoPlayer?.pause()
        sleepTimerEndAtMs = 0L
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { setWakeMode(C.WAKE_MODE_NETWORK) }
        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val base = super.onConnect(session, controller)
                    if (!base.isAccepted) return base
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            base.availableSessionCommands.buildUpon()
                                .add(PlaybackCommands.setSleepTimer)
                                .add(PlaybackCommands.getSleepTimer)
                                .build(),
                        )
                        .setAvailablePlayerCommands(base.availablePlayerCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    return when (customCommand.customAction) {
                        PlaybackCommands.ACTION_SET_SLEEP_TIMER -> {
                            setSleepTimer(args.getLong(PlaybackCommands.EXTRA_DURATION_MS))
                            Futures.immediateFuture(timerResult())
                        }
                        PlaybackCommands.ACTION_GET_SLEEP_TIMER -> Futures.immediateFuture(timerResult())
                        else -> super.onCustomCommand(session, controller, customCommand, args)
                    }
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        sleepTimerHandler.removeCallbacks(sleepTimerAction)
        sleepTimerEndAtMs = 0L
        mediaSession?.run {
            player.release()
            release()
        }
        exoPlayer = null
        mediaSession = null
        super.onDestroy()
    }

    private fun setSleepTimer(durationMs: Long) {
        sleepTimerHandler.removeCallbacks(sleepTimerAction)
        if (durationMs <= 0L) {
            sleepTimerEndAtMs = 0L
            return
        }
        sleepTimerEndAtMs = System.currentTimeMillis() + durationMs
        sleepTimerHandler.postDelayed(sleepTimerAction, durationMs)
    }

    private fun timerResult(): SessionResult = SessionResult(
        SessionResult.RESULT_SUCCESS,
        Bundle().apply { putLong(PlaybackCommands.EXTRA_END_AT_MS, sleepTimerEndAtMs) },
    )
}
