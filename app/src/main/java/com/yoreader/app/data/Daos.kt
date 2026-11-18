package com.yoreader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Transaction
    @Query("SELECT * FROM feed_groups ORDER BY position, name")
    fun observeGroupsWithFeeds(): Flow<List<GroupWithFeeds>>

    /** Returns -1 if a group with the same name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: FeedGroup): Long

    @Update
    suspend fun updateGroup(group: FeedGroup)

    @Delete
    suspend fun deleteGroup(group: FeedGroup)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFeedToGroup(ref: FeedGroupCrossRef)

    @Delete
    suspend fun removeFeedFromGroup(ref: FeedGroupCrossRef)
}

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Feed>>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getById(id: Long): Feed?

    @Query("SELECT * FROM feeds")
    suspend fun getAll(): List<Feed>

    @Query("SELECT * FROM feeds WHERE url = :url COLLATE NOCASE LIMIT 1")
    suspend fun getByUrl(url: String): Feed?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: Feed): Long

    @Update
    suspend fun update(feed: Feed)

    @Delete
    suspend fun delete(feed: Feed)
}

/** Columns for [ArticleListRow]; the excerpt is cut short so lists stay light. */
private const val LIST_COLUMNS = """
    a.id, a.feedId, f.title AS feedTitle, a.title,
    substr(COALESCE(a.summary, a.contentHtml), 1, 1500) AS excerptHtml,
    a.publishedAt, a.isRead, a.isStarred
"""

/** Lists are capped; older articles stay in the database but aren't shown (yet). */
private const val LIST_LIMIT = "LIMIT 1000"

@Dao
interface ArticleDao {
    @Query(
        "SELECT $LIST_COLUMNS FROM articles a INNER JOIN feeds f ON f.id = a.feedId " +
            "WHERE (:unreadOnly = 0 OR a.isRead = 0) " +
            "ORDER BY a.publishedAt DESC, a.id DESC $LIST_LIMIT"
    )
    fun observeAllList(unreadOnly: Boolean): Flow<List<ArticleListRow>>

    @Query(
        "SELECT $LIST_COLUMNS FROM articles a INNER JOIN feeds f ON f.id = a.feedId " +
            "INNER JOIN feed_group_cross_ref r ON r.feedId = a.feedId " +
            "WHERE r.groupId = :groupId AND (:unreadOnly = 0 OR a.isRead = 0) " +
            "ORDER BY a.publishedAt DESC, a.id DESC $LIST_LIMIT"
    )
    fun observeGroupList(groupId: Long, unreadOnly: Boolean): Flow<List<ArticleListRow>>

    @Query(
        "SELECT $LIST_COLUMNS FROM articles a INNER JOIN feeds f ON f.id = a.feedId " +
            "WHERE a.feedId = :feedId AND (:unreadOnly = 0 OR a.isRead = 0) " +
            "ORDER BY a.publishedAt DESC, a.id DESC $LIST_LIMIT"
    )
    fun observeFeedList(feedId: Long, unreadOnly: Boolean): Flow<List<ArticleListRow>>

    /** Home: the newest unread articles that aren't saved for later. */
    @Query(
        "SELECT $LIST_COLUMNS FROM articles a INNER JOIN feeds f ON f.id = a.feedId " +
            "WHERE a.isRead = 0 AND a.isStarred = 0 " +
            "ORDER BY a.publishedAt DESC, a.id DESC LIMIT :limit"
    )
    fun observeLatest(limit: Int): Flow<List<ArticleListRow>>

    /** Saved for later (stored as isStarred). */
    @Query(
        "SELECT $LIST_COLUMNS FROM articles a INNER JOIN feeds f ON f.id = a.feedId " +
            "WHERE a.isStarred = 1 ORDER BY a.publishedAt DESC, a.id DESC"
    )
    fun observeSaved(): Flow<List<ArticleListRow>>

    /**
     * Search in title, summary and text. [pattern] is a LIKE pattern over text folded
     * with [SearchText.normalize], with `\` as escape character.
     */
    @Query(
        "SELECT $LIST_COLUMNS FROM articles a INNER JOIN feeds f ON f.id = a.feedId " +
            "WHERE a.searchText LIKE :pattern ESCAPE '\\' " +
            "ORDER BY a.publishedAt DESC, a.id DESC LIMIT 200"
    )
    fun search(pattern: String): Flow<List<ArticleListRow>>

    @Query(
        """
        SELECT a.*, f.title AS feedTitle, f.url AS feedUrl
        FROM articles a INNER JOIN feeds f ON f.id = a.feedId
        WHERE a.id = :id
        """
    )
    fun observeWithFeed(id: Long): Flow<ArticleWithFeed?>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: Long): Article?

    /**
     * Existing (feedId, guid) rows are kept, so read state survives a refresh.
     * Returns the new row ids, with -1 for articles that were already stored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<Article>): List<Long>

    @Query(
        """
        SELECT feedId, COUNT(*) AS total, SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END) AS unread
        FROM articles GROUP BY feedId
        """
    )
    fun observeCounts(): Flow<List<FeedCounts>>

    @Query(
        """
        SELECT r.groupId AS groupId, COUNT(*) AS unread
        FROM articles a INNER JOIN feed_group_cross_ref r ON r.feedId = a.feedId
        WHERE a.isRead = 0 GROUP BY r.groupId
        """
    )
    fun observeUnreadByGroup(): Flow<List<GroupUnread>>

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadTotal(): Flow<Int>

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE articles SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    // --- Automatic clean-up of old read articles (see ArticleRetention) ---------------

    /** Remembers the read, unsaved articles published before [before] as removed. */
    @Query(
        "INSERT OR IGNORE INTO removed_articles (feedId, guid) SELECT feedId, guid FROM articles " +
            "WHERE isRead = 1 AND isStarred = 0 AND publishedAt < :before"
    )
    suspend fun rememberOldRead(before: Long)

    /** Deletes the read, unsaved articles published before [before]; returns how many. */
    @Query("DELETE FROM articles WHERE isRead = 1 AND isStarred = 0 AND publishedAt < :before")
    suspend fun deleteOldRead(before: Long): Int

    @Query("DELETE FROM removed_articles WHERE feedId NOT IN (SELECT id FROM feeds)")
    suspend fun forgetRemovedOfDeletedFeeds()

    @Query("SELECT guid FROM removed_articles WHERE feedId = :feedId")
    suspend fun removedGuids(feedId: Long): List<String>

    @Query("DELETE FROM removed_articles WHERE feedId = :feedId AND guid IN (:guids)")
    suspend fun forgetRemoved(feedId: Long, guids: List<String>)

    @Query(
        "UPDATE articles SET isRead = 1 WHERE isRead = 0 AND feedId IN " +
            "(SELECT feedId FROM feed_group_cross_ref WHERE groupId = :groupId)"
    )
    suspend fun markGroupRead(groupId: Long)

    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0 AND feedId = :feedId")
    suspend fun markFeedRead(feedId: Long)

    /** Stores a cleaned version; [searchText] should cover it so search finds its text. */
    @Query(
        "UPDATE articles SET cleanedHtml = :html, cleanedText = :text, searchText = :searchText " +
            "WHERE id = :id"
    )
    suspend fun setCleaned(id: Long, html: String, text: String, searchText: String)
}
