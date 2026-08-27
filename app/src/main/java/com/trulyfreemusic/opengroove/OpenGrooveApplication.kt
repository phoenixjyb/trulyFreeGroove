package com.trulyfreemusic.opengroove

import android.app.Application
import com.trulyfreemusic.opengroove.podcast.PodcastRefreshScheduler

class OpenGrooveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PodcastRefreshScheduler.schedule(this)
    }
}
