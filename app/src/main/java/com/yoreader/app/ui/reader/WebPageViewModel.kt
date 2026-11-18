package com.yoreader.app.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yoreader.app.YoReaderApp
import com.yoreader.app.clean.CleanResult
import com.yoreader.app.tts.ArticleSpeaker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** A cleaned web page, kept in memory while its screen is open. */
data class CleanedPage(
    val url: String,
    val title: String,
    val html: String,
    val text: String,
    val language: String?,
) {
    val host: String get() = Uri.parse(url).host?.removePrefix("www.") ?: url
}

/** A link opened inside the app: the live page, plus clean up and read aloud. */
class WebPageViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as YoReaderApp
    private val speaker = app.speaker

    /** The page being shown; follows links tapped inside the live page. */
    val url = MutableStateFlow(savedState.get<String>(ARG_URL).orEmpty())
    val pageTitle = MutableStateFlow<String?>(null)

    val cleaned = MutableStateFlow<CleanedPage?>(null)
    /** Cleaned view vs. the live page; only meaningful once [cleaned] is set. */
    val showCleaned = MutableStateFlow(false)
    val cleaning = MutableStateFlow(false)

    val speech: StateFlow<ArticleSpeaker.State> = speaker.state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun onPageChanged(newUrl: String, title: String?) {
        if (newUrl != url.value) {
            url.value = newUrl
            // A different page: the cleaned version belongs to the previous one.
            if (cleaned.value?.url != newUrl) cleaned.value = null
        }
        if (!title.isNullOrBlank()) pageTitle.value = title
    }

    fun setShowCleaned(value: Boolean) {
        showCleaned.value = value && cleaned.value != null
    }

    /** Cleans the current page; with [thenRead], starts reading it when done. */
    fun clean(thenRead: Boolean = false) {
        if (cleaning.value) return
        val target = url.value
        viewModelScope.launch {
            cleaning.value = true
            when (val result = app.cleaner.clean(target)) {
                is CleanResult.Success -> {
                    val title = result.title ?: pageTitle.value ?: Uri.parse(target).host ?: target
                    val page = CleanedPage(
                        url = target,
                        title = title,
                        html = result.html,
                        text = result.text,
                        language = speaker.guessLanguage(title + "\n" + result.text),
                    )
                    cleaned.value = page
                    showCleaned.value = true
                    if (thenRead) play(page)
                }
                is CleanResult.Failure -> _messages.send(result.message)
            }
            cleaning.value = false
        }
    }

    /** Pause/resume this page, or start reading it (cleaning it first if needed). */
    fun toggleReadAloud() {
        val page = cleaned.value
        val s = speaker.state.value
        if (page != null && s.pageUrl == page.url) {
            when (s.status) {
                ArticleSpeaker.Status.SPEAKING, ArticleSpeaker.Status.PREPARING -> return speaker.pause()
                ArticleSpeaker.Status.PAUSED -> return speaker.resume()
                ArticleSpeaker.Status.IDLE -> Unit
            }
        }
        if (page == null) clean(thenRead = true) else {
            showCleaned.value = true
            play(page)
        }
    }

    private fun play(page: CleanedPage) {
        speaker.play(
            articleId = idFor(page.url),
            title = page.title,
            // Exactly the paragraphs on screen, so the page can highlight and follow them.
            paragraphs = ArticleHtml.readable(page.html, page.title).paragraphs,
            language = page.language,
            pageUrl = page.url,
            sourceKey = "site:${page.host}",
        )
    }

    companion object {
        const val ARG_URL = "url"

        /** Negative ids never clash with stored articles. */
        fun idFor(url: String): Long = -1L - (url.hashCode().toLong() and 0x7fffffffL)
    }
}
