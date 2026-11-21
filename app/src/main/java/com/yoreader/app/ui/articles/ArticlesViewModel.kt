package com.yoreader.app.ui.articles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yoreader.app.YoReaderApp
import com.yoreader.app.data.ArticleListRow
import com.yoreader.app.fetch.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which articles a list shows. Encoded in the navigation route as "<kind>/<id>". */
sealed interface ArticleScope {
    /** Home: newest unread, not-saved articles from every source. */
    data object Latest : ArticleScope
    data object Saved : ArticleScope
    data object All : ArticleScope
    data class Group(val groupId: Long) : ArticleScope
    data class Feed(val feedId: Long) : ArticleScope

    companion object {
        const val LATEST = "latest"
        const val SAVED = "saved"
        const val ALL = "all"
        const val GROUP = "group"
        const val FEED = "feed"

        fun from(kind: String?, id: Long): ArticleScope = when (kind) {
            LATEST -> Latest
            SAVED -> Saved
            GROUP -> Group(id)
            FEED -> Feed(id)
            else -> All
        }
    }
}

/** Describes a swipe that happened, so it can be undone. */
data class SwipeResult(val row: ArticleListRow, val action: SwipeAction, val message: String)

/** The row as it will be after [action]. */
internal fun ArticleListRow.after(action: SwipeAction): ArticleListRow = when (action) {
    SwipeAction.READ -> copy(isRead = !isRead)
    SwipeAction.SAVE -> copy(isStarred = !isStarred)
}

/**
 * Puts the [opened] articles that the query no longer returns back at their place
 * (same order as the queries: newest first, no date last), shown as read. Each row
 * comes with whether it is such a held row.
 */
internal fun withOpened(
    rows: List<ArticleListRow>,
    opened: Map<Long, ArticleListRow>,
): List<Pair<ArticleListRow, Boolean>> {
    val ids = rows.mapTo(HashSet()) { it.id }
    val held = opened.values.filter { it.id !in ids }
    if (held.isEmpty()) return rows.map { it to false }
    val order = compareByDescending<Pair<ArticleListRow, Boolean>, Long?>(nullsFirst()) { it.first.publishedAt }
        .thenByDescending { it.first.id }
    return (rows.map { it to false } + held.map { it.copy(isRead = true) to true }).sortedWith(order)
}

/** Whether [row] (in this state) is listed in [scope]. */
internal fun belongsIn(scope: ArticleScope, unreadOnly: Boolean, row: ArticleListRow): Boolean = when (scope) {
    ArticleScope.Latest -> !row.isRead && !row.isStarred
    ArticleScope.Saved -> row.isStarred
    else -> !unreadOnly || !row.isRead
}

class ArticlesViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as YoReaderApp
    private val db = app.database
    private val articleDao = db.articleDao()

    val scope: ArticleScope = ArticleScope.from(
        savedState.get<String>(ARG_KIND),
        savedState.get<Long>(ARG_ID) ?: 0L,
    )

    /** The All/Unread filter; only offered for all, group and feed lists. */
    val hasUnreadFilter: Boolean = scope !is ArticleScope.Latest && scope !is ArticleScope.Saved

    /**
     * Lists open showing unread articles only, so read ones are out of the way;
     * "All" brings them back for this visit.
     */
    val unreadOnly: StateFlow<Boolean> = savedState.getStateFlow(KEY_UNREAD_ONLY, true)

    /**
     * Swipes that take a row out of this list, by article id. The row stays on screen in
     * its new state for [LEAVE_DELAY_MS], so you can see what happened, and only then is
     * the change saved (and the row goes). Meanwhile it can still be swiped: back into
     * the list (which cancels it), or to another state that also leaves (which starts
     * the wait again).
     */
    private val leaving = MutableStateFlow<Map<Long, Leaving>>(emptyMap())

    /** [original]: the row as the database still has it; [target]: as it will be saved. */
    private class Leaving(val original: ArticleListRow, val target: ArticleListRow, val job: Job)

    /**
     * Unread articles opened from this list, by id, as they were when opened. Opening an
     * article marks it read, which would take it out of this list at once; instead it
     * stays, shown as read, until [LEAVE_DELAY_MS] after you come back to the list.
     */
    private val opened = MutableStateFlow<Map<Long, ArticleListRow>>(emptyMap())

    /** Null until the first query result arrives, to tell loading from empty. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val articles: StateFlow<List<ArticleListItem>?> =
        unreadOnly
            .flatMapLatest { query(it) }
            .combine(opened) { rows, opened -> withOpened(rows, opened) }
            .combine(leaving) { rows, leaving ->
                rows.map { (row, held) ->
                    if (held) return@map row.toListItem().copy(leaving = true)
                    val swiped = leaving[row.id]
                    // Only while the database still has the row as it was when swiped.
                    if (swiped != null && swiped.original.isRead == row.isRead && swiped.original.isStarred == row.isStarred) {
                        row.copy(isRead = swiped.target.isRead, isStarred = swiped.target.isStarred)
                            .toListItem().copy(leaving = true)
                    } else {
                        row.toListItem()
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn<List<ArticleListItem>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun query(unreadOnly: Boolean): Flow<List<ArticleListRow>> = when (scope) {
        ArticleScope.Latest -> articleDao.observeLatest(LATEST_COUNT)
        ArticleScope.Saved -> articleDao.observeSaved()
        ArticleScope.All -> articleDao.observeAllList(unreadOnly)
        is ArticleScope.Group -> articleDao.observeGroupList(scope.groupId, unreadOnly)
        is ArticleScope.Feed -> articleDao.observeFeedList(scope.feedId, unreadOnly)
    }

    fun setUnreadOnly(value: Boolean) {
        savedState[KEY_UNREAD_ONLY] = value
    }

    /**
     * Applies a swipe: READ flips read/unread, SAVE flips saved. The new value comes from
     * [row] as shown, so a repeated call for the same swipe sets the same value again.
     * Rows on their way out (dimmed) can be swiped too.
     */
    fun swipe(row: ArticleListRow, action: SwipeAction): SwipeResult {
        val message = when (action) {
            SwipeAction.READ -> if (row.isRead) "Marked as unread" else "Marked as read"
            SwipeAction.SAVE -> if (row.isStarred) "Removed from saved" else "Saved for later"
        }
        move(row, row.after(action))
        return SwipeResult(row, action, message)
    }

    /** Puts the article back the way it was before [result]'s swipe. */
    fun undo(result: SwipeResult) {
        move(result.row.after(result.action), result.row)
    }

    /** Changes the article from [shown] (as on screen) to [target]. */
    private fun move(shown: ArticleListRow, target: ArticleListRow) {
        val id = shown.id
        if (id in opened.value) {
            // Opened from here: the database already has it read. It stays until its
            // time is up, unless the swipe brings it back into the list for good.
            opened.update { if (belongs(target)) it - id else it + (id to target) }
            app.appScope.launch { save(shown, target) }
            return
        }
        val pending = leaving.value[id]
        pending?.job?.cancel()
        val original = pending?.original ?: shown
        if (belongs(original) && !belongs(target)) {
            // The app's scope, so the change is still saved if this screen closes meanwhile.
            val job = app.appScope.launch {
                delay(LEAVE_DELAY_MS)
                save(original, target)
                // Give the list a moment to drop the row before forgetting the swipe.
                delay(1_000)
                leaving.update { if (it[id]?.target == target) it - id else it }
            }
            leaving.update { it + (id to Leaving(original, target, job)) }
        } else {
            leaving.update { it - id }
            app.appScope.launch { save(original, target) }
        }
    }

    /** Saves the values that differ between [from] (as stored) and [to]. */
    private suspend fun save(from: ArticleListRow, to: ArticleListRow) {
        if (from.isRead != to.isRead) articleDao.setRead(to.id, to.isRead)
        if (from.isStarred != to.isStarred) articleDao.setStarred(to.id, to.isStarred)
    }

    /** Call before opening article [id] from this list. */
    fun onOpen(id: Long) {
        val row = articles.value?.firstOrNull { it.row.id == id }?.row ?: return
        if (id !in opened.value && id !in leaving.value && belongs(row) && !belongs(row.copy(isRead = true))) {
            opened.update { it + (id to row) }
        }
    }

    /** Call when the list is on screen again: articles opened before then leave shortly. */
    fun onShown() {
        val ids = opened.value.keys
        if (ids.isEmpty()) return
        viewModelScope.launch {
            delay(LEAVE_DELAY_MS)
            opened.update { it - ids }
        }
    }

    private fun belongs(row: ArticleListRow) = belongsIn(scope, unreadOnly.value, row)

    fun markAllRead() {
        viewModelScope.launch {
            when (scope) {
                ArticleScope.All -> articleDao.markAllRead()
                is ArticleScope.Group -> articleDao.markGroupRead(scope.groupId)
                is ArticleScope.Feed -> articleDao.markFeedRead(scope.feedId)
                ArticleScope.Latest, ArticleScope.Saved -> Unit
            }
        }
    }

    /** Refreshes only the feeds this list shows. */
    fun refresh() {
        val app = getApplication<Application>()
        when (scope) {
            ArticleScope.Latest, ArticleScope.All, ArticleScope.Saved -> RefreshScheduler.refreshAllNow(app)
            is ArticleScope.Feed -> RefreshScheduler.refreshFeed(app, scope.feedId)
            is ArticleScope.Group -> viewModelScope.launch {
                db.groupDao().observeGroupsWithFeeds().first()
                    .firstOrNull { it.group.id == scope.groupId }
                    ?.feeds?.forEach { RefreshScheduler.refreshFeed(app, it.id) }
            }
        }
    }

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_ID = "id"
        const val LATEST_COUNT = 20
        /** How long a swiped-away row stays visible, in its new state, before it goes. */
        const val LEAVE_DELAY_MS = 2_000L
        private const val KEY_UNREAD_ONLY = "unreadOnly"
    }
}
