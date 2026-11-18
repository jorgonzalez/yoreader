package com.yoreader.app.ui.reader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yoreader.app.YoReaderApp
import com.yoreader.app.ui.theme.Background
import java.util.Locale
import kotlin.math.abs

/** Where reading aloud is in the page shown: see [ArticleHtml.READ_FUNCTION]. */
internal data class ReadingSpot(val paragraph: Int, val progress: Float)

/**
 * Our own dark-styled HTML (feed content or a cleaned page). Tapping a link calls
 * [onLinkClick] instead of navigating inside the view.
 *
 * While the article is read aloud, [reading] says where: that paragraph is
 * highlighted and, with [follow], kept in view. Dragging the page calls [onUserScroll]
 * (so the screen can stop following). JavaScript is on only for that: [ArticleHtml]
 * strips the article's own scripts.
 *
 * Text is shown at the size chosen in Options; changing it doesn't reload the page or
 * lose the place (see [ArticleHtml.ZOOM_TOKEN]).
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun ArticleWebView(
    html: String,
    baseUrl: String,
    onLinkClick: (String) -> Unit,
    reading: ReadingSpot? = null,
    follow: Boolean = true,
    onUserScroll: () -> Unit = {},
) {
    val linkHandler by rememberUpdatedState(onLinkClick)
    val userScroll by rememberUpdatedState(onUserScroll)
    // Survives recompositions: whether the page has loaded, and the last call made.
    val js = remember { ReadingScript() }
    val context = LocalContext.current
    val appSettings = remember(context) { (context.applicationContext as YoReaderApp).settings }
    val textZoom by appSettings.textZoom.collectAsStateWithLifecycle()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Background.toArgb())
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        linkHandler(request.url.toString())
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // The size may have changed while the page was loading.
                        view.evaluateJavascript(ArticleHtml.zoomScript(js.zoom), null)
                        js.loaded = true
                        js.applied = null
                        js.apply(view)
                    }
                }
                val slop = ViewConfiguration.get(ctx).scaledTouchSlop
                var downY = 0f
                var dragging = false
                setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downY = e.y
                            dragging = false
                        }
                        MotionEvent.ACTION_MOVE -> if (!dragging && abs(e.y - downY) > slop) {
                            dragging = true
                            userScroll()
                        }
                    }
                    false // the WebView still handles the touch
                }
            }
        },
        update = { view ->
            if (js.zoom != textZoom) {
                js.zoom = textZoom
                if (js.loaded) view.evaluateJavascript(ArticleHtml.zoomScript(textZoom), null)
            }
            // Reload only when the content really changed (e.g. not when saving),
            // so the scroll position is kept.
            if (view.tag != html) {
                view.tag = html
                js.loaded = false
                val page = html.replace(ArticleHtml.ZOOM_TOKEN, ArticleHtml.zoomValue(textZoom))
                view.loadDataWithBaseURL(baseUrl, page, "text/html", "utf-8", null)
            }
            js.wanted = reading?.let {
                "${ArticleHtml.READ_FUNCTION}(${it.paragraph}, ${"%.3f".format(Locale.ROOT, it.progress)}, $follow)"
            } ?: "${ArticleHtml.READ_FUNCTION}(-1, 0, false)"
            js.apply(view)
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The latest reading-position call for the page, sent once the page has loaded. */
private class ReadingScript {
    var loaded = false

    /** Text size (percent) the page shows. */
    var zoom = 100
    var wanted: String? = null
    var applied: String? = null

    fun apply(view: WebView) {
        val call = wanted ?: return
        if (!loaded || call == applied) return
        applied = call
        view.evaluateJavascript(call, null)
    }
}

/**
 * A real web page, like a small browser: JavaScript on, links followed inside the view,
 * system Back goes to the previous page. [onPageChanged] reports the current URL and title.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LiveWebView(url: String, onPageChanged: (url: String, title: String?) -> Unit) {
    val pageChanged by rememberUpdatedState(onPageChanged)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme
                            if (scheme == "http" || scheme == "https") return false // stay in the view
                            openExternal(view.context, request.url.toString()) // mailto:, tel:, apps
                            return true
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                            url?.let { pageChanged(it, view.title) }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            url?.let { pageChanged(it, view.title) }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onReceivedTitle(view: WebView, title: String?) {
                            view.url?.let { pageChanged(it, title) }
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = {
                webView = null
                it.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

internal fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser or app can handle it; nothing useful to do.
    }
}

internal fun share(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        // Chat apps ignore the subject, so the title goes in the text too.
        putExtra(Intent.EXTRA_TEXT, if (title.isBlank()) url else "$title\n$url")
    }
    context.startActivity(Intent.createChooser(send, null))
}
