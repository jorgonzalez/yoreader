package com.yoreader.app.ui.options

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Switch
import com.yoreader.app.tts.TtsLanguage
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.yoreader.app.data.ArticleRetention
import com.yoreader.app.BuildConfig
import com.yoreader.app.settings.TextZoom
import com.yoreader.app.ui.reader.ArticleHtml
import com.yoreader.app.ui.reader.ArticleWebView
import com.yoreader.app.ui.reader.openExternal
import kotlin.math.roundToInt

const val REPO_URL = "https://github.com/jorgonzalez/yoreader"

/**
 * Options, opened from the side menu: article text size (with a live preview drawn
 * exactly like the reader), read-aloud languages, how long read articles are kept, and
 * about the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    textZoom: Int,
    onSetTextZoom: (Int) -> Unit,
    keepReadDays: Int,
    onSetKeepReadDays: (Int) -> Unit,
    /** Read-aloud languages this phone has a voice for (null: still checking). */
    voices: List<TtsLanguage>?,
    enabledLanguages: Set<String>,
    onSetLanguage: (id: String, on: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var choosingKeep by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Options") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Reading")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            ) {
                Icon(Icons.Default.TextFields, contentDescription = null)
                Text(
                    "Article text size",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
                Text("$textZoom%", style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = { onSetTextZoom(TextZoom.DEFAULT) },
                    enabled = textZoom != TextZoom.DEFAULT,
                ) { Text("Reset") }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("A", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = textZoom.toFloat(),
                    onValueChange = { onSetTextZoom(it.roundToInt()) },
                    valueRange = TextZoom.MIN.toFloat()..TextZoom.MAX.toFloat(),
                    steps = TextZoom.STEPS_BETWEEN,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text("A", style = MaterialTheme.typography.headlineSmall)
            }
            // The preview is the reader's own page, so it shows the real result.
            val sample = remember { ArticleHtml.sample() }
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            ) {
                ArticleWebView(html = sample, baseUrl = REPO_URL, onLinkClick = {})
            }
            Text(
                "Used in the reader and in cleaned pages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            HorizontalDivider(Modifier.padding(top = 16.dp))
            SectionTitle("Read-aloud languages")
            ReadAloudLanguages(voices, enabledLanguages, onSetLanguage)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("Storage")
            OptionRow(
                icon = { Icon(Icons.Default.AutoDelete, contentDescription = null) },
                title = "Remove read articles",
                detail = keepLabel(keepReadDays) + " · saved articles are always kept",
                onClick = { choosingKeep = true },
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("About")
            OptionRow(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                title = "yoreader ${BuildConfig.VERSION_NAME}",
                detail = "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.GIT_COMMIT} · GPL-3.0 license",
            )
            OptionRow(
                icon = { Icon(Icons.Default.Code, contentDescription = null) },
                title = "Source code on GitHub",
                detail = REPO_URL.removePrefix("https://"),
                trailing = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, Modifier.size(20.dp))
                },
                onClick = { openExternal(context, REPO_URL) },
            )
        }
    }

    if (choosingKeep) {
        KeepReadDialog(
            current = keepReadDays,
            onPick = {
                onSetKeepReadDays(it)
                choosingKeep = false
            },
            onDismiss = { choosingKeep = false },
        )
    }
}

/**
 * A switch per language with a voice on this phone, sorted by name. The languages
 * turned on are the ones articles are detected in and read with; the last one can't be
 * turned off.
 */
@Composable
private fun ReadAloudLanguages(
    voices: List<TtsLanguage>?,
    enabled: Set<String>,
    onSet: (id: String, on: Boolean) -> Unit,
) {
    val context = LocalContext.current
    Text(
        "Articles are read in the language they're written in, among the ones turned on " +
            "here. Only languages with a voice on this phone are listed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
    when {
        voices == null -> Text(
            "Checking the phone's voices…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        voices.isEmpty() -> Text(
            "No voices found. Install a text-to-speech engine or voices in Android Settings.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        else -> {
            val onCount = voices.count { it.id in enabled }
            voices.forEach { lang ->
                val on = lang.id in enabled
                val locked = on && onCount == 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = on, enabled = !locked, role = Role.Switch) { onSet(lang.id, it) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        lang.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = on, onCheckedChange = null, enabled = !locked)
                }
            }
        }
    }
    TextButton(
        onClick = { openTtsSettings(context) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, Modifier.size(18.dp))
        Text("Add voices in Android settings", modifier = Modifier.padding(start = 8.dp))
    }
}

/** Android's text-to-speech settings (engine, voice downloads), or general settings. */
private fun openTtsSettings(context: Context) {
    for (action in listOf("com.android.settings.TTS_SETTINGS", Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // try the next one
        }
    }
}

private fun keepLabel(days: Int): String = when (days) {
    0 -> "Never"
    365 -> "After 1 year"
    else -> "After $days days"
}

/** Picks how long read articles are kept ([ArticleRetention.CHOICES]). */
@Composable
private fun KeepReadDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove read articles") },
        text = {
            Column {
                Text(
                    "Read articles older than this are removed from the phone. " +
                        "Saved and unread articles are always kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ArticleRetention.CHOICES.forEach { days ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = days == current, role = Role.RadioButton) { onPick(days) },
                    ) {
                        RadioButton(selected = days == current, onClick = null)
                        Text(
                            keepLabel(days) + if (days == ArticleRetention.DEFAULT_DAYS) " (default)" else "",
                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun OptionRow(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        icon()
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}
