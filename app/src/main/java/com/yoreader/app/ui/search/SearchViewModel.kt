package com.yoreader.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yoreader.app.YoReaderApp
import com.yoreader.app.data.Feed
import com.yoreader.app.data.SearchText
import com.yoreader.app.ui.articles.ArticleListItem
import com.yoreader.app.ui.articles.toListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SearchResults(
    val query: String,
    val sources: List<Feed>,
    val saved: List<ArticleListItem>,
    val articles: List<ArticleListItem>,
) {
    val isEmpty get() = sources.isEmpty() && saved.isEmpty() && articles.isEmpty()
}

/**
 * Searches sources (title, URL) and articles (title, summary, text), ignoring case and
 * accents; saved articles are listed separately.
 */
class SearchViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val db = (application as YoReaderApp).database

    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
    }

    /** Null when the query is too short to search. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<SearchResults?> =
        query
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce(250)
            .flatMapLatest { q ->
                if (q.length < MIN_LENGTH) {
                    flowOf(null)
                } else {
                    val folded = SearchText.normalize(q)
                    val pattern = "%" + escapeLike(folded) + "%"
                    combine(db.feedDao().observeAll(), db.articleDao().search(pattern)) { allFeeds, rows ->
                        // Few sources, so they're matched here rather than in SQL.
                        val feeds = allFeeds.filter {
                            folded in SearchText.normalize(it.title) || folded in it.url.lowercase()
                        }
                        val items = rows.map { it.toListItem() }
                        SearchResults(
                            query = q,
                            sources = feeds,
                            saved = items.filter { it.row.isStarred },
                            articles = items.filterNot { it.row.isStarred },
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun escapeLike(s: String) =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val MIN_LENGTH = 2
        private const val KEY_QUERY = "query"
    }
}
