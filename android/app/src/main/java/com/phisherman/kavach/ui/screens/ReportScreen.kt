package com.phisherman.kavach.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.phisherman.kavach.ui.UiVerdict
import com.phisherman.kavach.ui.components.LevelBadge
import com.phisherman.kavach.ui.components.levelColors
import com.phisherman.kavach.ui.components.levelLabel
import com.phisherman.kavach.ui.strings
import com.phisherman.kavach.ui.theme.LocalHindi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    verdict: UiVerdict,
    onBack: () -> Unit,
    onReportPortal: () -> Unit,
    onCall1930: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val s = strings()
    val hindi = LocalHindi.current
    var includeText by remember { mutableStateOf(false) }
    val evidence = remember(includeText, verdict) { buildEvidence(verdict, hindi, includeText) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(s.reportTitle) }) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Summary
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            verdict.subject.ifEmpty { verdict.channel.name },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        LevelBadge(verdict.level, levelLabel(verdict.level, s))
                    }
                    verdict.reasons(hindi).filter { it.isNotBlank() }.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Primary actions
            Button(
                onClick = onReportPortal,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.size(8.dp)); Text(s.reportNow)
            }
            Button(
                onClick = onCall1930,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = levelColors(com.phisherman.kavach.engine.Level.DANGEROUS).fg,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
            ) {
                Icon(Icons.Rounded.Call, null); Spacer(Modifier.size(8.dp)); Text(s.call1930)
            }

            // Evidence pack
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(s.evidencePack, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onCopy(evidence) }) {
                            Icon(Icons.Rounded.ContentCopy, s.copyEvidence)
                        }
                        IconButton(onClick = { onShare(evidence) }) {
                            Icon(Icons.Rounded.Share, s.shareEvidence)
                        }
                    }
                    Text(
                        evidence,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(checked = includeText, onCheckedChange = { includeText = it })
                        Spacer(Modifier.size(10.dp))
                        Text(s.includeMessageText, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Recovery checklist
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = levelColors(com.phisherman.kavach.engine.Level.SUSPICIOUS).container),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.ifYouPaid, style = MaterialTheme.typography.titleMedium,
                        color = levelColors(com.phisherman.kavach.engine.Level.SUSPICIOUS).onContainer)
                    Text(s.recoveryChecklist, style = MaterialTheme.typography.bodyMedium,
                        color = levelColors(com.phisherman.kavach.engine.Level.SUSPICIOUS).onContainer)
                }
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text(s.goBack)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Defanged evidence text (PS4 Section 10.3). Raw subject included only on opt-in. */
private fun buildEvidence(verdict: UiVerdict, hindi: Boolean, includeRaw: Boolean): String {
    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date())
    val subject = if (includeRaw) verdict.subject else defang(verdict.subject)
    val reasons = verdict.reasons(hindi).filter { it.isNotBlank() }
        .joinToString("\n") { " - $it" }
    return buildString {
        appendLine("PHISHERMAN INCIDENT SUMMARY")
        appendLine("Time: $time (local)")
        appendLine("Channel: ${verdict.channel.name}")
        appendLine("Risk: ${verdict.level.name} (${verdict.score}/100)")
        appendLine("Subject: $subject")
        appendLine("Flagged because:")
        appendLine(reasons.ifEmpty { " - model-rated risk" })
        if (!includeRaw) appendLine("(Link/sender defanged. Message text included only if you opt in.)")
    }.trim()
}

private fun defang(host: String): String =
    host.replace("http", "hxxp").replace(".", "[.]")
