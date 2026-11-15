package com.yoreader.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class BottomTab { LATEST, SAVED, SEARCH }

/**
 * The always-visible bar at the bottom: menu, saved, latest (home) and search, as small
 * icons. [selected] is null on screens that aren't one of the tabs.
 */
@Composable
fun BottomBar(
    selected: BottomTab?,
    onMenu: () -> Unit,
    onSelect: (BottomTab) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.Default.Menu, Icons.Default.Menu, "Menu", selected = false, onClick = onMenu)
                BarIcon(
                    Icons.Default.Bookmarks, Icons.Outlined.Bookmarks, "Saved for later",
                    selected = selected == BottomTab.SAVED,
                    onClick = { onSelect(BottomTab.SAVED) },
                )
                BarIcon(
                    Icons.Default.Home, Icons.Outlined.Home, "Latest",
                    selected = selected == BottomTab.LATEST,
                    onClick = { onSelect(BottomTab.LATEST) },
                )
                BarIcon(
                    Icons.Default.Search, Icons.Default.Search, "Search",
                    selected = selected == BottomTab.SEARCH,
                    onClick = { onSelect(BottomTab.SEARCH) },
                )
            }
        }
    }
}

@Composable
private fun BarIcon(
    selectedIcon: ImageVector,
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics {
            this.selected = selected
            role = Role.Tab
        },
    ) {
        Icon(
            if (selected) selectedIcon else icon,
            contentDescription = description,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
