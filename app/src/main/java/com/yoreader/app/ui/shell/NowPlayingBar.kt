package com.yoreader.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yoreader.app.tts.ArticleSpeaker
import com.yoreader.app.tts.SpeechRate

/**
 * Strip above the bottom bar while an article is being read aloud: title and progress
 * (tap to open the article), speed − / +, pause/resume and stop.
 */
@Composable
fun NowPlayingBar(
    state: ArticleSpeaker.State,
    rate: Float,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
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
                            ArticleSpeaker.Status.PAUSED -> "Paused · ${state.position + 1}/${state.total}"
                            else -> "Reading · ${state.position + 1}/${state.total}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                // Speed: − 1.25× +
                IconButton(
                    onClick = onSlower,
                    enabled = !SpeechRate.isSlowest(rate),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Slower")
                }
                Text(
                    SpeechRate.label(rate),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 40.dp)
                        .semantics { contentDescription = "Speed ${SpeechRate.label(rate)}" },
                )
                IconButton(
                    onClick = onFaster,
                    enabled = !SpeechRate.isFastest(rate),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Faster")
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
