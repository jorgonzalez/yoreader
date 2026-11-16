package com.yoreader.app

import android.app.Application
import android.util.Xml
import com.yoreader.app.clean.ArticleCleaner
import com.yoreader.app.data.AppDatabase
import com.yoreader.app.fetch.FeedFetcher
import com.yoreader.app.fetch.FeedParser
import com.yoreader.app.fetch.FeedRefresher
import com.yoreader.app.fetch.RefreshScheduler
import com.yoreader.app.tts.ArticleSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class YoReaderApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    private val httpClient by lazy { FeedFetcher.defaultClient() }

    val refresher: FeedRefresher by lazy {
        FeedRefresher(
            db = database,
            fetcher = FeedFetcher(httpClient, FeedParser { Xml.newPullParser() }),
        )
    }

    val cleaner: ArticleCleaner by lazy { ArticleCleaner(httpClient) }

    /** One reader for the whole app, so listening survives moving between screens. */
    val speaker: ArticleSpeaker by lazy { ArticleSpeaker(this) }

    /** For short jobs that must finish even if the screen that started them closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.schedulePeriodic(this)
    }
}
