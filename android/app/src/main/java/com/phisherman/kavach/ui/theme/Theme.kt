package com.phisherman.kavach.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** True when the current theme is dark — used by the semantic risk palette. */
val LocalIsDark = staticCompositionLocalOf { true }

/** In-app language toggle (English / Hindi), independent of the system locale. */
val LocalHindi = compositionLocalOf { false }

@Composable
fun PhishermanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    hindi: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val light = colors.background.luminance() > 0.5f
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsDark provides darkTheme,
        LocalHindi provides hindi,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content,
        )
    }
}
