package com.yoreader.app.fetch

import androidx.room.withTransaction
import com.yoreader.app.data.AppDatabase
import com.yoreader.app.data.Article
import com.yoreader.app.data.Feed
import com.yoreader.app.data.FeedUrl
import com.yoreader.app.data.SearchText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RefreshSummary(val feeds: Int, val failed: Int, val newArticles: Int)

/** Fetches feeds and stores the results: new articles, feed metadata, errors. */
class FeedRefresher(
    private val db: AppDatabase,
    private val fetcher: FeedFetcher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val feedDao = db.feedDao()
    private val articleDao = db.articleDao()

    suspend fun refreshAll(): RefreshSummary = coroutineScope {
        val feeds = feedDao.getAll()
        val limit = Semaphore(MAX_PARALLEL)
        val results = feeds.map { feed ->
            async { limit.withPermit { refresh(feed) } }
        }.awaitAll()
        RefreshSummary(
            feeds = feeds.size,
            failed = results.count { it == null },
            newArticles = results.sumOf { it ?: 0 },
        )
    }

    /** Returns the number of new articles, or null if the refresh failed. */
    suspend fun refresh(feedId: Long): Int? = feedDao.getById(feedId)?.let { refresh(it) }

    private suspend fun refresh(feed: Feed): Int? {
        return when (val result = fetcher.fetch(feed.url, feed.etag, feed.lastModified)) {
            // Re-read the row: it may have been edited or deleted during the download.
            is FetchResult.NotModified -> {
                feedDao.getById(feed.id)?.let { feedDao.update(it.copy(lastFetchedAt = now(), lastError = null)) }
                0
            }
            is FetchResult.Failure -> {
                feedDao.getById(feed.id)?.let { feedDao.update(it.copy(lastError = result.message)) }
                null
            }
            is FetchResult.Success -> store(feed, result)
        }
    }

    private suspend fun store(feed: Feed, result: FetchResult.Success): Int? = db.withTransaction {
        // The user may have deleted the feed while it was downloading.
        val current = feedDao.getById(feed.id) ?: return@withTransaction null

        // A web page URL that led us to its feed: switch the source to the real feed URL,
        // unless that feed is already in the library as another source.
        var url = current.url
        if (FeedUrl.key(result.url) != FeedUrl.key(current.url)) {
            val other = feedDao.getByUrl(result.url)
            if (other != null && other.id != current.id) {
                feedDao.update(current.copy(lastError = "Same feed as \"${other.title}\""))
                return@withTransaction null
            }
            url = result.url
        }

        val parsed = result.feed
        // Replace the host-name placeholder title with the feed's real title.
        val title = if (current.title == FeedUrl.defaultTitle(current.url) && !parsed.title.isNullOrBlank()) {
            parsed.title
        } else {
            current.title
        }

        val fetchedAt = now()
        val articles = parsed.items.map { item ->
            Article(
                feedId = current.id,
                guid = item.guid,
                title = item.title,
                link = item.link,
                summary = item.summary,
                contentHtml = item.contentHtml,
                language = parsed.language,
                // Undated items sort by when we first saw them.
                publishedAt = item.publishedAt ?: fetchedAt,
                searchText = SearchText.forArticle(item.title, item.summary, item.contentHtml),
            )
        }
        val inserted = if (articles.isEmpty()) 0 else articleDao.insertAll(articles).count { it != -1L }

        feedDao.update(
            current.copy(
                title = title,
                url = url,
                siteUrl = parsed.siteUrl ?: current.siteUrl,
                language = parsed.language ?: current.language,
                lastFetchedAt = fetchedAt,
                etag = result.etag,
                lastModified = result.lastModified,
                lastError = null,
            )
        )
        inserted
    }

    private companion object {
        const val MAX_PARALLEL = 4
    }
}
