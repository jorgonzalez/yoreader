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
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
 * save for later, share (title and link, to any app; or on Nostr), and a menu with mark unread,
 * open the original page (in the app), open in browser and version switching. Links
 * in the text also open in the app. While the article is read aloud, the paragraph
 * being read is highlighted and kept in view (until you scroll; Follow brings it back).
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
    onSetRead: (Boolean) -> Unit,
    /** Opens a link inside the app (where it can be cleaned up and read aloud). */
    onOpenLink: (String) -> Unit,
    /** Whether there's a Nostr key, so Share also offers "Share on Nostr". */
    canShareOnNostr: Boolean = false,
    sharingOnNostr: Boolean = false,
    nostrAlsoNote: Boolean = false,
    onShareOnNostr: (comment: String, alsoNote: Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var shareMenuOpen by remember { mutableStateOf(false) }
    var nostrDialog by remember { mutableStateOf(false) }
    val article = item?.article
    val link = article?.link?.takeIf { it.isNotBlank() }
    val hasCleaned = article?.cleanedHtml != null
    val showingCleaned = hasCleaned && !showFeedVersion
    val speechStatus = speech.status.takeIf { article != null && speech.articleId == article.id }
    // Highlight and follow only when the version on screen is the one being read.
    val follow = rememberFollowState(
        speech,
        readingHere = speechStatus != null && speech.contentKey ==
            (if (showingCleaned) ReaderViewModel.CONTENT_CLEANED else ReaderViewModel.CONTENT_FEED),
        pageKey = article?.id,
    )

    LaunchedEffect(messages) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    if (nostrDialog && article != null) {
        NostrShareDialog(
            title = article.title,
            alsoNote = nostrAlsoNote,
            onShare = { comment, alsoNote ->
                nostrDialog = false
                onShareOnNostr(comment, alsoNote)
            },
            onDismiss = { nostrDialog = false },
        )
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
                    // Up to eight buttons: a little narrower than the usual 48dp, so they
                    // fit next to the back arrow on a ~410dp wide phone.
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides ACTION_SIZE) {

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
                        Box(Modifier.size(ACTION_SIZE), contentAlignment = Alignment.Center) {
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
                        Box {
                            if (sharingOnNostr) {
                                Box(Modifier.size(ACTION_SIZE), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                IconButton(onClick = {
                                    if (canShareOnNostr) shareMenuOpen = true else share(context, article.title, link)
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }
                            }
                            DropdownMenu(expanded = shareMenuOpen, onDismissRequest = { shareMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Share link") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        shareMenuOpen = false
                                        share(context, article.title, link)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share on Nostr…") },
                                    leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null) },
                                    onClick = {
                                        shareMenuOpen = false
                                        nostrDialog = true
                                    },
                                )
                            }
                        }
                    }

                    // Opening marks the article read; this switches it back (and forth).
                    IconButton(onClick = { onSetRead(!article.isRead) }) {
                        if (article.isRead) {
                            Icon(Icons.Default.MarkEmailUnread, contentDescription = "Mark as unread")
                        } else {
                            Icon(Icons.Default.MarkEmailRead, contentDescription = "Mark as read")
                        }
                    }

                    if (link != null) {
                        // The original web page inside the app (links work there too).
                        IconButton(onClick = { onOpenLink(link) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open original page")
                        }
                        IconButton(onClick = { openExternal(context, link) }) {
                            Icon(Icons.Default.Public, contentDescription = "Open in browser")
                        }
                    }

                    // Only the cleaned-up version has more options.
                    if (hasCleaned) Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                val html = remember(item.article, item.feedTitle, showingCleaned) {
                    ArticleHtml.build(item.article, item.feedTitle, cleaned = showingCleaned)
                }
                ArticleWebView(
                    html = html,
                    baseUrl = link ?: item.feedUrl,
                    onLinkClick = onOpenLink,
                    reading = follow.reading,
                    follow = follow.following,
                    onUserScroll = follow.stop,
                    // Same as the clean-up button: clean, or switch between the versions.
                    onDoubleTap = when {
                        cleaning -> null
                        hasCleaned -> { { onSetShowFeedVersion(!showFeedVersion) } }
                        link != null -> onClean
                        else -> null
                    },
                )
                if (follow.offerFollow) {
                    FollowButton(
                        onClick = follow.resume,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

/** Width of each top bar button in the reader. */
private val ACTION_SIZE = 44.dp
