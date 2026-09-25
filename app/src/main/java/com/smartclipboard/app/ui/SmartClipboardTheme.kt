package com.smartclipboard.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1769D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEAF0F8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC4FF),
    onPrimary = Color(0xFF00336C),
    primaryContainer = Color(0xFF174B91),
    background = Color(0xFF10141C),
    surface = Color(0xFF1B202A),
    surfaceVariant = Color(0xFF283142)
)

@Composable
fun SmartClipboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
