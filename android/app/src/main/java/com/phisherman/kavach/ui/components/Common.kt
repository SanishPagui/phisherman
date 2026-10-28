package com.phisherman.kavach.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.ui.Strings
import com.phisherman.kavach.ui.theme.Risk
import com.phisherman.kavach.ui.theme.RiskColors

@Composable
@ReadOnlyComposable
fun levelColors(level: Level): RiskColors = when (level) {
    Level.SAFE -> Risk.safe()
    Level.SUSPICIOUS -> Risk.suspicious()
    Level.DANGEROUS -> Risk.dangerous()
}

fun levelLabel(level: Level, s: Strings): String = when (level) {
    Level.SAFE -> s.safe
    Level.SUSPICIOUS -> s.suspicious
    Level.DANGEROUS -> s.dangerous
}

/** Circular 0–100 risk gauge with an animated sweep. */
@Composable
fun RiskMeter(
    score: Int,
    level: Level,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 148.dp,
) {
    val colors = levelColors(level)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(700),
        label = "meter",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val inset = 8.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke,
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(colors.fg.copy(alpha = 0.7f), colors.fg)),
                startAngle = 135f, sweepAngle = 270f * animated, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colors.fg,
            )
            Text(
                text = "/ 100",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Compact level pill. */
@Composable
fun LevelBadge(level: Level, label: String, modifier: Modifier = Modifier) {
    val colors = levelColors(level)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A single reason bullet with a coloured dot. */
@Composable
fun ReasonRow(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(dot),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Thin linear meter used in list rows. */
@Composable
fun MiniMeter(score: Int, level: Level, modifier: Modifier = Modifier) {
    val colors = levelColors(level)
    Box(
        modifier
            .width(56.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(score.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.fg),
        )
    }
}
