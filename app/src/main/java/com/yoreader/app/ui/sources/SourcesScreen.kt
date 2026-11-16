package com.yoreader.app.ui.sources

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yoreader.app.data.Feed
import com.yoreader.app.data.FeedGroup
import com.yoreader.app.data.FeedUrl
import com.yoreader.app.ui.SourceItem
import com.yoreader.app.ui.components.AddSourceDialog
import com.yoreader.app.ui.components.ConfirmDialog
import com.yoreader.app.ui.components.EmptyState
import com.yoreader.app.ui.components.GroupCheckList
import com.yoreader.app.ui.components.RefreshAction
import com.yoreader.app.ui.components.TextInputDialog
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * All sources. Add a source by URL (optionally into groups), tap a source to read its
 * articles; its ⋮ menu changes its groups, refreshes it, or deletes it. The top bar's ⋮
 * menu exports all sources to an OPML file or imports them from one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    sources: List<SourceItem>?,
    groups: List<FeedGroup>,
    onBack: () -> Unit,
    onOpenFeed: (feedId: Long) -> Unit,
    onAddFeed: (url: String, groupIds: Set<Long>) -> Unit,
    onSetMembership: (feedId: Long, groupId: Long, member: Boolean) -> Unit,
    onDeleteFeed: (Feed) -> Unit,
    refreshing: Boolean,
    onRefreshAll: () -> Unit,
    onRefreshFeed: (feedId: Long) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    messages: Flow<String>,
) {
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    // System file pickers: "save as" for export, "open" for import. OPML files come
    // with all sorts of MIME types (or none), so any file can be picked for import.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-opml"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    LaunchedEffect(messages) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    var showAdd by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<Feed?>(null) }

    val groupsById = groups.associateBy { it.id }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    RefreshAction(refreshing, onRefreshAll)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Import or export sources")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export sources to file") },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch("yoreader-sources-${LocalDate.now()}.opml")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import sources from file") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add source") },
            )
        },
    ) { padding ->
        when {
            sources == null -> Unit
            sources.isEmpty() -> EmptyState(
                text = "No sources yet.\nTap \"Add source\" and paste the URL of an RSS or Atom feed.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(sources, key = { it.feed.id }) { item ->
                    val groupNames = item.groupIds
                        .mapNotNull { groupsById[it]?.name }
                        .sortedBy { it.lowercase() }
                    ListItem(
                        headlineContent = { Text(item.feed.title) },
                        supportingContent = {
                            Column {
                                Text(
                                    item.feed.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val error = item.feed.lastError
                                Text(
                                    error ?: fetchStatusText(item),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (error != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    if (groupNames.isEmpty()) "No groups" else groupNames.joinToString(", "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        trailingContent = {
                            SourceMenu(
                                onEditGroups = { editingId = item.feed.id },
                                onRefresh = { onRefreshFeed(item.feed.id) },
                                onDelete = { deleting = item.feed },
                            )
                        },
                        modifier = Modifier.clickable { onOpenFeed(item.feed.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        AddSourceDialog(
            groups = groups,
            existingKeys = sources.orEmpty().map { FeedUrl.key(it.feed.url) }.toSet(),
            onDismiss = { showAdd = false },
            onAdd = { url, groupIds ->
                onAddFeed(url, groupIds)
                showAdd = false
            },
        )
    }

    val editing = sources?.firstOrNull { it.feed.id == editingId }
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text(editing.feed.title) },
            text = {
                if (groups.isEmpty()) {
                    Text("You don't have any groups yet. Create one on the main screen first.")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Groups", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        GroupCheckList(
                            groups = groups,
                            selected = editing.groupIds,
                            onToggle = { groupId, checked ->
                                onSetMembership(editing.feed.id, groupId, checked)
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editingId = null }) { Text("Done") } },
        )
    }

    val toDelete = deleting
    if (toDelete != null) {
        ConfirmDialog(
            title = "Delete source?",
            message = "\"${toDelete.title}\" will be removed from your library and from all groups.",
            confirmLabel = "Delete",
            onDismiss = { deleting = null },
            onConfirm = {
                onDeleteFeed(toDelete)
                deleting = null
            },
        )
    }
}

/** ⋮ menu on a source row: groups, refresh, delete. */
@Composable
private fun SourceMenu(onEditGroups: () -> Unit, onRefresh: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Source options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Groups…") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = { open = false; onEditGroups() },
            )
            DropdownMenuItem(
                text = { Text("Refresh now") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { open = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/** "Updated 5 minutes ago · 12 unread of 40", or "Not fetched yet". */
private fun fetchStatusText(item: SourceItem): String {
    val fetchedAt = item.feed.lastFetchedAt ?: return "Not fetched yet"
    val ago = if (System.currentTimeMillis() - fetchedAt < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    }
    val articles = when (item.articleCount) {
        0 -> "no articles"
        else -> "${item.unreadCount} unread of ${item.articleCount}"
    }
    return "Updated $ago · $articles"
}
