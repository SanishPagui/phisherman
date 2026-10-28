package com.phisherman.kavach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.ui.Strings
import com.phisherman.kavach.ui.UiVerdict
import com.phisherman.kavach.ui.components.LevelBadge
import com.phisherman.kavach.ui.components.ReasonRow
import com.phisherman.kavach.ui.components.RiskMeter
import com.phisherman.kavach.ui.components.levelColors
import com.phisherman.kavach.ui.components.levelLabel
import com.phisherman.kavach.ui.strings

@Composable
fun WarningScreen(
    verdict: UiVerdict,
    onGoBack: () -> Unit,
    onOpenAnyway: () -> Unit,
    onReport: () -> Unit,
) {
    val s = strings()
    val colors = levelColors(verdict.level)
    var confirm by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            RiskMeter(score = verdict.score, level = verdict.level)
            Spacer(Modifier.height(12.dp))
            LevelBadge(verdict.level, levelLabel(verdict.level, s))
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verdict.level == Level.DANGEROUS) Icons.Rounded.GppMaybe else Icons.Rounded.Shield,
                    contentDescription = null, tint = colors.fg, modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = if (verdict.level == Level.DANGEROUS) s.dangerousLinkBlocked else s.linkLooksUnsafe,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(Modifier.height(16.dp))
            HostCard(verdict.subject)
            Spacer(Modifier.height(8.dp))
            Text(
                s.estimatedRisk,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            ReasonsCard(verdict, s)

            Spacer(Modifier.height(24.dp))
            // Primary, safest action: biggest, highest-contrast, one tap (PS4 7.4).
            Button(
                onClick = onGoBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Rounded.ArrowBack, null)
                Spacer(Modifier.size(8.dp))
                Text(s.goBack, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Flag, null)
                Spacer(Modifier.size(8.dp))
                Text(s.reportThis)
            }
            Spacer(Modifier.height(6.dp))
            // Risky action: small, needs a second confirming tap (PS4 7.4).
            TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    s.openAnyway,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.GppMaybe, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(s.openAnywayConfirm) },
            text = { Text(s.openAnywayWarn) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onOpenAnyway() }) {
                    Text(s.openAnyway, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun HostCard(host: String) {
    // Bold the registrable part (last two labels, approx), grey the rest so the real
    // domain is obvious (PS4 Section 7.4 Host display).
    val labels = host.split(".")
    val reg = if (labels.size >= 2) labels.takeLast(2).joinToString(".") else host
    val prefix = host.removeSuffix(reg).removeSuffix(".")
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
            Text(
                buildAnnotatedString {
                    if (prefix.isNotEmpty()) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("$prefix.")
                        }
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(reg)
                    }
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun ReasonsCard(verdict: UiVerdict, s: Strings) {
    val colors = levelColors(verdict.level)
    val reasons = verdict.reasons(com.phisherman.kavach.ui.theme.LocalHindi.current).filter { it.isNotBlank() }
    if (reasons.isEmpty()) return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                s.whyFlagged,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onContainer,
            )
            reasons.take(3).forEach { ReasonRow(it, dot = colors.fg) }
        }
    }
}
