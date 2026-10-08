package com.mariotatis.lockcontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B0D2A),
    primaryContainer = AccentDeep,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    secondaryContainer = PanelHigh,
    onSecondaryContainer = Color.White,
    background = Ink,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHigh,
    outline = Outline,
    outlineVariant = Outline,
)

@Composable
fun LockControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography, content = content)
}
