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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yoreader.app.nostr.NostrKey

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
