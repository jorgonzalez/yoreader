package com.yoreader.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yoreader.app.data.GroupWithFeeds

/**
 * The side panel opened from the menu icon: all articles, the groups, managing sources,
 * and an "Add source" button pinned to the bottom.
 * [selectedGroupId] highlights the group being viewed (-1 for "All articles").
 */
@Composable
fun AppDrawer(
    groups: List<GroupWithFeeds>,
    groupUnread: Map<Long, Int>,
    totalUnread: Int,
    selectedGroupId: Long?,
    onOpenAll: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onNewGroup: () -> Unit,
    onManageSources: () -> Unit,
    onAddSource: () -> Unit,
) {
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
                    NavigationDrawerItem(
                        label = { Text(g.group.name) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        badge = { UnreadBadge(groupUnread[g.group.id] ?: 0) },
                        selected = selectedGroupId == g.group.id,
                        onClick = { onOpenGroup(g.group.id) },
                    )
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
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add source")
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count > 0) Text(if (count > 999) "999+" else count.toString())
}

/** [AppDrawer]'s selectedGroupId value for "All articles". */
const val ALL = -1L
