package app.expensetracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Colours used across the app. Dark is true black for AMOLED screens; light follows the phone setting. */
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val pink: Color,
    val cyan: Color,
    val good: Color,
    val bad: Color,
    val hero: Brush,
    val dark: Boolean,
)

private val heroBrush = Brush.linearGradient(listOf(Color(0xFF5B34FF), Color(0xFFB23CFF), Color(0xFFFF4F8F)))

val DarkPalette = Palette(
    bg = Color(0xFF000000), surface = Color(0xFF0E0E15), surface2 = Color(0xFF1A1A25), line = Color(0x17FFFFFF),
    fg = Color(0xFFF7F6FB), muted = Color(0xFF9A98AD), accent = Color(0xFF9B6BFF), pink = Color(0xFFFF4FD8),
    cyan = Color(0xFF2EF2E0), good = Color(0xFF2EF2E0), bad = Color(0xFFFF5C7A), hero = heroBrush, dark = true,
)

val LightPalette = Palette(
    bg = Color(0xFFF6F3FF), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEFEBFB), line = Color(0x17141228),
    fg = Color(0xFF16141F), muted = Color(0xFF6B6980), accent = Color(0xFF6B3CFF), pink = Color(0xFFE0309F),
    cyan = Color(0xFF0A9C9C), good = Color(0xFF0A8F8F), bad = Color(0xFFD63A4F), hero = heroBrush, dark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Shortcut for reading the current palette: `Pal.accent`. */
val Pal: Palette
    @Composable get() = LocalPalette.current

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.fg,
            surface = p.surface, onSurface = p.fg, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
            surfaceContainerHigh = p.surface, surfaceContainerLow = p.surface, outline = p.line,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.fg,
            surface = p.surface, onSurface = p.fg, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
            surfaceContainerHigh = p.surface, surfaceContainerLow = p.surface, outline = p.line,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}

val ChartColors = listOf(
    Color(0xFFFF5C7A), Color(0xFFB6FF5C), Color(0xFF2EF2E0), Color(0xFFB18CFF),
    Color(0xFFFFB938), Color(0xFFFF4FD8), Color(0xFF4DA3FF), Color(0xFFB0AEC4),
)
