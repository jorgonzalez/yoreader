package com.yoreader.app.ui

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.util.Xml
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.yoreader.app.YoReaderApp
import com.yoreader.app.data.Feed
import com.yoreader.app.data.FeedGroup
import com.yoreader.app.data.FeedGroupCrossRef
import com.yoreader.app.data.FeedUrl
import com.yoreader.app.data.GroupWithFeeds
import com.yoreader.app.data.Opml
import com.yoreader.app.data.OpmlException
import com.yoreader.app.data.OpmlFeed
import com.yoreader.app.fetch.RefreshScheduler
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A source plus the ids of the groups it belongs to and its article counts. */
data class SourceItem(
    val feed: Feed,
    val groupIds: Set<Long>,
    val articleCount: Int = 0,
    val unreadCount: Int = 0,
)

/**
 * Holds the groups and sources, and all actions on them. The UI validates names and
 * URLs before calling in, so the guards here are a second line of defence.
 *
 * The StateFlows are null until the first database result arrives, so screens can
 * tell "still loading" from "really empty".
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as YoReaderApp).database
    private val groupDao = db.groupDao()
    private val feedDao = db.feedDao()
    private val articleDao = db.articleDao()

    /** True while any feed refresh is running, including the periodic background one. */
    val isRefreshing: StateFlow<Boolean> =
        RefreshScheduler.isRefreshing(application)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val groups: StateFlow<List<GroupWithFeeds>?> =
        groupDao.observeGroupsWithFeeds()
            .stateIn<List<GroupWithFeeds>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Unread article count per group id. */
    val groupUnread: StateFlow<Map<Long, Int>> =
        articleDao.observeUnreadByGroup()
            .map { list -> list.associate { it.groupId to it.unread } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val totalUnread: StateFlow<Int> =
        articleDao.observeUnreadTotal()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sources: StateFlow<List<SourceItem>?> =
        combine(
            feedDao.observeAll(),
            groupDao.observeGroupsWithFeeds(),
            articleDao.observeCounts(),
        ) { feeds, groups, counts ->
            val membership = HashMap<Long, MutableSet<Long>>()
            groups.forEach { g ->
                g.feeds.forEach { f ->
                    membership.getOrPut(f.id) { mutableSetOf() }.add(g.group.id)
                }
            }
            val countsByFeed = counts.associateBy { it.feedId }
            feeds.map {
                SourceItem(
                    feed = it,
                    groupIds = membership[it.id] ?: emptySet(),
                    articleCount = countsByFeed[it.id]?.total ?: 0,
                    unreadCount = countsByFeed[it.id]?.unread ?: 0,
                )
            }
        }.stateIn<List<SourceItem>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addGroup(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val next = (groups.value?.maxOfOrNull { it.group.position } ?: -1) + 1
            groupDao.insertGroup(FeedGroup(name = clean, position = next))
        }
    }

    fun renameGroup(group: FeedGroup, name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean == group.name) return
        viewModelScope.launch {
            try {
                groupDao.updateGroup(group.copy(name = clean))
            } catch (e: SQLiteConstraintException) {
                // Name already taken; the dialog normally catches this first.
            }
        }
    }

    fun deleteGroup(group: FeedGroup) {
        viewModelScope.launch { groupDao.deleteGroup(group) }
    }

    /**
     * [url] must already be normalized with [FeedUrl.normalize]. If the source already
     * exists (e.g. it's in another group), it is just added to [groupIds].
     */
    fun addFeed(url: String, groupIds: Set<Long>) {
        viewModelScope.launch {
            val (id, isNew) = db.withTransaction {
                var id = feedDao.insert(Feed(title = FeedUrl.defaultTitle(url), url = url))
                val isNew = id != -1L
                if (!isNew) {
                    val key = FeedUrl.key(url)
                    id = feedDao.getAll().firstOrNull { FeedUrl.key(it.url) == key }?.id
                        ?: return@withTransaction -1L to false
                }
                groupIds.forEach { groupDao.addFeedToGroup(FeedGroupCrossRef(id, it)) }
                id to isNew
            }
            // Fetch right away so the real title and first articles show up.
            if (isNew) RefreshScheduler.refreshFeed(getApplication(), id)
        }
    }

    fun refreshAll() = RefreshScheduler.refreshAllNow(getApplication())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for a snackbar (import/export results). */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Writes all sources and groups as OPML to [uri] (from the system "save" picker). */
    fun exportOpml(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val groups = groupDao.observeGroupsWithFeeds().first()
                val feeds = feedDao.getAll()
                val xml = Opml.write(groups, feeds)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("can't open file")
                }
                "Exported ${countText(feeds.size, "source", "sources")} " +
                    "and ${countText(groups.size, "group", "groups")}"
            } catch (e: Exception) {
                "Couldn't save the file"
            }
            _messages.send(message)
        }
    }

    /**
     * Adds the sources in the OPML file at [uri]: new feeds are created and fetched,
     * folders become groups (created if missing), and feeds already in the library are
     * just added to those groups. Nothing is removed.
     */
    fun importOpml(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val entries = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { Opml.parse(it, Xml.newPullParser()) }
                        ?: throw IOException("can't open file")
                }
                val result = db.withTransaction { importEntries(entries) }
                if (result.newFeeds > 0) RefreshScheduler.refreshAllNow(getApplication())
                result.describe()
            } catch (e: OpmlException) {
                e.message ?: "The file isn't valid OPML"
            } catch (e: Exception) {
                "Couldn't read the file"
            }
            _messages.send(message)
        }
    }

    private data class ImportResult(val newFeeds: Int, val existingFeeds: Int, val newGroups: Int, val skipped: Int) {
        fun describe(): String {
            if (newFeeds == 0 && existingFeeds == 0) return "No sources found in the file"
            val parts = mutableListOf("Imported ${countText(newFeeds, "new source", "new sources")}")
            if (newGroups > 0) parts += countText(newGroups, "group", "groups")
            var text = parts.joinToString(" and ")
            if (existingFeeds > 0) text += "; $existingFeeds already there"
            if (skipped > 0) text += "; $skipped skipped (invalid address)"
            return text
        }
    }

    private suspend fun importEntries(entries: List<OpmlFeed>): ImportResult {
        val feedsByKey = feedDao.getAll().associateBy { FeedUrl.key(it.url) }.toMutableMap()
        val groups = groupDao.observeGroupsWithFeeds().first().map { it.group }
        val groupIds = groups.associate { it.name.lowercase() to it.id }.toMutableMap()
        var nextPosition = (groups.maxOfOrNull { it.position } ?: -1) + 1
        var newFeeds = 0
        var existingFeeds = 0
        var newGroups = 0
        var skipped = 0

        for (entry in entries) {
            val url = FeedUrl.normalize(entry.url)
            if (url == null) {
                skipped++
                continue
            }
            val key = FeedUrl.key(url)
            val feedId = feedsByKey[key]?.id?.also { existingFeeds++ } ?: run {
                val feed = Feed(
                    title = entry.title ?: FeedUrl.defaultTitle(url),
                    url = url,
                    siteUrl = entry.siteUrl,
                )
                val id = feedDao.insert(feed)
                if (id == -1L) return@run null
                newFeeds++
                feedsByKey[key] = feed.copy(id = id)
                id
            } ?: continue

            for (name in entry.groups) {
                val groupId = groupIds[name.lowercase()] ?: run {
                    val id = groupDao.insertGroup(FeedGroup(name = name, position = nextPosition++))
                    if (id == -1L) return@run null
                    newGroups++
                    groupIds[name.lowercase()] = id
                    id
                } ?: continue
                groupDao.addFeedToGroup(FeedGroupCrossRef(feedId, groupId))
            }
        }
        return ImportResult(newFeeds, existingFeeds, newGroups, skipped)
    }

    private companion object {
        fun countText(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
    }

    fun refreshFeed(feedId: Long) = RefreshScheduler.refreshFeed(getApplication(), feedId)

    fun deleteFeed(feed: Feed) {
        viewModelScope.launch { feedDao.delete(feed) }
    }

    fun setMembership(feedId: Long, groupId: Long, member: Boolean) {
        viewModelScope.launch {
            val ref = FeedGroupCrossRef(feedId, groupId)
            if (member) groupDao.addFeedToGroup(ref) else groupDao.removeFeedFromGroup(ref)
        }
    }
}
