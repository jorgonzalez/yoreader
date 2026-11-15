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
import com.yoreader.app.tts.ArticleSpeaker
import com.yoreader.app.tts.SpeechText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setShowFeedVersion(value: Boolean) {
        savedState[KEY_SHOW_FEED] = value
    }

    /** Downloads the article's page and keeps just the article text. */
    fun clean() {
        val a = article.value?.article ?: return
        if (cleaning.value) return
        viewModelScope.launch {
            cleaning.value = true
            when (val result = app.cleaner.clean(a.link)) {
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

        val useCleaned = a.cleanedText != null && !showFeedVersion.value
        val paragraphs = if (useCleaned) {
            a.cleanedText!!.split("\n\n").filter { it.isNotBlank() }
        } else {
            Html.toParagraphs(a.contentHtml ?: a.summary.orEmpty())
        }
        if (!useCleaned && paragraphs.sumOf { it.length } < SHORT_TEXT) {
            viewModelScope.launch {
                _messages.send("The feed only has a short summary. Tap Clean up to get the full article.")
            }
        }
        val language = SpeechText.guessLanguage(a.title + "\n" + paragraphs.joinToString("\n"))
            ?: a.language
        speaker.play(a.id, a.title, paragraphs, language)
    }

    companion object {
        const val ARG_ARTICLE_ID = "articleId"
        private const val KEY_SHOW_FEED = "showFeedVersion"
        private const val SHORT_TEXT = 400
    }
}
