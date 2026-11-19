package com.yoreader.app.ui.articles

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yoreader.app.YoReaderApp
import com.yoreader.app.data.ArticleListRow
import com.yoreader.app.fetch.Html
import kotlin.math.abs

/**
 * A list row with the HTML excerpt turned into a short plain-text preview. [leaving]:
 * swiped out of this list, shown in its new state for a moment before it goes.
 */
data class ArticleListItem(val row: ArticleListRow, val preview: String, val leaving: Boolean = false)

internal fun ArticleListRow.toListItem(): ArticleListItem {
    val text = excerptHtml?.let(Html::toPlainText).orEmpty()
    // Some feeds repeat the title as the first words of the summary.
    return ArticleListItem(this, text.removePrefix(title).trim().take(220))
}

enum class SwipeAction { READ, SAVE }

/** A swipe only counts once the card has moved at least this share of its width. */
private const val MIN_SWIPE_FRACTION = 0.2f

/**
 * An article row. Swipe left toggles read, swipe right toggles saved for later; the
 * card must be dragged at least 20% of its width (a short flick doesn't count).
 * The card springs back after a swipe, showing its new state; a row leaving the list
 * fades and can't be swiped again (the snackbar's Undo brings it back).
 */
@Composable
internal fun SwipeableArticleRow(
    item: ArticleListItem,
    onClick: () -> Unit,
    onSwipe: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = item.row
    // The swipe state (and its callback) is created once per row, but the row changes
    // (read, saved), so always call the latest onSwipe: it carries the current row.
    // Otherwise a second swipe would act on the row as it was at the first one.
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    var rowWidth by remember { mutableIntStateOf(0) }
    // The state's own callback needs to read the state's offset, hence this holder.
    val stateHolder = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) return@rememberSwipeToDismissBoxState true
            // A quick flick would otherwise count however short it was: require the card
            // to have actually moved at least MIN_SWIPE_FRACTION of its width.
            val offset = stateHolder[0]?.let { runCatching { it.requireOffset() }.getOrNull() } ?: 0f
            if (rowWidth > 0 && abs(offset) < rowWidth * MIN_SWIPE_FRACTION) {
                return@rememberSwipeToDismissBoxState false
            }
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> currentOnSwipe(SwipeAction.READ)
                SwipeToDismissBoxValue.StartToEnd -> currentOnSwipe(SwipeAction.SAVE)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // spring back
        },
        positionalThreshold = { distance -> distance * MIN_SWIPE_FRACTION },
    )
    stateHolder[0] = state
    val leavingAlpha by animateFloatAsState(if (item.leaving) 0.45f else 1f, label = "leaving")
    SwipeToDismissBox(
        state = state,
        modifier = modifier
            .onSizeChanged { rowWidth = it.width }
            .alpha(leavingAlpha),
        gesturesEnabled = !item.leaving,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBackground(
                    icon = if (row.isStarred) Icons.Default.BookmarkRemove else Icons.Default.Bookmark,
                    label = if (row.isStarred) "Remove from saved" else "Save for later",
                    color = MaterialTheme.colorScheme.primary,
                    alignment = Alignment.CenterStart,
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeBackground(
                    icon = if (row.isRead) Icons.Default.MarkEmailUnread else Icons.Default.Done,
                    label = if (row.isRead) "Mark unread" else "Mark read",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    alignment = Alignment.CenterEnd,
                )
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        ArticleRow(item, onClick)
    }
}

@Composable
private fun SwipeBackground(icon: ImageVector, label: String, color: Color, alignment: Alignment) {
    val onColor = if (color == MaterialTheme.colorScheme.primary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        horizontalArrangement = if (alignment == Alignment.CenterStart) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = onColor)
        Spacer(Modifier.width(8.dp))
        Text(label, color = onColor, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * An article in a list. Its text follows the article text size chosen in Options
 * (on top of Android's own font size), like the reader.
 */
@Composable
internal fun ArticleRow(item: ArticleListItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { (context.applicationContext as YoReaderApp).settings }
    val zoom by settings.textZoom.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * zoom / 100f),
    ) {
        ArticleRowContent(item, onClick)
    }
}

@Composable
private fun ArticleRowContent(item: ArticleListItem, onClick: () -> Unit) {
    val row = item.row
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Opaque, so the swipe background only shows where the row has moved away.
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.feedTitle,
                style = MaterialTheme.typography.labelMedium,
                color = if (row.isRead) muted else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            row.publishedAt?.let {
                Text(
                    " · " + relativeTime(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                )
            }
            if (row.isStarred) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = "Saved",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            row.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (row.isRead) FontWeight.Normal else FontWeight.SemiBold,
            color = if (row.isRead) muted else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.preview.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                item.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "5 min. ago", "3 hours ago", "Yesterday", "Mar 5". */
internal fun relativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(
            time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
}
