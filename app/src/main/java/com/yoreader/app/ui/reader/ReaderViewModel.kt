package com.yoreader.app.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yoreader.app.YoReaderApp
import com.yoreader.app.clean.CleanResult
import com.yoreader.app.data.ArticleWithFeed
import com.yoreader.app.data.SearchText
import com.yoreader.app.fetch.Html
import com.yoreader.app.nostr.NostrRelay
import com.yoreader.app.nostr.NostrShare
import com.yoreader.app.tts.ArticleSpeaker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as YoReaderApp
    private val articleDao = app.database.articleDao()
    private val articleId: Long = savedState.get<Long>(ARG_ARTICLE_ID) ?: 0L

    /** Null while loading, or if the article was deleted (e.g. its source was removed). */
    val article: StateFlow<ArticleWithFeed?> =
        articleDao.observeWithFeed(articleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True while the web page is being downloaded and cleaned. */
    val cleaning = MutableStateFlow(false)

    /** When a cleaned version exists, it's shown unless the user switched back to the feed's. */
    val showFeedVersion: StateFlow<Boolean> = savedState.getStateFlow(KEY_SHOW_FEED, false)

    val speech: StateFlow<ArticleSpeaker.State> = app.speaker.state

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for a snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        // Opening an article marks it read. Done once, so "mark unread" sticks.
        viewModelScope.launch { articleDao.setRead(articleId, true) }
    }

    fun setRead(read: Boolean) {
        viewModelScope.launch { articleDao.setRead(articleId, read) }
    }

    fun setStarred(starred: Boolean) {
        viewModelScope.launch { articleDao.setStarred(articleId, starred) }
    }

    /** Whether a Nostr key exists, so the article can be shared there. */
    val canShareOnNostr: StateFlow<Boolean> = app.nostrKeys.key
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.nostrKeys.key.value != null)

    val nostrAlsoNote: StateFlow<Boolean> = app.settings.nostrAlsoNote

    /** True while the article is being published to the relays. */
    val sharingOnNostr = MutableStateFlow(false)

    /**
     * Publishes the article as a web bookmark (and, if [alsoNote], a plain note) to the
     * relays, with [comment], and says how it went. Runs in the app's scope, so leaving
     * the article doesn't cancel it.
     */
    fun shareOnNostr(comment: String, alsoNote: Boolean) {
        val key = app.nostrKeys.key.value ?: return
        val a = article.value?.article ?: return
        val link = a.link.takeIf { it.isNotBlank() } ?: return
        app.settings.setNostrAlsoNote(alsoNote)
        sharingOnNostr.value = true
        app.appScope.launch {
            try {
                val results = app.nostr.publish(NostrShare.bookmark(key, link, a.title, comment))
                if (alsoNote) app.nostr.publish(NostrShare.note(key, link, a.title, comment))
                // Once your relays changed (or the first time), tell followers where you write.
                val relays = app.settings.nostrRelays.value
                val listed = relays.joinToString("\n")
                if (app.settings.nostrPublishedRelays != listed) {
                    val sent = app.nostr.publish(NostrShare.relayList(key, relays))
                    if (sent.any { it.second == NostrRelay.PublishResult.Accepted }) app.settings.nostrPublishedRelays = listed
                }
                _messages.send(NostrShare.summary(results))
            } finally {
                sharingOnNostr.value = false
            }
        }
    }

    fun setShowFeedVersion(value: Boolean) {
        savedState[KEY_SHOW_FEED] = value
    }

    /**
     * Downloads the article's page and keeps just the article text. If the site refuses
     * the download, loads the page in an invisible browser view instead.
     */
    fun clean() {
        val a = article.value?.article ?: return
        if (cleaning.value) return
        viewModelScope.launch {
            cleaning.value = true
            var result = app.cleaner.clean(a.link)
            if (result is CleanResult.Failure && result.blocked) {
                result = app.browserCleaner.clean(a.link) ?: result
            }
            when (result) {
                is CleanResult.Success -> {
                    articleDao.setCleaned(
                        id = a.id,
                        html = result.html,
                        text = result.text,
                        searchText = SearchText.forArticle(a.title, a.summary, result.html),
                    )
                    setShowFeedVersion(false)
                    _messages.send("Cleaned up: showing just the article")
                }
                is CleanResult.Failure -> _messages.send(result.message)
            }
            cleaning.value = false
        }
    }

    /** Play, pause or resume this article. Reads the version on screen. */
    fun toggleReadAloud() {
        val item = article.value ?: return
        val a = item.article
        val speaker = app.speaker
        val s = speaker.state.value
        if (s.articleId == a.id) {
            when (s.status) {
                ArticleSpeaker.Status.SPEAKING, ArticleSpeaker.Status.PREPARING -> {
                    speaker.pause()
                    return
                }
                ArticleSpeaker.Status.PAUSED -> {
                    speaker.resume()
                    return
                }
                ArticleSpeaker.Status.IDLE -> Unit
            }
        }

        // Exactly the paragraphs on screen, so the reader can highlight and follow them.
        val useCleaned = a.cleanedHtml != null && !showFeedVersion.value
        val paragraphs = ArticleHtml.readable(a, cleaned = useCleaned)?.paragraphs.orEmpty()
        if (!useCleaned && paragraphs.sumOf { it.length } < SHORT_TEXT) {
            viewModelScope.launch {
                _messages.send("The feed only has a short summary. Tap Clean up to get the full article.")
            }
        }
        val language = app.speaker.guessLanguage(a.title + "\n" + paragraphs.joinToString("\n"))
            ?: a.language
        speaker.play(
            a.id, a.title, paragraphs, language,
            sourceKey = "feed:${a.feedId}",
            contentKey = if (useCleaned) CONTENT_CLEANED else CONTENT_FEED,
        )
    }

    companion object {
        /** [ArticleSpeaker.State.contentKey] values: which version is being read. */
        const val CONTENT_CLEANED = "cleaned"
        const val CONTENT_FEED = "feed"

        const val ARG_ARTICLE_ID = "articleId"
        private const val KEY_SHOW_FEED = "showFeedVersion"
        private const val SHORT_TEXT = 400
    }
}
