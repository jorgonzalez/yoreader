package com.yoreader.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Feed::class, FeedGroup::class, FeedGroupCrossRef::class, Article::class, RemovedArticle::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "yoreader.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()

        /** v2: HTTP caching headers and last refresh error on feeds. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN etag TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN lastModified TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN lastError TEXT")
            }
        }

        /** v4: cleaned article HTML, next to the existing cleaned plain text. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN cleanedHtml TEXT")
            }
        }

        /** v5: read articles removed by the automatic clean-up, so refreshes skip them. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS removed_articles " +
                        "(feedId INTEGER NOT NULL, guid TEXT NOT NULL, PRIMARY KEY(feedId, guid))"
                )
            }
        }

        /** v6: when read/saved last changed, so the laptop sync keeps the newest change. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN readChangedAt INTEGER")
                db.execSQL("ALTER TABLE articles ADD COLUMN starredChangedAt INTEGER")
            }
        }

        /** v3: folded search text per article, filled in for existing articles. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN searchText TEXT")
                // Content is cut short in the query: a cursor row must stay under ~2 MB.
                db.query(
                    "SELECT id, title, summary, substr(contentHtml, 1, 200000) FROM articles"
                ).use { c ->
                    while (c.moveToNext()) {
                        val text = SearchText.forArticle(
                            c.getString(1),
                            if (c.isNull(2)) null else c.getString(2),
                            if (c.isNull(3)) null else c.getString(3),
                        )
                        db.execSQL("UPDATE articles SET searchText = ? WHERE id = ?", arrayOf(text, c.getLong(0)))
                    }
                }
            }
        }
    }
}
