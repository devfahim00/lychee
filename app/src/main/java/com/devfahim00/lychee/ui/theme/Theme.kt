package com.devfahim00.lychee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Lychee palette — juicy pink on deep plum glass
val LycheePink = Color(0xFFFF5C8A)
val LycheePinkLight = Color(0xFFFF8FB1)
val LycheeViolet = Color(0xFFB14CFF)
val LycheeVioletDeep = Color(0xFF7C4DFF)
val LycheeCoral = Color(0xFFFF8A65)
val LycheeMint = Color(0xFF6DFFB8)
val LycheeRed = Color(0xFFFF6B81)
val GlassWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF6F0FA)
val TextSecondary = Color(0xFFC9BEDA)
val TextTertiary = Color(0xFF9A8FB0)
val BgTop = Color(0xFF0E0A1A)
val BgBottom = Color(0xFF1A0F2E)
val SuccessGreen = Color(0xFF5EE6A8)

private val LycheeColorScheme = darkColorScheme(
    primary = LycheePink,
    onPrimary = Color.White,
    primaryContainer = LycheeVioletDeep,
    onPrimaryContainer = Color.White,
    secondary = LycheeViolet,
    onSecondary = Color.White,
    tertiary = LycheeCoral,
    onTertiary = Color.White,
    background = BgTop,
    onBackground = TextPrimary,
    surface = Color(0xFF171126),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF221A35),
    onSurfaceVariant = TextSecondary,
    error = LycheeRed,
    onError = Color.White,
    outline = TextTertiary,
    outlineVariant = Color(0x33FFFFFF)
)

@Composable
fun LycheeTheme(content: @Composable () -> Unit) {
    // Lychee is always dark & glassy
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = LycheeColorScheme,
        content = content
    )
}
