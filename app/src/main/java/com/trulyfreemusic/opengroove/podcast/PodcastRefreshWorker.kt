package com.trulyfreemusic.opengroove.podcast

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trulyfreemusic.opengroove.library.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PodcastRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val library = LibraryRepository(applicationContext)
        val catalog = PodcastFeedCatalog()
        val shows = try {
            library.subscribedPodcasts()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@withContext Result.retry()
        }
        shows.forEach { show ->
            currentCoroutineContext().ensureActive()
            try {
                val feed = catalog.load(show.feedUrl, show)
                library.upsertPodcast(feed.show, feed.episodes, subscribed = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // One unavailable publisher feed should not block the remaining subscriptions.
            }
        }
        Result.success()
    }
}

object PodcastRefreshScheduler {
    private const val WORK_NAME = "open_groove_podcast_feed_refresh"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<PodcastRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
