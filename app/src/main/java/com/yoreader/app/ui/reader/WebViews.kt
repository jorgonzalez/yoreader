package com.yoreader.app.ui.reader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.viewinterop.AndroidView
import com.yoreader.app.ui.theme.Background

/**
 * Our own dark-styled HTML (feed content or a cleaned page), JavaScript off.
 * Tapping a link calls [onLinkClick] instead of navigating inside the view.
 */
@Composable
internal fun ArticleWebView(html: String, baseUrl: String, onLinkClick: (String) -> Unit) {
    val linkHandler by rememberUpdatedState(onLinkClick)
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Background.toArgb())
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        linkHandler(request.url.toString())
                        return true
                    }
                }
            }
        },
        // Reload only when the content really changed (e.g. not when saving),
        // so the scroll position is kept.
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
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
