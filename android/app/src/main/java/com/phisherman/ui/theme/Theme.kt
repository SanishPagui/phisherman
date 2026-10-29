package com.phisherman.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Brand ----
val Mint = Color(0xFF3DDC97)
val MintDark = Color(0xFF0FA968)
private val Navy = Color(0xFF0A0F1E)

fun heroBrush() = Brush.linearGradient(listOf(Color(0xFF0B1630), Color(0xFF0D2B45), Color(0xFF0B4A4A)))
fun brandBrush() = Brush.horizontalGradient(listOf(Color(0xFF12B981), Color(0xFF0EA5A4)))

// ---- Risk colours ----
val SafeGreen = Color(0xFF16A34A)
val AmberWarn = Color(0xFFF59E0B)
val DangerRed = Color(0xFFE5484D)

private val DarkColors = darkColorScheme(
    primary = Mint, onPrimary = Navy,
    primaryContainer = Color(0xFF123A2C), onPrimaryContainer = Color(0xFFBFF5DC),
    secondary = Color(0xFF8CA0C6), onSecondary = Navy,
    background = Navy, onBackground = Color(0xFFE7ECF6),
    surface = Color(0xFF121A2E), onSurface = Color(0xFFE7ECF6),
    surfaceVariant = Color(0xFF1A2440), onSurfaceVariant = Color(0xFF9AA6C0),
    outline = Color(0xFF34406A), outlineVariant = Color(0xFF222C48),
    error = DangerRed, onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = MintDark, onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E7), onPrimaryContainer = Color(0xFF05321F),
    secondary = Color(0xFF42527A), onSecondary = Color.White,
    background = Color(0xFFF3F5FA), onBackground = Color(0xFF0E1526),
    surface = Color.White, onSurface = Color(0xFF0E1526),
    surfaceVariant = Color(0xFFEDF0F6), onSurfaceVariant = Color(0xFF5B6479),
    outline = Color(0xFFC9D0DE), outlineVariant = Color(0xFFE6EAF2),
    error = DangerRed, onError = Color.White,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

/** True in the dark theme; used by the risk palette. */
val LocalIsDark = staticCompositionLocalOf { true }

/** In-app English / Hindi toggle, independent of the system language. */
val LocalHindi = staticCompositionLocalOf { false }

/** Pick the English or Hindi string for the current in-app language. */
@Composable
@ReadOnlyComposable
fun tr(en: String, hi: String): String = if (LocalHindi.current) hi else en

@Composable
fun PhishermanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    hindi: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIsDark provides darkTheme, LocalHindi provides hindi) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Colours for one risk level, resolved for the current theme. */
data class RiskColors(val fg: Color, val container: Color, val onContainer: Color)

object Risk {
    @Composable @ReadOnlyComposable
    fun safe(dark: Boolean = LocalIsDark.current) = RiskColors(
        SafeGreen, if (dark) Color(0xFF14321F) else Color(0xFFDCF5E5), if (dark) Color(0xFFBFF0D6) else Color(0xFF0C3D22))

    @Composable @ReadOnlyComposable
    fun suspicious(dark: Boolean = LocalIsDark.current) = RiskColors(
        AmberWarn, if (dark) Color(0xFF3B2A0C) else Color(0xFFFEF2D7), if (dark) Color(0xFFFFD79A) else Color(0xFF5A3C05))

    @Composable @ReadOnlyComposable
    fun dangerous(dark: Boolean = LocalIsDark.current) = RiskColors(
        DangerRed, if (dark) Color(0xFF3A1619) else Color(0xFFFDE3E4), if (dark) Color(0xFFF7B9BB) else Color(0xFF5E1518))
}
