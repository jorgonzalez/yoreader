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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

class ArticlesViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val db = (application as YoReaderApp).database
    private val articleDao = db.articleDao()

    val scope: ArticleScope = ArticleScope.from(
        savedState.get<String>(ARG_KIND),
        savedState.get<Long>(ARG_ID) ?: 0L,
    )

    /** The All/Unread filter; only offered for all, group and feed lists. */
    val hasUnreadFilter: Boolean = scope !is ArticleScope.Latest && scope !is ArticleScope.Saved

    val unreadOnly: StateFlow<Boolean> = savedState.getStateFlow(KEY_UNREAD_ONLY, false)

    /** Null until the first query result arrives, to tell loading from empty. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val articles: StateFlow<List<ArticleListItem>?> =
        unreadOnly
            .flatMapLatest { query(it) }
            .map { rows -> rows.map { it.toListItem() } }
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
     */
    fun swipe(row: ArticleListRow, action: SwipeAction): SwipeResult {
        viewModelScope.launch {
            when (action) {
                SwipeAction.READ -> articleDao.setRead(row.id, !row.isRead)
                SwipeAction.SAVE -> articleDao.setStarred(row.id, !row.isStarred)
            }
        }
        val message = when (action) {
            SwipeAction.READ -> if (row.isRead) "Marked as unread" else "Marked as read"
            SwipeAction.SAVE -> if (row.isStarred) "Removed from saved" else "Saved for later"
        }
        return SwipeResult(row, action, message)
    }

    /** Puts the article back the way it was before [result]'s swipe. */
    fun undo(result: SwipeResult) {
        viewModelScope.launch {
            when (result.action) {
                SwipeAction.READ -> articleDao.setRead(result.row.id, result.row.isRead)
                SwipeAction.SAVE -> articleDao.setStarred(result.row.id, result.row.isStarred)
            }
        }
    }

    /** Whether the row no longer belongs in this list after the swipe. */
    fun swipeRemovesRow(row: ArticleListRow, action: SwipeAction): Boolean = when (scope) {
        ArticleScope.Latest -> true // it becomes read or saved: both leave Latest
        ArticleScope.Saved -> action == SwipeAction.SAVE
        else -> action == SwipeAction.READ && unreadOnly.value && !row.isRead
    }

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
        private const val KEY_UNREAD_ONLY = "unreadOnly"
    }
}
