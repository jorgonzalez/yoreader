package com.yoreader.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yoreader.app.tts.ArticleSpeaker

/** Strip above the bottom bar while an article is being read aloud. Tap to open it. */
@Composable
fun NowPlayingBar(
    state: ArticleSpeaker.State,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
) {
    val speaking = state.status == ArticleSpeaker.Status.SPEAKING ||
        state.status == ArticleSpeaker.Status.PREPARING
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else (state.position + 1f) / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (state.status) {
                            ArticleSpeaker.Status.PREPARING -> "Starting…"
                            ArticleSpeaker.Status.PAUSED -> "Paused · part ${state.position + 1} of ${state.total}"
                            else -> "Reading · part ${state.position + 1} of ${state.total}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (speaking) "Pause reading" else "Resume reading",
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Close, contentDescription = "Stop reading")
                }
            }
        }
    }
}
