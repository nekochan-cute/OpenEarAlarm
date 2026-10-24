// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Deep teal from the launcher icon, on warm paper by day and blue-black by night.
private val Light = lightColorScheme(
    primary = Color(0xFF175D65), onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE8E7), onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4D6362), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6E4), onSecondaryContainer = Color(0xFF0A1F1E),
    tertiary = Color(0xFFA25B12), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF331200),
    background = Color(0xFFF8F6F1), onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFF8F6F1), onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE7E4DD), onSurfaceVariant = Color(0xFF5D605D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F1EC),
    surfaceContainer = Color(0xFFEDEBE6), surfaceContainerHigh = Color(0xFFE7E5E0), surfaceContainerHighest = Color(0xFFE1DFDA),
    outline = Color(0xFF8A8C88), outlineVariant = Color(0xFFD7D5CF),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86D1CD), onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF12474B), onPrimaryContainer = Color(0xFFCBE8E7),
    secondary = Color(0xFFB4CBC9), onSecondary = Color(0xFF1F3534),
    secondaryContainer = Color(0xFF263534), onSecondaryContainer = Color(0xFFD0E6E4),
    tertiary = Color(0xFFFFB877), onTertiary = Color(0xFF4F2500),
    tertiaryContainer = Color(0xFF6E3800), onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF0D1214), onBackground = Color(0xFFE2E4E3),
    surface = Color(0xFF0D1214), onSurface = Color(0xFFE2E4E3),
    surfaceVariant = Color(0xFF283033), onSurfaceVariant = Color(0xFFA3ABAC),
    surfaceContainerLowest = Color(0xFF080C0D), surfaceContainerLow = Color(0xFF131A1C),
    surfaceContainer = Color(0xFF172022), surfaceContainerHigh = Color(0xFF1F282A), surfaceContainerHighest = Color(0xFF283234),
    outline = Color(0xFF737C7D), outlineVariant = Color(0xFF2E383A),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410), errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFeatureSettings = "tnum"),
)

/** Alarm times in lists. */
val ListTimeStyle = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum")
/** The clock on the ringing screen and the time in the editor. */
val ClockStyle = TextStyle(fontSize = 88.sp, lineHeight = 96.sp, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = "tnum")

@Composable
internal fun OpenEarTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(28.dp)),
        content = content,
    )
}
