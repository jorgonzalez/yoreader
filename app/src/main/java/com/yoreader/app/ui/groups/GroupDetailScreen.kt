package com.yoreader.app.ui.groups

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yoreader.app.data.FeedUrl
import com.yoreader.app.data.GroupWithFeeds
import com.yoreader.app.ui.components.AddSourceDialog
import com.yoreader.app.ui.components.ConfirmDialog
import com.yoreader.app.ui.components.EmptyState
import com.yoreader.app.ui.components.TextInputDialog

/** The sources in one group, with rename, delete, add a source by URL, and remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: GroupWithFeeds?,
    allFeedKeys: Set<String>,
    otherGroupNames: List<String>,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSetMembership: (feedId: Long, member: Boolean) -> Unit,
    onAddFeed: (url: String) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAddSource by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.group?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (group != null) {
                        IconButton(onClick = { showRename = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename group")
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete group")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (group != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSource = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add source") },
                )
            }
        },
    ) { padding ->
        if (group != null) {
            if (group.feeds.isEmpty()) {
                EmptyState(
                    text = "This group has no sources yet.\nTap \"Add source\" and paste a feed or website address.",
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(group.feeds.sortedBy { it.title.lowercase() }, key = { it.id }) { feed ->
                        ListItem(
                            headlineContent = { Text(feed.title) },
                            supportingContent = { Text(feed.url, maxLines = 1) },
                            trailingContent = {
                                IconButton(onClick = { onSetMembership(feed.id, false) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove from group")
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (group != null) {
        if (showRename) {
            val taken = otherGroupNames.map { it.lowercase() }
            TextInputDialog(
                title = "Rename group",
                label = "Group name",
                confirmLabel = "Rename",
                initialValue = group.group.name,
                validate = { input ->
                    val name = input.trim()
                    when {
                        name.isEmpty() -> "Enter a name"
                        name.lowercase() in taken -> "A group with this name already exists"
                        else -> null
                    }
                },
                onDismiss = { showRename = false },
                onConfirm = {
                    onRename(it)
                    showRename = false
                },
            )
        }

        if (showDelete) {
            ConfirmDialog(
                title = "Delete group?",
                message = "\"${group.group.name}\" will be deleted. Its sources stay in your library.",
                confirmLabel = "Delete",
                onDismiss = { showDelete = false },
                onConfirm = {
                    showDelete = false
                    onDelete()
                },
            )
        }

        if (showAddSource) {
            AddSourceDialog(
                intoGroup = group.group,
                groupKeys = group.feeds.map { FeedUrl.key(it.url) }.toSet(),
                existingKeys = allFeedKeys,
                onDismiss = { showAddSource = false },
                onAdd = { url, _ ->
                    onAddFeed(url)
                    showAddSource = false
                },
            )
        }
    }
}
