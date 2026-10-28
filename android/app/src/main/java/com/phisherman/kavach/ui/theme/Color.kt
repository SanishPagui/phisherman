package com.phisherman.kavach.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---- Brand ----
val Mint = Color(0xFF3DDC97)
val MintDark = Color(0xFF17B077)
val Navy = Color(0xFF0B1020)
val NavySurface = Color(0xFF131A2E)
val NavyElevated = Color(0xFF1B2440)

// ---- Semantic risk colours (consistent in both themes) ----
val SafeGreen = Color(0xFF30A46C)
val SafeGreenSoft = Color(0xFF1E3A2E)
val AmberWarn = Color(0xFFFFB224)
val AmberSoft = Color(0xFF402D10)
val DangerRed = Color(0xFFE5484D)
val DangerSoft = Color(0xFF3A1619)

val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Navy,
    primaryContainer = MintDark,
    onPrimaryContainer = Color(0xFFEAFBF3),
    secondary = Color(0xFF8CA0C6),
    onSecondary = Navy,
    background = Navy,
    onBackground = Color(0xFFE7ECF6),
    surface = NavySurface,
    onSurface = Color(0xFFE7ECF6),
    surfaceVariant = NavyElevated,
    onSurfaceVariant = Color(0xFFA9B4CC),
    outline = Color(0xFF34406A),
    error = DangerRed,
    onError = Color.White,
)

val LightColors = lightColorScheme(
    primary = MintDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF4E6),
    onPrimaryContainer = Color(0xFF05321F),
    secondary = Color(0xFF42527A),
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF131A2E),
    surface = Color.White,
    onSurface = Color(0xFF131A2E),
    surfaceVariant = Color(0xFFEBEEF6),
    onSurfaceVariant = Color(0xFF515A72),
    outline = Color(0xFFCAD2E4),
    error = DangerRed,
    onError = Color.White,
)

/** Semantic colours for a risk level, resolved for the current theme. */
data class RiskColors(
    val fg: Color,
    val container: Color,
    val onContainer: Color,
)

object Risk {
    @Composable
    @ReadOnlyComposable
    fun safe(dark: Boolean = LocalIsDark.current) = RiskColors(
        fg = SafeGreen,
        container = if (dark) SafeGreenSoft else Color(0xFFDDF3E8),
        onContainer = if (dark) Color(0xFFBFF0D6) else Color(0xFF0C3D27),
    )

    @Composable
    @ReadOnlyComposable
    fun suspicious(dark: Boolean = LocalIsDark.current) = RiskColors(
        fg = AmberWarn,
        container = if (dark) AmberSoft else Color(0xFFFFF0D6),
        onContainer = if (dark) Color(0xFFFFD79A) else Color(0xFF5A3C05),
    )

    @Composable
    @ReadOnlyComposable
    fun dangerous(dark: Boolean = LocalIsDark.current) = RiskColors(
        fg = DangerRed,
        container = if (dark) DangerSoft else Color(0xFFFCE0E1),
        onContainer = if (dark) Color(0xFFF7B9BB) else Color(0xFF5E1518),
    )
}
