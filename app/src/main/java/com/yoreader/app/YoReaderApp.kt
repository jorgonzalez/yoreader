package com.yoreader.app

import android.app.Application
import android.util.Xml
import com.yoreader.app.clean.ArticleCleaner
import com.yoreader.app.clean.BrowserCleaner
import com.yoreader.app.data.AppDatabase
import com.yoreader.app.data.ArticleRetention
import com.yoreader.app.fetch.FeedFetcher
import com.yoreader.app.fetch.FeedParser
import com.yoreader.app.fetch.FeedRefresher
import com.yoreader.app.fetch.RefreshScheduler
import com.yoreader.app.nostr.NostrClient
import com.yoreader.app.nostr.NostrKeyStore
import com.yoreader.app.nostr.NostrSource
import com.yoreader.app.settings.AppSettings
import com.yoreader.app.sync.LaptopSync
import com.yoreader.app.tts.ArticleSpeaker
import com.yoreader.app.tts.ReadAloudService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class YoReaderApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    private val httpClient by lazy { FeedFetcher.defaultClient() }

    val refresher: FeedRefresher by lazy {
        FeedRefresher(
            db = database,
            fetcher = FeedFetcher(httpClient, FeedParser { Xml.newPullParser() }),
            nostr = NostrSource(nostr),
        )
    }

    val cleaner: ArticleCleaner by lazy { ArticleCleaner(httpClient) }

    /** The Nostr identity used to share articles (none until created in Options). */
    val nostrKeys: NostrKeyStore by lazy { NostrKeyStore(this) }

    /** Publishes to and reads from Nostr relays. */
    val nostr: NostrClient by lazy { NostrClient(httpClient, settings.nostrRelays.value) }

    /** Clean up through an invisible browser view, for sites that refuse [cleaner]. */
    val browserCleaner: BrowserCleaner by lazy { BrowserCleaner(this) }

    /** Choices from the Options screen. */
    val settings: AppSettings by lazy { AppSettings(this) }

    val retention: ArticleRetention by lazy { ArticleRetention(database) }

    /** Removes old read articles as chosen in Options (also run after background refreshes). */
    suspend fun cleanUpOldArticles(): Int = retention.cleanUp(settings.keepReadDays.value)

    /** The sync file shared with the laptop page (set up in Options). */
    val laptopSync: LaptopSync by lazy { LaptopSync(this, database) }

    /** Syncs with the laptop in the background, if a sync file is set. */
    fun syncLaptopSoon() {
        appScope.launch { laptopSync.sync() }
    }

    /** One reader for the whole app, so listening survives moving between screens. */
    val speaker: ArticleSpeaker by lazy { ArticleSpeaker(this) { settings.ttsLanguages.value } }

    /** For short jobs that must finish even if the screen that started them closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.schedulePeriodic(this)
        // Old read articles go at start-up and whenever the setting changes.
        appScope.launch {
            settings.keepReadDays.collect { cleanUpOldArticles() }
        }
        appScope.launch {
            settings.nostrRelays.collect { nostr.setRelays(it) }
        }
        // While something is being read, a foreground service keeps it going with the
        // screen off and shows the controls (notification, lock screen, headset).
        // Reading only starts from the app's own screens, so the app is in the
        // foreground then, as Android requires for starting such a service.
        appScope.launch(Dispatchers.Main) {
            speaker.state.map { it.isActive }.distinctUntilChanged().collect { active ->
                if (active) ReadAloudService.start(this@YoReaderApp)
            }
        }
    }
}
