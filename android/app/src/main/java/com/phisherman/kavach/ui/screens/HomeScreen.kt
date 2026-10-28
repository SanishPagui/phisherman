package com.phisherman.kavach.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.engine.Verdict
import com.phisherman.kavach.ui.Strings
import com.phisherman.kavach.ui.components.LevelBadge
import com.phisherman.kavach.ui.components.ReasonRow
import com.phisherman.kavach.ui.components.levelColors
import com.phisherman.kavach.ui.components.levelLabel
import com.phisherman.kavach.ui.strings
import com.phisherman.kavach.ui.theme.LocalHindi

@Composable
fun HomeScreen(
    isDefaultBrowser: Boolean,
    flaggedCount: Int,
    checksCount: Int,
    pasteResult: Verdict?,
    analysing: Boolean,
    voiceRunning: Boolean,
    voiceVerdict: Verdict?,
    voiceTranscript: String,
    onAnalyse: (String) -> Unit,
    onClearPaste: () -> Unit,
    onSetDefaultBrowser: () -> Unit,
    onStartGuard: () -> Unit,
    onStopGuard: () -> Unit,
    onFeedTranscript: (String) -> Unit,
    onReport: (Verdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Header(s)
        StatusCard(isDefaultBrowser, s, onSetDefaultBrowser)
        StatsRow(flaggedCount, checksCount, s)
        PasteCard(s, analysing, pasteResult, onAnalyse, onClearPaste, onReport)
        CallGuardCard(s, voiceRunning, voiceVerdict, voiceTranscript, onStartGuard, onStopGuard, onFeedTranscript, onReport)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Header(s: Strings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text("Phisherman", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text(s.appTagline, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusCard(isDefault: Boolean, s: Strings, onSetDefault: () -> Unit) {
    val colors = if (isDefault) levelColors(Level.SAFE) else levelColors(Level.SUSPICIOUS)
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isDefault) Icons.Rounded.CheckCircle else Icons.Rounded.Shield,
                null, tint = colors.fg, modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isDefault) s.protectionOn else s.protectionOff,
                    style = MaterialTheme.typography.titleMedium, color = colors.onContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!isDefault) {
                    Text(s.setAsBrowserCta, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onContainer)
                }
            }
            if (!isDefault) {
                Spacer(Modifier.size(8.dp))
                Button(onClick = onSetDefault, shape = RoundedCornerShape(12.dp)) {
                    Text("Set")
                }
            }
        }
    }
}

@Composable
private fun StatsRow(flagged: Int, checks: Int, s: Strings) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatTile(flagged.toString(), s.flaggedItems, levelColors(Level.DANGEROUS).fg, Modifier.weight(1f))
        StatTile(checks.toString(), s.linksChecked, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge, color = accent, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasteCard(
    s: Strings, analysing: Boolean, result: Verdict?,
    onAnalyse: (String) -> Unit, onClear: () -> Unit, onReport: (Verdict) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.checkLink + " / " + s.checkMessage, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(s.pasteHint) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Button(
                onClick = { onAnalyse(text) },
                enabled = text.isNotBlank() && !analysing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (analysing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(s.analyse)
                }
            }
            AnimatedVisibility(visible = result != null) {
                result?.let { InlineVerdict(it, s, onReport) }
            }
        }
    }
}

@Composable
private fun InlineVerdict(verdict: Verdict, s: Strings, onReport: (Verdict) -> Unit) {
    val colors = levelColors(verdict.level)
    val hindi = LocalHindi.current
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(verdict.level, levelLabel(verdict.level, s))
                Spacer(Modifier.weight(1f))
                Text("${verdict.score}/100", style = MaterialTheme.typography.titleMedium,
                    color = colors.fg, fontWeight = FontWeight.Bold)
            }
            verdict.reasons.forEach { ReasonRow(it.text(hindi), dot = colors.fg) }
            if (verdict.level != Level.SAFE) {
                TextButton(onClick = { onReport(verdict) }) {
                    Icon(Icons.Rounded.Flag, null, tint = colors.fg)
                    Spacer(Modifier.size(6.dp))
                    Text(s.reportThis, color = colors.fg)
                }
            }
        }
    }
}

@Composable
private fun CallGuardCard(
    s: Strings, running: Boolean, verdict: Verdict?, transcript: String,
    onStart: () -> Unit, onStop: () -> Unit, onFeed: (String) -> Unit, onReport: (Verdict) -> Unit,
) {
    var line by remember { mutableStateOf("") }
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text(s.callGuard, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }

            if (verdict != null && verdict.level != Level.SAFE) {
                val colors = levelColors(verdict.level)
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.container),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LevelBadge(verdict.level, levelLabel(verdict.level, s))
                            Spacer(Modifier.weight(1f))
                            Text("${verdict.score}/100", color = colors.fg, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        if (verdict.level == Level.DANGEROUS) {
                            Text(
                                if (LocalHindi.current) "फ़ोन रखें। OTP या PIN कभी साझा न करें।"
                                else "Hang up. Never share OTP or PIN.",
                                style = MaterialTheme.typography.titleMedium, color = colors.onContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        verdict.reasons.forEach { ReasonRow(it.text(LocalHindi.current), dot = colors.fg) }
                    }
                }
            }

            if (running) {
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = levelColors(Level.DANGEROUS).fg, contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Icon(Icons.Rounded.Stop, null); Spacer(Modifier.size(8.dp)); Text(s.stopGuard)
                }
            } else {
                OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Mic, null); Spacer(Modifier.size(8.dp)); Text(s.startGuard)
                }
            }

            // Typed/played-file transcript — the reliable demo path (PS4 Section 9.4).
            OutlinedTextField(
                value = line, onValueChange = { line = it },
                placeholder = { Text(s.typeTranscript) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            TextButton(
                onClick = { if (line.isNotBlank()) { onFeed(line); line = "" } },
                modifier = Modifier.align(Alignment.End),
            ) { Text(s.feedLine) }

            if (transcript.isNotBlank()) {
                Text(transcript, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
        }
    }
}
