package com.yoreader.app.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yoreader.app.BuildConfig
import com.yoreader.app.data.GroupWithFeeds

/**
 * The side panel opened from the menu icon: all articles, the groups (tap one to see
 * its sources, plus "All in …" for the whole group), managing sources, and an
 * "Add source" button pinned to the bottom, with the app version in small text below.
 * [selectedGroupId] highlights the group being viewed (-1 for "All articles").
 */
@Composable
fun AppDrawer(
    groups: List<GroupWithFeeds>,
    groupUnread: Map<Long, Int>,
    totalUnread: Int,
    selectedGroupId: Long?,
    /** Unread articles per source, for the sources listed under an open group. */
    feedUnread: Map<Long, Int>,
    selectedFeedId: Long?,
    onOpenAll: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onOpenFeed: (Long) -> Unit,
    onNewGroup: () -> Unit,
    onManageSources: () -> Unit,
    onAddSource: () -> Unit,
) {
    // Groups opened in the menu; the group being viewed starts open.
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId != null && selectedGroupId != ALL && selectedGroupId !in expanded) {
            expanded = expanded + selectedGroupId
        }
    }

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight()) {
            Text(
                "yoreader",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                NavigationDrawerItem(
                    label = { Text("All articles") },
                    icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                    badge = { UnreadBadge(totalUnread) },
                    selected = selectedGroupId == ALL,
                    onClick = onOpenAll,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Groups",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    )
                    IconButton(onClick = onNewGroup) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New group")
                    }
                }
                if (groups.isEmpty()) {
                    Text(
                        "No groups yet. Tap the folder icon to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                groups.forEach { g ->
                    val id = g.group.id
                    val isOpen = id in expanded
                    // Tapping a group opens it here, listing its sources; its articles
                    // are one tap further ("All in …").
                    NavigationDrawerItem(
                        label = { Text(g.group.name) },
                        icon = {
                            Icon(
                                if (isOpen) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                            )
                        },
                        badge = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UnreadBadge(groupUnread[id] ?: 0)
                                Icon(
                                    if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isOpen) "Hide sources" else "Show sources",
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        },
                        selected = selectedGroupId == id && !isOpen,
                        onClick = { expanded = if (isOpen) expanded - id else expanded + id },
                    )
                    AnimatedVisibility(visible = isOpen, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(Modifier.padding(start = 20.dp)) {
                            NavigationDrawerItem(
                                label = { Text("All in ${g.group.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(Icons.Default.Inbox, contentDescription = null, Modifier.size(20.dp)) },
                                badge = { UnreadBadge(groupUnread[id] ?: 0) },
                                selected = selectedGroupId == id,
                                onClick = { onOpenGroup(id) },
                            )
                            if (g.feeds.isEmpty()) {
                                Text(
                                    "No sources in this group yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                                )
                            }
                            g.feeds.sortedBy { it.title.lowercase() }.forEach { feed ->
                                NavigationDrawerItem(
                                    label = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    icon = {
                                        Icon(Icons.Default.RssFeed, contentDescription = null, Modifier.size(20.dp))
                                    },
                                    badge = { UnreadBadge(feedUnread[feed.id] ?: 0) },
                                    selected = selectedFeedId == feed.id,
                                    onClick = { onOpenFeed(feed.id) },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Manage sources") },
                    icon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                    selected = false,
                    onClick = onManageSources,
                )
            }

            Button(
                onClick = onAddSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add source")
            }
            Text(
                "yoreader ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE} · ${BuildConfig.GIT_COMMIT}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count > 0) Text(if (count > 999) "999+" else count.toString())
}

/** [AppDrawer]'s selectedGroupId value for "All articles". */
const val ALL = -1L
