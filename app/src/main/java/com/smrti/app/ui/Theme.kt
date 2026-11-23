package com.smrti.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.smrti.app.data.Prefs

// Brand palette from the logo: sun yellow #FAB62F and deep navy #041237.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFAB62F),            // logo yellow
    onPrimary = Color(0xFF041237),          // logo navy
    secondary = Color(0xFFFFD27A),
    onSecondary = Color(0xFF041237),
    secondaryContainer = Color(0xFF1B2C63),  // selected nav pill
    onSecondaryContainer = Color(0xFFFAB62F),
    background = Color(0xFF041237),
    onBackground = Color(0xFFF6F1E4),
    surface = Color(0xFF0A1A44),
    onSurface = Color(0xFFF6F1E4),
    surfaceVariant = Color(0xFF112457),
    onSurfaceVariant = Color(0xFFA9B4D0),
    surfaceContainerLowest = Color(0xFF030D2B),
    surfaceContainerLow = Color(0xFF081640),
    surfaceContainer = Color(0xFF0A1A44),
    surfaceContainerHigh = Color(0xFF112457),
    surfaceContainerHighest = Color(0xFF182F6B),
    outline = Color(0xFF243B7A),
    outlineVariant = Color(0xFF1A2E66),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5A1F2A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF041237),            // navy reads best as text and buttons
    onPrimary = Color(0xFFFAB62F),          // yellow on navy, like the logo
    secondary = Color(0xFFB57A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBEBC3),  // selected nav pill: a flat, pale yellow
    onSecondaryContainer = Color(0xFF041237),
    background = Color(0xFFFAF8F3),          // calm warm off-white
    onBackground = Color(0xFF041237),
    surface = Color.White,
    onSurface = Color(0xFF041237),
    surfaceVariant = Color(0xFFF3F0E8),
    onSurfaceVariant = Color(0xFF5B6479),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF8F3),
    surfaceContainer = Color(0xFFF7F4EC),
    surfaceContainerHigh = Color.White,      // floating nav bar
    surfaceContainerHighest = Color(0xFFF3F0E8),
    outline = Color(0xFFE6E1D3),             // hairlines instead of shadows
    outlineVariant = Color(0xFFEFEBE0)
)

private val defaultTypography = Typography()
/** A slightly tighter, quieter type scale: small letter-spaced labels, comfortable reading lines. */
private val SmrtiTypography = defaultTypography.copy(
    titleMedium = defaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = defaultTypography.titleSmall.copy(letterSpacing = 0.6.sp),
    labelLarge = defaultTypography.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    bodyLarge = defaultTypography.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = defaultTypography.bodyMedium.copy(lineHeight = 21.sp)
)

/** The theme the user picked. Read by [SmrtiTheme]; changing it recolours the whole app at once. */
object ThemeState {
    var mode by mutableStateOf(Prefs.THEME_DARK)
}

/** Animations are always kept to a minimum (reduced motion is built in, not an option). */
val reduceMotion: Boolean
    get() = true

@Composable
fun isAppDark(): Boolean = when (ThemeState.mode) {
    Prefs.THEME_LIGHT -> false
    Prefs.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun SmrtiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isAppDark()) DarkColors else LightColors, typography = SmrtiTypography, content = content)
}
