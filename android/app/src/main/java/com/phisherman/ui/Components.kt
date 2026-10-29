package com.phisherman.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phisherman.engine.Level
import com.phisherman.ui.theme.Risk
import com.phisherman.ui.theme.RiskColors
import com.phisherman.ui.theme.brandBrush
import com.phisherman.ui.theme.tr

@Composable
@ReadOnlyComposable
fun levelColors(level: Level): RiskColors = when (level) {
    Level.SAFE -> Risk.safe()
    Level.SUSPICIOUS -> Risk.suspicious()
    Level.DANGEROUS -> Risk.dangerous()
}

@Composable
@ReadOnlyComposable
fun levelLabel(level: Level): String = when (level) {
    Level.SAFE -> tr("Safe", "सुरक्षित")
    Level.SUSPICIOUS -> tr("Suspicious", "संदिग्ध")
    Level.DANGEROUS -> tr("Dangerous", "ख़तरनाक")
}

fun levelIcon(level: Level): ImageVector = when (level) {
    Level.SAFE -> Icons.Rounded.GppGood
    Level.SUSPICIOUS -> Icons.Rounded.GppMaybe
    Level.DANGEROUS -> Icons.Rounded.GppBad
}

/** Rounded surface card with a hairline border. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(container)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(padding),
        content = content,
    )
}

/** Large page title at the top of a tab. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** Small uppercase section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 4.dp, top = 10.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Rounded square tile holding an icon. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier,
             background: Color = tint.copy(alpha = 0.14f), size: Dp = 44.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/** Full-width gradient primary button. */
@Composable
fun GradientButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false,
    brush: Brush = brandBrush(), height: Dp = 54.dp,
) {
    val fg = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) brush else SolidColor(MaterialTheme.colorScheme.surfaceVariant))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.White)
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Secondary outlined button. */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                  icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(shape).border(1.dp, color.copy(alpha = 0.35f), shape).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

/** Green "On" chip. */
@Composable
fun OnChip(label: String = tr("On", "चालू")) {
    val c = Risk.safe()
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.container).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, null, tint = c.fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.onContainer)
    }
}

/** Compact gradient pill action ("Turn on"). */
@Composable
fun PillAction(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(brandBrush()).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Animated shield orb with soft expanding rings. */
@Composable
fun ShieldOrb(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 84.dp, iconTint: Color = Color.White) {
    val t by rememberInfiniteTransition(label = "orb").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = this.size.minDimension / 2f
            for (i in 0..1) {
                val p = (t + i * 0.5f) % 1f
                drawCircle(color.copy(alpha = 0.35f * (1f - p)), radius = base * (0.62f + 0.38f * p))
            }
        }
        Box(Modifier.size(size * 0.62f).clip(CircleShape).background(Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color))),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(size * 0.32f))
        }
    }
}

/** Level pill with a coloured dot. */
@Composable
fun LevelBadge(level: Level, modifier: Modifier = Modifier) {
    val c = levelColors(level)
    Row(modifier.clip(RoundedCornerShape(50)).background(c.container).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c.fg))
        Spacer(Modifier.width(6.dp))
        Text(levelLabel(level).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.onContainer)
    }
}

/** One reason with a coloured marker. */
@Composable
fun ReasonRow(text: String, dot: Color, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(dot.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}

/** Labelled horizontal bar for a 0..1 value. */
@Composable
fun ValueBar(label: String, value: Double, display: String, color: Color) {
    val animated by animateFloatAsState(value.toFloat().coerceIn(0f, 1f), tween(800), label = "bar")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(animated).height(8.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/** Segmented control: one option selected, the selected one filled with the brand colour. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, icons: List<ImageVector>? = null) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (on) cs.surface else Color.Transparent)
                    .clickable { onSelect(value) }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                icons?.getOrNull(i)?.let {
                    Icon(it, null, tint = if (on) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onSurface else cs.onSurfaceVariant)
            }
        }
    }
}

/** Icon + title + subtitle row with something on the right (switch, chip, chevron). */
@Composable
fun SettingRow(
    icon: ImageVector, title: String, subtitle: String? = null, tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint, size = 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** Coloured notice card (warning reminder, engine error, background limits). */
@Composable
fun NoticeCard(level: Level, icon: ImageVector, title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    val c = levelColors(level)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.container).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, Color.White, background = c.fg, size = 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.onContainer)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.onContainer.copy(alpha = 0.85f))
        }
        action?.let { (label, onClick) ->
            Text(label, Modifier.clip(RoundedCornerShape(50)).background(c.fg).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}