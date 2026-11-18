package com.yoreader.app.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.yoreader.app.data.AppDatabase
import com.yoreader.app.fetch.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

/**
 * Keeps a sync file (chosen in Options, e.g. in Google Drive) up to date for the
 * laptop page, and takes the read/saved changes made there. Runs after background
 * refreshes and when the app is opened or left. The file is reached through Android's
 * file picker permission, so it works with any storage app that offers files there.
 */
class LaptopSync(
    private val context: Context,
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class State(
        val fileUri: Uri? = null,
        val fileName: String? = null,
        val lastSyncAt: Long? = null,
        val error: String? = null,
        val running: Boolean = false,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(
        State(
            fileUri = prefs.getString(KEY_URI, null)?.let(Uri::parse),
            fileName = prefs.getString(KEY_NAME, null),
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
            error = prefs.getString(KEY_ERROR, null),
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** Starts syncing with [uri] (from the file picker), keeping access to it. */
    suspend fun useFile(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            _state.update { it.copy(error = "That storage app doesn't let yoreader keep using the file") }
            return
        }
        _state.value.fileUri?.takeIf { it != uri }?.let(::release)
        val name = withContext(Dispatchers.IO) { displayName(uri) }
        prefs.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_NAME, name)
            .remove(KEY_LAST_SYNC).remove(KEY_ERROR).remove(KEY_FINGERPRINT).remove(KEY_WRITTEN_AT)
            .apply()
        _state.value = State(fileUri = uri, fileName = name)
        sync()
    }

    /** Stops syncing; the file itself is left as it is. */
    fun stop() {
        _state.value.fileUri?.let(::release)
        prefs.edit().clear().apply()
        _state.value = State()
    }

    /** Syncs now if a file is set; failures end up in [state]. */
    suspend fun sync() {
        val uri = _state.value.fileUri ?: return
        mutex.withLock {
            _state.update { it.copy(running = true) }
            val error = try {
                withContext(Dispatchers.IO) { syncWith(uri) }
                null
            } catch (e: Exception) {
                describe(e)
            }
            val at = now()
            prefs.edit().apply {
                if (error == null) putLong(KEY_LAST_SYNC, at).remove(KEY_ERROR) else putString(KEY_ERROR, error)
            }.apply()
            _state.update {
                if (error == null) it.copy(running = false, lastSyncAt = at, error = null)
                else it.copy(running = false, error = error)
            }
        }
    }

    private suspend fun syncWith(uri: Uri) {
        val resolver = context.contentResolver
        val dao = db.articleDao()

        // 1. Changes from the laptop, unless the file is still the phone's last write
        //    (seen from its first bytes, without reading megabytes).
        val head = resolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(PEEK_BYTES)
            val n = input.read(buf).coerceAtLeast(0)
            String(buf, 0, n, Charsets.UTF_8)
        } ?: throw FileNotFoundException()
        val (by, at) = SyncFile.peek(head)
        val ownLastWrite = by == SyncFile.BY_PHONE && at != null && at == prefs.getLong(KEY_WRITTEN_AT, 0L)
        if (!ownLastWrite && head.isNotBlank()) {
            val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw FileNotFoundException()
            val statuses = SyncFile.parse(text).statuses
            db.withTransaction {
                for (s in statuses) {
                    s.readAt?.let { dao.syncRead(s.feedUrl, s.guid, s.read, it) }
                    s.savedAt?.let { dao.syncSaved(s.feedUrl, s.guid, s.saved, it) }
                }
            }
        }

        // 2. The phone's view, written unless nothing changed since the last write. The
        //    day counts too: read articles leave the file after READ_DAYS.
        val time = now()
        val fingerprint = dao.syncFingerprint() + "|" + TimeUnit.MILLISECONDS.toDays(time)
        if (ownLastWrite && fingerprint == prefs.getString(KEY_FINGERPRINT, null)) return

        val body = SyncFile.body(
            groups = db.groupDao().observeGroupsWithFeeds().first().map { it.group.name },
            feeds = feedEntries(),
            articles = dao.forSync(time - TimeUnit.DAYS.toMillis(READ_DAYS)).map {
                SyncFile.ArticleEntry(
                    feedUrl = it.feedUrl,
                    guid = it.guid,
                    title = it.title,
                    link = it.link,
                    publishedAt = it.publishedAt,
                    excerpt = it.excerptHtml?.let { h -> Html.toPlainText(h).take(EXCERPT_CHARS) },
                    html = it.html,
                    read = it.isRead,
                    readAt = it.readChangedAt,
                    saved = it.isStarred,
                    savedAt = it.starredChangedAt,
                )
            },
        )
        val bytes = SyncFile.write(body, time).toByteArray(Charsets.UTF_8)
        // "wt" replaces the whole file; a few storage apps only accept "w".
        val out = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } catch (e: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } ?: throw FileNotFoundException()
        out.use { it.write(bytes) }
        prefs.edit().putString(KEY_FINGERPRINT, fingerprint).putLong(KEY_WRITTEN_AT, time).apply()
    }

    private suspend fun feedEntries(): List<SyncFile.FeedEntry> {
        val groupsByFeed = mutableMapOf<Long, MutableList<String>>()
        db.groupDao().observeGroupsWithFeeds().first().forEach { g ->
            g.feeds.forEach { f -> groupsByFeed.getOrPut(f.id) { mutableListOf() } += g.group.name }
        }
        return db.feedDao().getAll().sortedBy { it.title.lowercase() }.map {
            SyncFile.FeedEntry(it.url, it.title, it.siteUrl, groupsByFeed[it.id].orEmpty())
        }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun release(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Already gone.
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is SyncFile.FormatException -> e.message ?: "The sync file isn't valid"
        is FileNotFoundException -> "The sync file can't be found; choose it again"
        is SecurityException -> "No access to the sync file any more; choose it again"
        else -> "Sync failed: ${e.message ?: e.javaClass.simpleName}"
    }

    private companion object {
        const val PREFS = "laptop_sync"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_ERROR = "error"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_WRITTEN_AT = "written_at"

        /** Read articles stay in the file this long after being read, so the laptop sees it. */
        const val READ_DAYS = 7L
        const val EXCERPT_CHARS = 300
        const val PEEK_BYTES = 200
    }
}
