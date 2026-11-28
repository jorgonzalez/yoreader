package com.yoreader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Share action of the reader and the in-app page. Without a Nostr key it shares the
 * title and link to any app; with one it offers "Share link" and "Share on Nostr…" (which
 * asks for a comment). Shows a spinner while a Nostr share is being published.
 */
@Composable
internal fun ShareButton(
    title: String,
    link: String,
    canShareOnNostr: Boolean,
    sharingOnNostr: Boolean,
    nostrAlsoNote: Boolean,
    onShareOnNostr: (comment: String, alsoNote: Boolean) -> Unit,
    size: Dp = 48.dp,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var nostrDialog by remember { mutableStateOf(false) }

    if (nostrDialog) {
        NostrShareDialog(
            title = title,
            alsoNote = nostrAlsoNote,
            onShare = { comment, alsoNote ->
                nostrDialog = false
                onShareOnNostr(comment, alsoNote)
            },
            onDismiss = { nostrDialog = false },
        )
    }

    Box {
        if (sharingOnNostr) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = {
                if (canShareOnNostr) menuOpen = true else share(context, title, link)
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Share link") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    share(context, title, link)
                },
            )
            DropdownMenuItem(
                text = { Text("Share on Nostr…") },
                leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    nostrDialog = true
                },
            )
        }
    }
}
