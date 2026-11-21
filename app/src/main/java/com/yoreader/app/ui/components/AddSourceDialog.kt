package com.yoreader.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yoreader.app.data.FeedGroup
import com.yoreader.app.data.FeedUrl

/**
 * Asks for a feed (or website) URL.
 *
 * - With [intoGroup]: the source goes into that group, with no group choice shown. A URL
 *   that is already a source elsewhere is allowed; it just gets linked to the group.
 * - Without: the user may tick any of [groups], and URLs already in [existingKeys] are refused.
 *
 * [existingKeys] / [groupKeys] are [FeedUrl.key]s of all sources / of the group's sources.
 */
@Composable
fun AddSourceDialog(
    existingKeys: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (url: String, groupIds: Set<Long>) -> Unit,
    groups: List<FeedGroup> = emptyList(),
    intoGroup: FeedGroup? = null,
    groupKeys: Set<String> = emptySet(),
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }

    TextInputDialog(
        title = if (intoGroup != null) "Add source to \"${intoGroup.name}\"" else "Add source",
        label = "Feed, website or Nostr npub",
        placeholder = "example.com/feed.xml",
        confirmLabel = "Add",
        keyboardType = KeyboardType.Uri,
        validate = { input ->
            val url = FeedUrl.normalize(input)
            val key = url?.let(FeedUrl::key)
            when {
                url == null -> "Enter a web address or a Nostr public key (npub1…)"
                intoGroup != null && key in groupKeys -> "This source is already in this group"
                intoGroup == null && key in existingKeys -> "This source is already added"
                else -> null
            }
        },
        onDismiss = onDismiss,
        onConfirm = { input ->
            val url = FeedUrl.normalize(input) ?: return@TextInputDialog
            onAdd(url, if (intoGroup != null) setOf(intoGroup.id) else selected)
        },
    ) {
        if (intoGroup != null) {
            Text(
                "Paste the address of an RSS or Atom feed, or of a website that has one. " +
                    "Or someone's Nostr public key (npub1…), to follow what they share.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (groups.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Add to groups", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            GroupCheckList(
                groups = groups,
                selected = selected,
                onToggle = { groupId, checked ->
                    selected = if (checked) selected + groupId else selected - groupId
                },
            )
        }
    }
}
