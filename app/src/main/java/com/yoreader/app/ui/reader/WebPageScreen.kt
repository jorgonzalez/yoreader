package com.yoreader.app.ui.reader

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yoreader.app.tts.ArticleSpeaker
import kotlinx.coroutines.flow.Flow

/**
 * A link opened inside the app. Shows the live page; Clean up turns it into a readable
 * dark page, and Read aloud reads that (cleaning first if needed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPageScreen(
    url: String,
    pageTitle: String?,
    cleaned: CleanedPage?,
    showCleaned: Boolean,
    cleaning: Boolean,
    speech: ArticleSpeaker.State,
    messages: Flow<String>,
    onBack: () -> Unit,
    onPageChanged: (url: String, title: String?) -> Unit,
    onClean: () -> Unit,
    onSetShowCleaned: (Boolean) -> Unit,
    onReadAloud: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val host = Uri.parse(url).host?.removePrefix("www.") ?: url
    val showingCleaned = showCleaned && cleaned != null
    val speechStatus = speech.status.takeIf { cleaned != null && speech.pageUrl == cleaned.url }
    // Only the cleaned view is what's read aloud, so only it highlights and follows.
    val follow = rememberFollowState(speech, readingHere = speechStatus != null && showingCleaned, pageKey = cleaned?.url)

    LaunchedEffect(messages) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            (if (showingCleaned) cleaned?.title else pageTitle) ?: host,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onReadAloud, enabled = !cleaning) {
                        val accent = MaterialTheme.colorScheme.primary
                        when (speechStatus) {
                            ArticleSpeaker.Status.SPEAKING, ArticleSpeaker.Status.PREPARING ->
                                Icon(Icons.Default.Pause, contentDescription = "Pause reading", tint = accent)
                            ArticleSpeaker.Status.PAUSED ->
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume reading", tint = accent)
                            else ->
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = "Read aloud")
                        }
                    }
                    if (cleaning) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { if (cleaned != null) onSetShowCleaned(!showCleaned) else onClean() }) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = when {
                                    showingCleaned -> "Show the original page"
                                    cleaned != null -> "Show the cleaned-up page"
                                    else -> "Clean up"
                                },
                                tint = if (showingCleaned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = { share(context, cleaned?.title ?: pageTitle ?: host, url) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { openExternal(context, url) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (showingCleaned && cleaned != null) {
                val html = remember(cleaned) {
                    ArticleHtml.webPage(cleaned.title, cleaned.host, cleaned.html, cleaned.language)
                }
                ArticleWebView(
                    html = html,
                    baseUrl = cleaned.url,
                    onLinkClick = onOpenLink,
                    reading = follow.reading,
                    follow = follow.following,
                    onUserScroll = follow.stop,
                )
                if (follow.offerFollow) {
                    FollowButton(
                        onClick = follow.resume,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                }
            } else {
                // Recreated when coming back from the cleaned view, at the current page.
                key(showingCleaned) {
                    LiveWebView(url = url, onPageChanged = onPageChanged)
                }
            }
        }
    }
}
