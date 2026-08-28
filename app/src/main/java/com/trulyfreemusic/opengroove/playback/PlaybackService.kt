package com.trulyfreemusic.opengroove.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.trulyfreemusic.opengroove.library.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val progressWriteMutex = Mutex()
    private val library by lazy { LibraryRepository(applicationContext) }
    private var sleepTimerEndAtMs = 0L
    private var lastPodcastDurationMs = 0L
    private var lastSavedPodcastPositionMs = 0L
    private val sleepTimerAction = Runnable {
        exoPlayer?.pause()
        sleepTimerEndAtMs = 0L
    }
    private val progressCheckpointAction = object : Runnable {
        override fun run() {
            checkpointCurrentPodcast(force = false)
            progressHandler.postDelayed(this, PROGRESS_CHECKPOINT_INTERVAL_MS)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { setWakeMode(C.WAKE_MODE_NETWORK) }
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val previousEpisode = oldPosition.mediaItem?.toPodcastEpisodeOrNull() ?: return
                val nextEpisodeId = newPosition.mediaItem?.toPodcastEpisodeOrNull()?.episodeId
                if (previousEpisode.episodeId == nextEpisodeId) return
                val durationMs = maxOf(lastPodcastDurationMs, previousEpisode.durationMs)
                val positionMs = if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && durationMs > 0L) {
                    durationMs
                } else {
                    oldPosition.positionMs.coerceAtLeast(0L)
                }
                persistPodcastProgress(previousEpisode.episodeId, positionMs, durationMs)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val episode = mediaItem?.toPodcastEpisodeOrNull()
                lastPodcastDurationMs = episode?.durationMs?.coerceAtLeast(0L) ?: 0L
                lastSavedPodcastPositionMs = episode?.positionMs?.coerceAtLeast(0L) ?: 0L
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) checkpointCurrentPodcast(force = true)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) checkpointCurrentPodcast(force = true, useDuration = true)
            }
        })
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
        progressHandler.postDelayed(progressCheckpointAction, PROGRESS_CHECKPOINT_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        sleepTimerHandler.removeCallbacks(sleepTimerAction)
        progressHandler.removeCallbacks(progressCheckpointAction)
        sleepTimerEndAtMs = 0L
        mediaSession?.run {
            player.release()
            release()
        }
        exoPlayer = null
        mediaSession = null
        progressScope.cancel()
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

    private fun checkpointCurrentPodcast(force: Boolean, useDuration: Boolean = false) {
        val player = exoPlayer ?: return
        val episode = player.currentMediaItem?.toPodcastEpisodeOrNull() ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val playerDurationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val durationMs = maxOf(playerDurationMs, episode.durationMs, lastPodcastDurationMs)
        val effectivePositionMs = if (useDuration && durationMs > 0L) durationMs else positionMs

        lastPodcastDurationMs = durationMs
        if (!force && abs(effectivePositionMs - lastSavedPodcastPositionMs) < PROGRESS_CHECKPOINT_INTERVAL_MS) return
        lastSavedPodcastPositionMs = effectivePositionMs
        persistPodcastProgress(episode.episodeId, effectivePositionMs, durationMs)
    }

    private fun persistPodcastProgress(episodeId: String, positionMs: Long, durationMs: Long) {
        progressScope.launch {
            progressWriteMutex.withLock {
                library.updateEpisodeProgress(episodeId, positionMs, durationMs)
            }
        }
    }

    private companion object {
        const val PROGRESS_CHECKPOINT_INTERVAL_MS = 15_000L
    }
}
