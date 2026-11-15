package com.yoreader.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// yoreader is dark-only by design: no light scheme, no dynamic color.
private val YoDarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Outline,
    error = ErrorRed,
)

@Composable
fun YoReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YoDarkColors,
        content = content,
    )
}
