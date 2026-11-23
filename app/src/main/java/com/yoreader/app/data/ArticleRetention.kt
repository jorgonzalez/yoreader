package com.yoreader.app.data

import androidx.room.withTransaction
import java.util.concurrent.TimeUnit

/**
 * Automatic clean-up: read articles are deleted once the number of days chosen in
 * Options has passed since their date, since they were read and since they were
 * un-saved, whichever is latest (undated articles: the date they were first fetched).
 * Saved and unread articles are always kept.
 */
class ArticleRetention(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Returns how many articles were removed; [days] 0 (Never) removes nothing. */
    suspend fun cleanUp(days: Int): Int {
        val before = cutoff(days, now()) ?: return 0
        val dao = db.articleDao()
        return db.withTransaction {
            dao.forgetRemovedOfDeletedFeeds()
            dao.rememberOldRead(before)
            dao.deleteOldRead(before)
        }
    }

    companion object {
        /** Days offered in Options; 0 means never. */
        val CHOICES = listOf(30, 60, 90, 180, 365, 0)
        const val DEFAULT_DAYS = 90

        /** Read articles with no activity since this time are removed, or null when [days] is 0 (Never). */
        fun cutoff(days: Int, now: Long): Long? =
            if (days <= 0) null else now - TimeUnit.DAYS.toMillis(days.toLong())
    }
}
