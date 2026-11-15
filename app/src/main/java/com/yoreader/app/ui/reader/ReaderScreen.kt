package com.yoreader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yoreader.app.data.ArticleWithFeed
import com.yoreader.app.tts.ArticleSpeaker
import kotlinx.coroutines.flow.Flow

/**
 * One article. Top bar: read aloud, clean up (fetch the page and keep only the article),
 * save for later, open original (in the app), and a menu with mark unread, open in
 * browser, share and version switching. Links in the text also open in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    item: ArticleWithFeed?,
    speech: ArticleSpeaker.State,
    cleaning: Boolean,
    showFeedVersion: Boolean,
    messages: Flow<String>,
    onBack: () -> Unit,
    onReadAloud: () -> Unit,
    onClean: () -> Unit,
    onSetShowFeedVersion: (Boolean) -> Unit,
    onSetSaved: (Boolean) -> Unit,
    onMarkUnread: () -> Unit,
    /** Opens a link inside the app (where it can be cleaned up and read aloud). */
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    val article = item?.article
    val link = article?.link?.takeIf { it.isNotBlank() }
    val hasCleaned = article?.cleanedHtml != null
    val showingCleaned = hasCleaned && !showFeedVersion
    val speechStatus = speech.status.takeIf { article != null && speech.articleId == article.id }

    LaunchedEffect(messages) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                // The source and date are in the page header; the bar keeps room for actions.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (article == null) return@TopAppBar

                    // Read aloud / pause / resume
                    IconButton(onClick = onReadAloud) {
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

                    // Clean up; once cleaned, switches between the two versions.
                    if (cleaning) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(
                            onClick = { if (hasCleaned) onSetShowFeedVersion(!showFeedVersion) else onClean() },
                            enabled = link != null || hasCleaned,
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = when {
                                    showingCleaned -> "Show the feed's version"
                                    hasCleaned -> "Show the cleaned-up version"
                                    else -> "Clean up"
                                },
                                tint = if (showingCleaned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    IconButton(onClick = { onSetSaved(!article.isStarred) }) {
                        Icon(
                            if (article.isStarred) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (article.isStarred) "Remove from saved" else "Save for later",
                        )
                    }

                    if (link != null) {
                        IconButton(onClick = { onOpenLink(link) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open original")
                        }
                    }

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Mark as unread") },
                                leadingIcon = { Icon(Icons.Default.MarkEmailUnread, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onMarkUnread()
                                },
                            )
                            if (link != null) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        openExternal(context, link)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        share(context, article.title, link)
                                    },
                                )
                            }
                            if (hasCleaned) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (showingCleaned) "Show feed version" else "Show cleaned-up version")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.Subject, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onSetShowFeedVersion(!showFeedVersion)
                                    },
                                )
                                if (link != null) {
                                    DropdownMenuItem(
                                        text = { Text("Clean up again") },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            onClean()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (item != null) {
                ArticleWebView(
                    html = ArticleHtml.build(item.article, item.feedTitle, cleaned = showingCleaned),
                    baseUrl = link ?: item.feedUrl,
                    onLinkClick = onOpenLink,
                )
            }
        }
    }
}
