package com.yoreader.app.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yoreader.app.data.ArticleListRow
import com.yoreader.app.ui.components.ConfirmDialog
import com.yoreader.app.ui.components.EmptyState
import com.yoreader.app.ui.components.RefreshAction
import kotlinx.coroutines.launch

/**
 * A list of articles: Latest (home), Saved, all, one group, or one feed.
 * Swipe left to mark read, swipe right to save for later; both can be undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
    title: String,
    articles: List<ArticleListItem>?,
    emptyText: String,
    refreshing: Boolean,
    onOpenArticle: (Long) -> Unit,
    onSwipe: (ArticleListRow, SwipeAction) -> SwipeResult,
    onUndo: (SwipeResult) -> Unit,
    /** Null on top-level screens (Latest, Saved), which have no back arrow. */
    onBack: (() -> Unit)? = null,
    /** Null hides pull-to-refresh and the refresh button. */
    onRefresh: (() -> Unit)? = null,
    /** Non-null shows the All / Unread filter. */
    unreadOnly: Boolean? = null,
    onSetUnreadOnly: (Boolean) -> Unit = {},
    onMarkAllRead: (() -> Unit)? = null,
    /** Only for groups: opens the screen to rename the group and manage its sources. */
    onEditGroup: (() -> Unit)? = null,
    /** Only for groups: shows an "Add source" button that calls this. */
    onAddSource: (() -> Unit)? = null,
) {
    var confirmMarkAll by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Swipe callbacks can fire more than once per gesture; report each swipe once.
    // The whole row is the key, so a later swipe on the changed row still counts.
    var lastSwipe by remember { mutableStateOf<Pair<ArticleListRow, SwipeAction>?>(null) }
    val listState = rememberLazyListState()
    // After an undo, the article returns above the rows on screen (the list keeps the
    // current top row in place), so scroll up to it once it's back.
    var restoredId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(restoredId, articles) {
        val id = restoredId ?: return@LaunchedEffect
        val index = articles?.indexOfFirst { it.row.id == id } ?: -1
        if (index >= 0) {
            if (index <= listState.firstVisibleItemIndex) listState.animateScrollToItem(index)
            restoredId = null
        }
    }

    fun handleSwipe(row: ArticleListRow, action: SwipeAction) {
        if (lastSwipe == row to action) return
        lastSwipe = row to action
        val result = onSwipe(row, action)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val choice = snackbar.showSnackbar(result.message, "Undo", duration = SnackbarDuration.Long)
            if (choice == SnackbarResult.ActionPerformed) {
                onUndo(result)
                restoredId = row.id
            }
            if (lastSwipe == row to action) lastSwipe = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (onMarkAllRead != null && articles.orEmpty().any { !it.row.isRead }) {
                        IconButton(onClick = { confirmMarkAll = true }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Mark all as read")
                        }
                    }
                    if (onEditGroup != null) {
                        IconButton(onClick = onEditGroup) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit group")
                        }
                    }
                    if (onRefresh != null && onBack == null) {
                        RefreshAction(refreshing, onRefresh)
                    }
                },
            )
        },
        floatingActionButton = {
            if (onAddSource != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddSource,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add source") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (unreadOnly != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = unreadOnly,
                        onClick = { onSetUnreadOnly(true) },
                        label = { Text("Unread") },
                    )
                    FilterChip(
                        selected = !unreadOnly,
                        onClick = { onSetUnreadOnly(false) },
                        label = { Text("All") },
                    )
                    articles?.let { CountBadge(it.size, unreadOnly) }
                }
            }

            val list: @Composable () -> Unit = {
                when {
                    articles == null -> Unit
                    // Pull-to-refresh only sees pulls on a scrollable child, so the
                    // empty message sits inside a list too.
                    articles.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            EmptyState(text = emptyText, modifier = Modifier.fillParentMaxSize())
                        }
                    }
                    else -> LazyColumn(
                        state = listState,
                        // Room for the "Add source" button over the last row.
                        contentPadding = PaddingValues(bottom = if (onAddSource != null) 88.dp else 8.dp),
                    ) {
                        items(articles, key = { it.row.id }) { item ->
                            // animateItem: rows that leave fade and the gap closes smoothly.
                            Column(Modifier.animateItem()) {
                                SwipeableArticleRow(
                                    item = item,
                                    onClick = { onOpenArticle(item.row.id) },
                                    onSwipe = { action -> handleSwipe(item.row, action) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            if (onRefresh != null) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { list() }
            } else {
                list()
            }
        }
    }

    if (confirmMarkAll && onMarkAllRead != null) {
        ConfirmDialog(
            title = "Mark all as read?",
            message = "Every article in \"$title\" will be marked as read.",
            confirmLabel = "Mark read",
            onDismiss = { confirmMarkAll = false },
            onConfirm = {
                onMarkAllRead()
                confirmMarkAll = false
            },
        )
    }
}

/**
 * How many articles the list shows (unread ones, or all), in a circle that widens into
 * a pill for long numbers.
 */
@Composable
private fun CountBadge(count: Int, unreadOnly: Boolean) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 7.dp)
            .semantics {
                contentDescription = if (unreadOnly) "$count unread articles" else "$count articles"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
