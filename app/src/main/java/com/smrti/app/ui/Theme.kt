package com.smrti.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.smrti.app.data.Prefs

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5A94A),            // warm sun orange from the logo
    onPrimary = Color(0xFF1A0F00),
    secondary = Color(0xFFC2693A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1F12),  // selected nav pill
    onSecondaryContainer = Color(0xFFF5A94A),
    background = Color.Black,                // true black
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color(0xFF2E2E2E),
    outlineVariant = Color(0xFF242424)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2620A),            // same orange, deeper so it reads on white
    onPrimary = Color.White,
    secondary = Color(0xFFC2693A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7CC),  // selected nav pill
    onSecondaryContainer = Color(0xFFC2620A),
    background = Color(0xFFFAF7F2),          // warm white
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF1ECE3),
    onSurfaceVariant = Color(0xFF6B6760),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F3EC),
    surfaceContainer = Color(0xFFF3EEE6),
    surfaceContainerHigh = Color(0xFFEFE9DF),
    surfaceContainerHighest = Color(0xFFE9E2D6),
    outline = Color(0xFFD9D2C5),
    outlineVariant = Color(0xFFE6DFD2)
)

/** The theme the user picked. Read by [SmrtiTheme]; changing it recolours the whole app at once. */
object ThemeState {
    var mode by mutableStateOf(Prefs.THEME_DARK)
}

@Composable
fun isAppDark(): Boolean = when (ThemeState.mode) {
    Prefs.THEME_LIGHT -> false
    Prefs.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun SmrtiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isAppDark()) DarkColors else LightColors, content = content)
}
