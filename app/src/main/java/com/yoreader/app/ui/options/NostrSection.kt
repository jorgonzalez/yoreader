package com.yoreader.app.ui.options

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yoreader.app.nostr.NostrKey
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import com.yoreader.app.nostr.NostrClient
import com.yoreader.app.nostr.NostrShare
import com.yoreader.app.ui.components.TextInputDialog
import kotlinx.coroutines.launch

/**
 * Your Nostr identity, for sharing articles with others: create one, or bring one from
 * another app; show and share the public key; back up or remove the private one.
 */
@Composable
internal fun NostrSection(
    key: NostrKey?,
    onCreate: () -> Unit,
    onRestore: (NostrKey) -> Unit,
    onRemove: () -> Unit,
    relays: List<String>,
    onSetRelays: (List<String>) -> Unit,
    onCheckRelays: suspend () -> List<Pair<String, String?>>,
) {
    val context = LocalContext.current
    var restoring by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    if (key == null) {
        Text(
            "Share articles with others over Nostr, an open network with no accounts. " +
                "Your identity is a key kept on this phone; others follow its public part.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp)) {
            OutlinedButton(onClick = onCreate) { Text("Create key") }
            TextButton(onClick = { restoring = true }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Use existing key")
            }
        }
    } else {
        val npub = key.npub
        OptionRow(
            icon = { Icon(Icons.Default.Key, contentDescription = null) },
            title = "Your public key",
            detail = shortKey(npub),
            trailing = { Icon(Icons.Default.ContentCopy, contentDescription = "Copy", Modifier.size(20.dp)) },
            onClick = { copy(context, "Nostr public key", npub, sensitive = false) },
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { shareNpub(context, npub) }) { Text("Share") }
            TextButton(onClick = { backingUp = true }) { Text("Back up") }
            TextButton(onClick = { removing = true }) { Text("Remove") }
        }
        RelayList(relays, onSetRelays, onCheckRelays)
    }

    if (restoring) RestoreDialog(onRestore = { onRestore(it); restoring = false }, onDismiss = { restoring = false })
    if (backingUp && key != null) BackupDialog(key, onDismiss = { backingUp = false })
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove your Nostr key?") },
            text = {
                Text(
                    "It's deleted from this phone. Without a backup (nsec1…) it can't be " +
                        "recovered, and people who follow you would have to follow a new key.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(); removing = false }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RestoreDialog(onRestore: (NostrKey) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use existing key") },
        text = {
            Column {
                Text("Paste your private key (nsec1…), e.g. from a backup or another Nostr app.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = { Text("nsec1…") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val key = NostrKey.parse(text)
                when {
                    key != null -> onRestore(key)
                    NostrKey.isNpub(text) -> error = "That's a public key (npub). Paste the private one (nsec)."
                    else -> error = "That isn't a Nostr private key."
                }
            }) { Text("Use") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BackupDialog(key: NostrKey, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val nsec = remember(key) { key.nsec() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Back up your private key") },
        text = {
            Column {
                Text(
                    "Keep it somewhere safe, like a password manager. It's the only way to " +
                        "move your identity to another phone. Anyone who has it can post as you.",
                )
                SelectionContainer {
                    Text(
                        nsec,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { copy(context, "Nostr private key", nsec, sensitive = true) }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * Your relays: where your shares go and where the people you follow are read (besides
 * their own relays). Remove, add, test, or go back to the defaults.
 */
@Composable
private fun RelayList(
    relays: List<String>,
    onSetRelays: (List<String>) -> Unit,
    onCheckRelays: suspend () -> List<Pair<String, String?>>,
) {
    val scope = rememberCoroutineScope()
    /** Null: not tested; empty: testing; otherwise each relay's error (null: fine). */
    var check by remember { mutableStateOf<List<Pair<String, String?>>?>(null) }
    var adding by remember { mutableStateOf(false) }
    // A test result is for the list it tested.
    LaunchedEffect(relays) { check = null }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
        Icon(Icons.Default.Hub, contentDescription = null)
        Text("Relays", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
        TextButton(
            onClick = {
                check = emptyList()
                scope.launch { check = onCheckRelays() }
            },
            enabled = check?.isEmpty() != true,
        ) { Text(if (check?.isEmpty() == true) "Testing…" else "Test") }
    }
    val results = check.orEmpty().toMap()
    relays.forEach { url ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 56.dp, end = 8.dp)) {
            val status = when {
                check.isNullOrEmpty() || url !in results -> ""
                results[url] == null -> "✓ "
                else -> "✗ "
            }
            Column(Modifier.weight(1f)) {
                Text(status + host(url), style = MaterialTheme.typography.bodyMedium)
                results[url]?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = { onSetRelays(relays - url) }, enabled = relays.size > 1) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${host(url)}", Modifier.size(20.dp))
            }
        }
    }
    Row(Modifier.padding(start = 48.dp)) {
        TextButton(onClick = { adding = true }) { Text("Add relay") }
        if (relays != NostrClient.DEFAULT_RELAYS) {
            TextButton(onClick = { onSetRelays(NostrClient.DEFAULT_RELAYS) }) { Text("Reset") }
        }
    }
    Text(
        "Your shares go to these relays, and the people you follow are read from them " +
            "(and from their own). Your next share also publishes this list, so people " +
            "who follow you know where to look.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
    )
    if (adding) {
        TextInputDialog(
            title = "Add relay",
            label = "Relay address",
            placeholder = "relay.example.com",
            confirmLabel = "Add",
            keyboardType = KeyboardType.Uri,
            validate = { input ->
                val url = NostrShare.normalizeRelay(input)
                when {
                    url == null -> "Enter a relay address, like wss://relay.example.com"
                    url in relays -> "Already in the list"
                    else -> null
                }
            },
            onDismiss = { adding = false },
            onConfirm = { input ->
                NostrShare.normalizeRelay(input)?.let { onSetRelays(relays + it) }
                adding = false
            },
        )
    }
}

private fun host(url: String): String = url.removePrefix("wss://").removePrefix("ws://").trimEnd('/')

/** "npub1abcdef…uvwxyz": enough to recognise it. */
private fun shortKey(npub: String): String = npub.take(12) + "…" + npub.takeLast(6)

private fun shareNpub(context: Context, npub: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "My Nostr public key, to follow what I share in yoreader:\n$npub")
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Copies [text]; [sensitive] keeps it out of the clipboard preview (Android 13+). */
private fun copy(context: Context, label: String, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    // Android 13+ confirms copies itself.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
