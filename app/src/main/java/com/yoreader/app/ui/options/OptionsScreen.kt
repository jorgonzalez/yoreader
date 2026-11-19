package com.yoreader.app.ui.options

import android.content.ActivityNotFoundException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.OutlinedButton
import com.yoreader.app.sync.LaptopSync
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

/** The laptop page (web/ in the repo, published with GitHub Pages). */
const val WEB_URL = "https://jorgonzalez.github.io/yoreader/"

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
    sync: LaptopSync.State,
    onUseSyncFile: (Uri) -> Unit,
    onSyncNow: () -> Unit,
    onStopSync: () -> Unit,
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
                "Used in article lists, the reader and cleaned pages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            HorizontalDivider(Modifier.padding(top = 16.dp))
            SectionTitle("Read-aloud languages")
            ReadAloudLanguages(voices, enabledLanguages, onSetLanguage)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("Laptop")
            LaptopSyncSection(sync, onUseSyncFile, onSyncNow, onStopSync)

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
            val active = voices.filter { it.id in enabled }
            val inactive = voices.filter { it.id !in enabled }
            active.forEach { lang ->
                LanguageSwitch(lang, on = true, locked = active.size == 1, onSet = onSet)
            }
            if (inactive.isNotEmpty()) {
                var open by rememberSaveable { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open = !open }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "More languages (${inactive.size})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (open) "Hide" else "Show",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                AnimatedVisibility(visible = open) {
                    Column {
                        inactive.forEach { lang ->
                            LanguageSwitch(lang, on = false, locked = false, onSet = onSet)
                        }
                    }
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

/**
 * Sync with the laptop page through a file in Google Drive (or any storage app in the
 * file picker): create or choose it here, then choose the same file on the laptop.
 */
@Composable
private fun LaptopSyncSection(
    sync: LaptopSync.State,
    onUseFile: (Uri) -> Unit,
    onSyncNow: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(onUseFile)
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(onUseFile)
    }
    if (sync.fileUri == null) {
        Text(
            "Read on your laptop too: keep a sync file in Google Drive, then open the " +
                "yoreader page on the laptop and choose the same file there. Read and " +
                "saved articles stay the same on both.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp)) {
            OutlinedButton(onClick = { create.launch(SYNC_FILE_NAME) }) { Text("Create sync file") }
            TextButton(
                onClick = { choose.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Use existing file") }
        }
    } else {
        val detail = when {
            sync.running -> "Syncing…"
            sync.error != null -> sync.error
            sync.lastSyncAt != null && System.currentTimeMillis() - sync.lastSyncAt < DateUtils.MINUTE_IN_MILLIS ->
                "Synced just now"
            sync.lastSyncAt != null -> "Synced " + DateUtils.getRelativeTimeSpanString(
                sync.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            )
            else -> "Not synced yet"
        }
        OptionRow(
            icon = { Icon(Icons.Default.Sync, contentDescription = null) },
            title = sync.fileName ?: "Sync file",
            detail = detail,
            onClick = onSyncNow,
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onSyncNow, enabled = !sync.running) { Text("Sync now") }
            TextButton(onClick = onStop) { Text("Stop syncing") }
        }
    }
    OptionRow(
        icon = { Icon(Icons.Default.Laptop, contentDescription = null) },
        title = "Laptop page",
        detail = WEB_URL.removePrefix("https://").removeSuffix("/") + " · Chrome, Edge or Brave",
        trailing = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, Modifier.size(20.dp))
        },
        onClick = { openExternal(context, WEB_URL) },
    )
}

private const val SYNC_FILE_NAME = "yoreader-sync.json"

/** One read-aloud language with its switch; [locked]: the last one on can't go off. */
@Composable
private fun LanguageSwitch(
    lang: TtsLanguage,
    on: Boolean,
    locked: Boolean,
    onSet: (id: String, on: Boolean) -> Unit,
) {
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
