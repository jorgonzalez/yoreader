package com.yoreader.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A user-managed group of sources, e.g. "News", "Tech", "Spanish". */
@Entity(
    tableName = "feed_groups",
    indices = [Index(value = ["name"], unique = true)],
)
data class FeedGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Manual ordering in the group list. */
    val position: Int = 0,
)

/** A single RSS/Atom source. */
@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)],
)
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val siteUrl: String? = null,
    /** "es" or "en" when known; used as a TTS hint before per-article detection. */
    val language: String? = null,
    /** Last successful fetch (including "not modified"). */
    val lastFetchedAt: Long? = null,
    /** HTTP validators from the last response, sent back so unchanged feeds cost ~nothing. */
    val etag: String? = null,
    val lastModified: String? = null,
    /** Human-readable reason the last refresh failed; null when it succeeded. */
    val lastError: String? = null,
)

/** Many-to-many link: a feed can live in several groups. */
@Entity(
    tableName = "feed_group_cross_ref",
    primaryKeys = ["feedId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FeedGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class FeedGroupCrossRef(
    val feedId: Long,
    val groupId: Long,
)

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("feedId"),
        Index(value = ["feedId", "guid"], unique = true),
    ],
)
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    /** Feed-provided unique id (falls back to the link) so refreshes don't duplicate. */
    val guid: String,
    val title: String,
    val link: String,
    val summary: String? = null,
    val contentHtml: String? = null,
    /** Plain text from the "clean up" button (paragraphs separated by blank lines). */
    val cleanedText: String? = null,
    /** The same cleaned article as HTML (keeps images and links), for the reader. */
    val cleanedHtml: String? = null,
    /** Detected per article: "es" or "en". */
    val language: String? = null,
    val publishedAt: Long? = null,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    /** When [isRead] last changed (ms), here or on the laptop; null: never since sync existed. */
    val readChangedAt: Long? = null,
    /** When [isStarred] (saved) last changed (ms); null: never since sync existed. */
    val starredChangedAt: Long? = null,
    /** Title + summary + content, folded by [SearchText] for accent-insensitive search. */
    val searchText: String? = null,
)

/**
 * A read article removed by the automatic clean-up (see [ArticleRetention]). Kept so a
 * refresh doesn't add it back as new while its feed still lists it; forgotten once the
 * feed no longer does. Rows of deleted feeds are dropped by the next clean-up.
 */
@Entity(tableName = "removed_articles", primaryKeys = ["feedId", "guid"])
data class RemovedArticle(
    val feedId: Long,
    val guid: String,
)

/** Article totals for one feed, for the sources list. */
data class FeedCounts(
    val feedId: Long,
    val total: Int,
    val unread: Int,
)

/** Unread articles in one group (a feed in several groups counts in each). */
data class GroupUnread(
    val groupId: Long,
    val unread: Int,
)

/** One row of an article list: just what the list shows, not the full content. */
data class ArticleListRow(
    val id: Long,
    val feedId: Long,
    val feedTitle: String,
    val title: String,
    /** First part of the summary, or of the content when there is no summary (raw HTML). */
    val excerptHtml: String?,
    val publishedAt: Long?,
    val isRead: Boolean,
    val isStarred: Boolean,
)

/** An article as written to the laptop sync file. */
data class SyncArticleRow(
    val feedUrl: String,
    val guid: String,
    val title: String,
    val link: String,
    val publishedAt: Long?,
    /** Text to read on the laptop; only for unread and saved articles. */
    val html: String?,
    val excerptHtml: String?,
    val isRead: Boolean,
    val readChangedAt: Long?,
    val isStarred: Boolean,
    val starredChangedAt: Long?,
)

/** An article for the reader, with the name of its source. */
data class ArticleWithFeed(
    @Embedded val article: Article,
    val feedTitle: String,
    val feedUrl: String,
)

data class GroupWithFeeds(
    @Embedded val group: FeedGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = FeedGroupCrossRef::class,
            parentColumn = "groupId",
            entityColumn = "feedId",
        ),
    )
    val feeds: List<Feed>,
)
