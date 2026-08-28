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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PodcastRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val library = LibraryRepository(applicationContext)
        val catalog = PodcastFeedCatalog()
        val shows = runCatching { library.subscribedPodcasts() }.getOrElse { return@withContext Result.retry() }
        shows.forEach { show ->
            runCatching {
                val feed = catalog.load(show.feedUrl, show)
                library.upsertPodcast(feed.show, feed.episodes, subscribed = true)
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
