package com.phisherman.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.phisherman.data.Prefs

/** Phisherman brand colours, the same on every phone rather than wallpaper colours. */
object Brand {
    val Navy = Color(0xFF0B1630)
    val Teal = Color(0xFF0EA5A4)
    val Mint = Color(0xFF3DDC97)
    val Safe = Color(0xFF16A34A)
    val Warn = Color(0xFFF59E0B)
    val Danger = Color(0xFFE5484D)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * App-wide appearance and language, shared by every activity so a change in Settings applies
 * everywhere at once. Loaded from Prefs in PhishermanApp.onCreate.
 */
object AppSettings {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var hindi by mutableStateOf(false)

    fun load(ctx: Context) {
        themeMode = runCatching { ThemeMode.valueOf(Prefs.themeMode(ctx)) }.getOrDefault(ThemeMode.SYSTEM)
        hindi = Prefs.hindi(ctx)
    }

    fun setTheme(ctx: Context, mode: ThemeMode) { themeMode = mode; Prefs.setThemeMode(ctx, mode.name) }
    fun setHindi(ctx: Context, on: Boolean) { hindi = on; Prefs.setHindi(ctx, on) }
}

fun heroBrush() = Brush.linearGradient(listOf(Color(0xFF0B1630), Color(0xFF0D2B45), Color(0xFF0B4A4A)))
fun brandBrush() = Brush.horizontalGradient(listOf(Color(0xFF12B981), Color(0xFF0EA5A4)))

private val Light = lightColorScheme(
    primary = Color(0xFF0FA968), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E7), onPrimaryContainer = Color(0xFF05321F),
    secondary = Brand.Teal, onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF3EE), onSecondaryContainer = Color(0xFF053B35),
    background = Color(0xFFF3F5FA), onBackground = Color(0xFF0E1526),
    surface = Color.White, onSurface = Color(0xFF0E1526),
    surfaceVariant = Color(0xFFEDF0F6), onSurfaceVariant = Color(0xFF5B6479),
    outline = Color(0xFFC9D0DE), outlineVariant = Color(0xFFE6EAF2),
    error = Brand.Danger, onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Brand.Mint, onPrimary = Color(0xFF0A0F1E),
    primaryContainer = Color(0xFF123A2C), onPrimaryContainer = Color(0xFFBFF5DC),
    secondary = Color(0xFF5ED6C6), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B4A42), onSecondaryContainer = Color(0xFFCFF3EE),
    background = Color(0xFF0A0F1E), onBackground = Color(0xFFE7ECF6),
    surface = Color(0xFF121A2E), onSurface = Color(0xFFE7ECF6),
    surfaceVariant = Color(0xFF1A2440), onSurfaceVariant = Color(0xFF9AA6C0),
    outline = Color(0xFF34406A), outlineVariant = Color(0xFF222C48),
    error = Brand.Danger, onError = Color.White,
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
val LocalIsDark = staticCompositionLocalOf { false }

/** In-app English / Hindi toggle, independent of the system language. */
val LocalHindi = staticCompositionLocalOf { false }

/** Pick the English or Hindi string for the current in-app language. */
@Composable
@ReadOnlyComposable
fun tr(en: String, hi: String): String = if (LocalHindi.current) hi else en

@Composable
fun isAppInDarkTheme(): Boolean = when (AppSettings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PhishermanTheme(darkTheme: Boolean = isAppInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        // status / navigation bar icons follow the app theme, not the system one
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalIsDark provides darkTheme, LocalHindi provides AppSettings.hindi) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = AppTypography, content = content)
    }
}

/** Colours for one risk level, resolved for the current theme. */
data class RiskColors(val fg: Color, val container: Color, val onContainer: Color)

object Risk {
    @Composable @ReadOnlyComposable
    fun safe(dark: Boolean = LocalIsDark.current) = RiskColors(
        Brand.Safe, if (dark) Color(0xFF14321F) else Color(0xFFDCF5E5), if (dark) Color(0xFFBFF0D6) else Color(0xFF0C3D22))

    @Composable @ReadOnlyComposable
    fun suspicious(dark: Boolean = LocalIsDark.current) = RiskColors(
        Brand.Warn, if (dark) Color(0xFF3B2A0C) else Color(0xFFFEF2D7), if (dark) Color(0xFFFFD79A) else Color(0xFF5A3C05))

    @Composable @ReadOnlyComposable
    fun dangerous(dark: Boolean = LocalIsDark.current) = RiskColors(
        Brand.Danger, if (dark) Color(0xFF3A1619) else Color(0xFFFDE3E4), if (dark) Color(0xFFF7B9BB) else Color(0xFF5E1518))
}
