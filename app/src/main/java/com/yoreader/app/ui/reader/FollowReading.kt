package com.yoreader.app.ui.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yoreader.app.tts.ArticleSpeaker

/**
 * Following the text read aloud on screen. [reading] is where reading is in the page
 * shown, or null when this page isn't being read. Scrolling by hand stops following
 * ([stop]); each new start of reading, or [resume] (the Follow button), turns it back on.
 */
internal class FollowState(val reading: ReadingSpot?, val following: Boolean, val stop: () -> Unit, val resume: () -> Unit) {
    /** Show the Follow button: reading here, but the user scrolled away. */
    val offerFollow get() = reading != null && !following
}

/** [readingHere]: the page on screen is the one being read (same version, too). */
@Composable
internal fun rememberFollowState(speech: ArticleSpeaker.State, readingHere: Boolean, pageKey: Any?): FollowState {
    var following by remember(pageKey) { mutableStateOf(true) }
    val active = readingHere && speech.isActive
    LaunchedEffect(active) { if (active) following = true }
    return FollowState(
        reading = if (active) ReadingSpot(speech.paragraph, speech.paragraphProgress) else null,
        following = following,
        stop = { if (active) following = false },
        resume = { following = true },
    )
}

@Composable
internal fun FollowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
        text = { Text("Follow") },
        modifier = modifier,
    )
}
