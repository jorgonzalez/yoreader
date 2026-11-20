package com.yoreader.app.clean

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/** A page as a WebView shows it: its address and current HTML. */
data class LoadedPage(val url: String, val html: String)

/** Reads the page currently shown in [view], or null if there's none. Main thread. */
suspend fun loadedPage(view: WebView): LoadedPage? {
    val url = view.url?.takeIf { it.startsWith("http") } ?: return null
    val json = suspendCancellableCoroutine { cont ->
        view.evaluateJavascript("document.documentElement.outerHTML") { cont.resume(it) }
    }
    // The result is a JSON value: a quoted string, or null.
    val html = runCatching { JSONArray("[$json]").optString(0, "") }.getOrNull()
    return html?.takeIf { it.isNotBlank() && it != "null" }?.let { LoadedPage(url, it) }
}

/**
 * Cleans a page by loading it in an invisible WebView, as a browser would. For sites
 * that refuse [ArticleCleaner]'s own download (HTTP 403, Cloudflare's browser check)
 * but let browsers in. Slower (a few seconds), so only a fallback.
 */
class BrowserCleaner(private val context: Context) {

    /** The cleaned article, or null if the page didn't give one in time. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun clean(url: String): CleanResult.Success? = withContext(Dispatchers.Main) {
        val view = WebView(context)
        try {
            view.settings.javaScriptEnabled = true // checks like Cloudflare's need it
            view.settings.domStorageEnabled = true
            view.settings.allowFileAccess = false
            view.settings.allowContentAccess = false
            view.settings.blockNetworkImage = true // only the text is needed
            val finished = Channel<Unit>(Channel.CONFLATED)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    request.url.scheme != "http" && request.url.scheme != "https"

                override fun onPageFinished(view: WebView, url: String?) {
                    finished.trySend(Unit)
                }
            }
            view.loadUrl(url)
            var result: CleanResult? = null
            withTimeoutOrNull(TIMEOUT_MS) {
                // Pages full of ads may never quite "finish": don't wait forever for that.
                withTimeoutOrNull(FIRST_LOOK_MS) { finished.receive() }
                // A browser check replaces itself with the article after a moment: keep looking.
                while (result !is CleanResult.Success) {
                    loadedPage(view)?.let { page ->
                        result = withContext(Dispatchers.Default) { ArticleCleaner.extract(page.html, page.url) }
                    }
                    if (result !is CleanResult.Success) delay(RETRY_MS)
                }
            }
            result as? CleanResult.Success
        } finally {
            view.stopLoading()
            view.destroy()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val FIRST_LOOK_MS = 8_000L
        const val RETRY_MS = 1_500L
    }
}
