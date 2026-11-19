package com.yoreader.app.data

import androidx.room.withTransaction

/**
 * Adds the source at [url] (already normalized with [FeedUrl.normalize]) to [groupIds].
 * If it's already in the library (e.g. in another group), it's just added to those
 * groups. Returns the feed's id and whether it's new (so it should be fetched now), or
 * null if it couldn't be added.
 */
suspend fun AppDatabase.addFeed(url: String, groupIds: Collection<Long>): Pair<Long, Boolean>? =
    withTransaction {
        val feedDao = feedDao()
        var id = feedDao.insert(Feed(title = FeedUrl.defaultTitle(url), url = url))
        val isNew = id != -1L
        if (!isNew) {
            val key = FeedUrl.key(url)
            id = feedDao.getAll().firstOrNull { FeedUrl.key(it.url) == key }?.id ?: return@withTransaction null
        }
        groupIds.forEach { groupDao().addFeedToGroup(FeedGroupCrossRef(id, it)) }
        id to isNew
    }

/** Ids of the groups called [names], creating the missing ones at the end of the list. */
suspend fun AppDatabase.groupIds(names: Collection<String>): List<Long> = withTransaction {
    val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (clean.isEmpty()) return@withTransaction emptyList()
    val groups = groupDao().getAll()
    var next = (groups.maxOfOrNull { it.position } ?: -1) + 1
    clean.mapNotNull { name ->
        groups.firstOrNull { it.name == name }?.id
            ?: groupDao().insertGroup(FeedGroup(name = name, position = next++)).takeIf { it != -1L }
    }
}
