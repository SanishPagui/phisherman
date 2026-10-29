package com.phisherman.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Phisherman brand colours (navy + teal), the same on every phone rather than wallpaper colours. */
object Brand {
    val Navy = Color(0xFF0F3A68)
    val Teal = Color(0xFF0E9F8F)
    val Safe = Color(0xFF1E8E5A)
    val Warn = Color(0xFFD08A00)
    val Danger = Color(0xFFC62828)
}

private val Light = lightColorScheme(
    primary = Brand.Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6F7), onPrimaryContainer = Color(0xFF0A2540),
    secondary = Brand.Teal, onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF3EE), onSecondaryContainer = Color(0xFF053B35),
    background = Color(0xFFF4F7FB), onBackground = Color(0xFF14202E),
    surface = Color.White, onSurface = Color(0xFF14202E),
    surfaceVariant = Color(0xFFE9EEF5), onSurfaceVariant = Color(0xFF4A5868),
    error = Brand.Danger, outline = Color(0xFFC3CDD9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8EC1FF), onPrimary = Color(0xFF06223F),
    primaryContainer = Color(0xFF173A5E), onPrimaryContainer = Color(0xFFD8E6F7),
    secondary = Color(0xFF5ED6C6), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B4A42), onSecondaryContainer = Color(0xFFCFF3EE),
    background = Color(0xFF0D1520), onBackground = Color(0xFFE3EAF3),
    surface = Color(0xFF16212E), onSurface = Color(0xFFE3EAF3),
    surfaceVariant = Color(0xFF1F2C3B), onSurfaceVariant = Color(0xFFA9B6C6),
    error = Color(0xFFFF8A80), outline = Color(0xFF3A4A5C),
)

@Composable
fun PhishermanTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    // always dark, so every screen looks the same; the Surface gives text its light default colour
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
