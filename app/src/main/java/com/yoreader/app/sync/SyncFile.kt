package com.yoreader.app.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The laptop sync file (format described in sync/README.md at the repo root): the
 * phone writes its feeds, groups and articles there; the laptop page shows them and
 * changes read/saved states in it. Each state has the time it changed, and the
 * newest change wins on both sides.
 */
object SyncFile {
    const val FORMAT = "yoreader-sync"
    const val VERSION = 1
    const val BY_PHONE = "phone"

    /** A source; [error] is why its last refresh failed, if it did. */
    data class FeedEntry(
        val url: String,
        val title: String,
        val siteUrl: String?,
        val groups: List<String>,
        val error: String? = null,
    )

    /**
     * A source added on the laptop ("addFeeds" in the file), identified by [url] as
     * typed there and [at], when it was added. The phone lists the ones it took in
     * "addedFeeds" (without groups).
     */
    data class FeedRequest(val url: String, val groups: List<String>, val at: Long)

    data class ArticleEntry(
        val feedUrl: String,
        val guid: String,
        val title: String,
        val link: String,
        val publishedAt: Long?,
        val excerpt: String?,
        val html: String?,
        val read: Boolean,
        val readAt: Long?,
        val saved: Boolean,
        val savedAt: Long?,
    )

    /** A read/saved state from the file, as the laptop may have changed it. */
    data class Status(
        val feedUrl: String,
        val guid: String,
        val read: Boolean,
        val readAt: Long?,
        val saved: Boolean,
        val savedAt: Long?,
    )

    /** What [parse] found: who wrote the file last, when, the article states and new sources. */
    data class Contents(
        val writtenBy: String?,
        val writtenAt: Long?,
        val statuses: List<Status>,
        val addFeeds: List<FeedRequest> = emptyList(),
    )

    class FormatException(message: String) : Exception(message)

    /** The file's body, without the "written" fields (so it can be compared between syncs). */
    fun body(
        groups: List<String>,
        feeds: List<FeedEntry>,
        articles: List<ArticleEntry>,
        addedFeeds: List<FeedRequest> = emptyList(),
    ): JSONObject =
        JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("groups", JSONArray(groups))
            .put(
                "feeds",
                JSONArray().apply {
                    feeds.forEach { f ->
                        put(
                            JSONObject()
                                .put("url", f.url)
                                .put("title", f.title)
                                .putOpt("siteUrl", f.siteUrl)
                                .put("groups", JSONArray(f.groups))
                                .putOpt("error", f.error)
                        )
                    }
                },
            )
            .put(
                "articles",
                JSONArray().apply {
                    articles.forEach { a ->
                        put(
                            JSONObject()
                                .put("feed", a.feedUrl)
                                .put("guid", a.guid)
                                .put("title", a.title)
                                .put("link", a.link)
                                .putOpt("published", a.publishedAt)
                                .putOpt("excerpt", a.excerpt)
                                .putOpt("html", a.html)
                                .put("read", a.read)
                                .putOpt("readAt", a.readAt)
                                .put("saved", a.saved)
                                .putOpt("savedAt", a.savedAt)
                        )
                    }
                },
            )
            .apply {
                if (addedFeeds.isNotEmpty()) {
                    put(
                        "addedFeeds",
                        JSONArray().apply {
                            addedFeeds.forEach { put(JSONObject().put("url", it.url).put("at", it.at)) }
                        },
                    )
                }
            }

    /**
     * The whole file as written by the phone at [now]. "writtenBy"/"writtenAt" come
     * first (the laptop page keeps the order), so [peek] finds them in the first bytes.
     */
    fun write(body: JSONObject, now: Long): String {
        val rest = body.toString().removePrefix("{")
        return "{\"writtenBy\":\"$BY_PHONE\",\"writtenAt\":$now" + (if (rest == "}") "}" else ",$rest")
    }

    private val PEEK_BY = Regex("\"writtenBy\"\\s*:\\s*\"(\\w+)\"")
    private val PEEK_AT = Regex("\"writtenAt\"\\s*:\\s*(\\d+)")

    /** Who wrote the file and when, from its first bytes; nulls when they aren't there. */
    fun peek(head: String): Pair<String?, Long?> =
        PEEK_BY.find(head)?.groupValues?.get(1) to PEEK_AT.find(head)?.groupValues?.get(1)?.toLongOrNull()

    /** Reads a sync file; an empty file (just created) has nothing in it. */
    fun parse(text: String): Contents {
        if (text.isBlank()) return Contents(null, null, emptyList())
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw FormatException("The sync file isn't valid (${e.message})")
        }
        if (root.optString("format") != FORMAT) throw FormatException("This isn't a yoreader sync file")
        val version = root.optInt("version", 0)
        if (version > VERSION) throw FormatException("The sync file is from a newer yoreader; update the app")
        val list = root.optJSONArray("articles") ?: JSONArray()
        val statuses = (0 until list.length()).mapNotNull { i ->
            val a = list.optJSONObject(i) ?: return@mapNotNull null
            val feed = a.optString("feed").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val guid = a.optString("guid").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Status(
                feedUrl = feed,
                guid = guid,
                read = a.optBoolean("read"),
                readAt = a.optLongOrNull("readAt"),
                saved = a.optBoolean("saved"),
                savedAt = a.optLongOrNull("savedAt"),
            )
        }
        val requests = root.optJSONArray("addFeeds") ?: JSONArray()
        val addFeeds = (0 until requests.length()).mapNotNull { i ->
            val r = requests.optJSONObject(i) ?: return@mapNotNull null
            val url = r.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val at = r.optLongOrNull("at") ?: return@mapNotNull null
            val groups = r.optJSONArray("groups") ?: JSONArray()
            FeedRequest(url, (0 until groups.length()).mapNotNull { groups.optString(it).takeIf(String::isNotBlank) }, at)
        }
        return Contents(
            writtenBy = root.optString("writtenBy").takeIf { it.isNotEmpty() },
            writtenAt = root.optLongOrNull("writtenAt"),
            statuses = statuses,
            addFeeds = addFeeds,
        )
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}
