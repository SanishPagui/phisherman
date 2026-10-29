package com.phisherman.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phisherman.data.DecisionLog
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.link.Forwarder
import com.phisherman.link.LinkAnalyser
import com.phisherman.sms.SmsAnalyser
import com.phisherman.ui.theme.AmberWarn
import com.phisherman.ui.theme.LocalHindi
import com.phisherman.ui.theme.Mint
import com.phisherman.ui.theme.brandBrush
import com.phisherman.ui.theme.heroBrush
import com.phisherman.ui.theme.tr
import com.phisherman.voice.CallGuard
import com.phisherman.voice.CallGuardActivity
import com.phisherman.voice.GuardMode
import kotlinx.coroutines.launch

/** One protection the user can switch on. */
data class Shield(
    val icon: ImageVector,
    val title: String,
    val onText: String,
    val offText: String,
    val on: Boolean,
    val action: String,
    val onAction: () -> Unit,
)

@Composable
fun HomeTab(
    shields: List<Shield>,
    flagged: Int,
    checks: Int,
    batteryOk: Boolean,
    onFixBattery: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = shields.count { it.on }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopRow(onToggleLanguage)
        LinkAnalyser.loadError?.let {
            AppCard(container = levelColors(Level.DANGEROUS).container) {
                Text(tr("Detection engine failed to load", "पहचान इंजन लोड नहीं हुआ"),
                    style = MaterialTheme.typography.titleMedium, color = levelColors(Level.DANGEROUS).onContainer)
                Text(it, style = MaterialTheme.typography.bodySmall, color = levelColors(Level.DANGEROUS).onContainer)
            }
        }
        HeroCard(active, shields.size, flagged, checks)

        SectionLabel(tr("Shields", "सुरक्षा कवच"))
        AppCard(padding = 0.dp) {
            shields.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ShieldRow(s)
            }
        }
        if (active > 0 && !batteryOk) {
            AppCard(container = levelColors(Level.SUSPICIOUS).container, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.BatteryChargingFull, levelColors(Level.SUSPICIOUS).fg)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Keep protection running", "सुरक्षा चालू रखें"), style = MaterialTheme.typography.titleMedium,
                            color = levelColors(Level.SUSPICIOUS).onContainer)
                        Text(tr("Your phone may put Phisherman to sleep. Allow it to run in the background.",
                            "फ़ोन Phisherman को सुला सकता है। इसे बैकग्राउंड में चलने दें।"),
                            style = MaterialTheme.typography.bodySmall, color = levelColors(Level.SUSPICIOUS).onContainer)
                    }
                    PillAction(tr("Allow", "अनुमति"), onFixBattery)
                }
            }
        }

        SectionLabel(tr("Quick scan", "त्वरित जाँच"))
        ScanCard()

        SectionLabel(tr("Voice", "आवाज़"))
        CallGuardCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TopRow(onToggleLanguage: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(brandBrush()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Phisherman", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleLanguage,
            modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
            Icon(Icons.Rounded.Translate, "Language", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun HeroCard(active: Int, total: Int, flagged: Int, checks: Int) {
    val full = active == total
    val accent = if (full) Mint else AmberWarn
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(heroBrush()).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldOrb(if (full) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe, accent, size = 80.dp, iconTint = Color(0xFF0B1630))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (full) tr("You're protected", "आप सुरक्षित हैं") else tr("Protection incomplete", "सुरक्षा अधूरी है"),
                    style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(tr("$active of $total shields active", "$total में से $active सुरक्षा चालू"),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(total) { i ->
                        Box(Modifier.width(22.dp).height(5.dp).clip(RoundedCornerShape(50))
                            .background(if (i < active) accent else Color.White.copy(alpha = 0.18f)))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(flagged.toString(), tr("Threats caught", "पकड़े गए ख़तरे"), Color(0xFFFF8A8E), Modifier.weight(1f))
            HeroStat(checks.toString(), tr("Checks done", "जाँचें"), Mint, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("Everything is checked on this phone. Nothing is uploaded.", "सब कुछ इसी फ़ोन पर जाँचा जाता है। कुछ भी अपलोड नहीं होता।"),
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun HeroStat(value: String, label: String, accent: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun ShieldRow(s: Shield) {
    val tint = if (s.on) levelColors(Level.SAFE).fg else levelColors(Level.SUSPICIOUS).fg
    Row(Modifier.fillMaxWidth().clickable(enabled = !s.on, onClick = s.onAction).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconTile(s.icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(if (s.on) s.onText else s.offText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (s.on) OnChip() else PillAction(s.action, s.onAction)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

/** Paste a link or a whole message; links go to the link engine, anything else to the message engine. */
@Composable
private fun ScanCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, Verdict?>?>(null) }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Search, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tr("Check a link or message", "लिंक या संदेश जाँचें"), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(tr("Paste anything you're unsure about", "जिस पर शक हो उसे पेस्ट करें"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it; result = null },
            placeholder = { Text(tr("Paste a link or a whole SMS", "लिंक या पूरा SMS पेस्ट करें")) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 3, maxLines = 6,
            colors = fieldColors(),
            trailingIcon = if (input.isNotEmpty()) {
                { IconButton(onClick = { input = ""; result = null }) { Icon(Icons.Rounded.Close, null) } }
            } else null,
        )
        Spacer(Modifier.height(12.dp))
        GradientButton(
            tr("Check now", "अभी जाँचें"), icon = Icons.Rounded.Shield, enabled = input.isNotBlank(), loading = checking,
            onClick = {
                checking = true
                scope.launch {
                    val text = input.trim()
                    // one token with a dot = a link; anything else is checked as a message
                    val isLink = text.none { it.isWhitespace() } && '.' in text
                    val v = if (isLink) LinkAnalyser.analyse(ctx, text) else SmsAnalyser.analyse(ctx, null, text)
                    checking = false
                    if (v != null) DecisionLog.recordAsync(ctx, v)
                    result = (if (isLink) text else "") to v
                }
            },
        )
        AnimatedVisibility(result != null, enter = fadeIn() + expandVertically()) {
            result?.let { (url, v) ->
                Column {
                    Spacer(Modifier.height(14.dp))
                    if (v == null) {
                        Text(tr("That is not a web link (http or https).", "यह वेब लिंक (http या https) नहीं है।"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else ScanResult(url, v)
                }
            }
        }
    }
}

@Composable
private fun ScanResult(url: String, v: Verdict) {
    val ctx = LocalContext.current
    val c = levelColors(v.level)
    val hindi = LocalHindi.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.container).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(levelIcon(v.level), Color.White, background = c.fg, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(levelLabel(v.level), style = MaterialTheme.typography.titleLarge, color = c.onContainer)
                Text(tr("Checked in ${v.latencyMs} ms", "${v.latencyMs} ms में जाँचा"), style = MaterialTheme.typography.bodySmall,
                    color = c.onContainer.copy(alpha = 0.75f))
            }
        }
        v.reasons.forEach { ReasonRow(if (hindi) it.textHi ?: it.textEn else it.textEn, c.fg, c.onContainer) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (v.level == Level.SAFE && url.isNotEmpty()) {
                SmallAction(Icons.AutoMirrored.Rounded.OpenInNew, tr("Open", "खोलें"), c.fg) { Forwarder.openInRealBrowser(ctx, url) }
            }
            if (v.level != Level.SAFE) {
                SmallAction(Icons.Rounded.Warning, tr("See warning", "चेतावनी देखें"), c.fg) {
                    ctx.startActivity(if (url.isNotEmpty()) WarningActivity.intent(ctx, url, v) else WarningActivity.smsIntent(ctx, "", v))
                }
                SmallAction(Icons.Rounded.Flag, tr("Report", "रिपोर्ट"), c.fg) { ctx.startActivity(ReportActivity.intent(ctx, v)) }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(color).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CallGuardCard() {
    val ctx = LocalContext.current
    val st by CallGuard.state.collectAsState()
    val running = st.mode != GuardMode.OFF
    val c = levelColors(st.level)
    AppCard(Modifier.clickable { ctx.startActivity(Intent(ctx, CallGuardActivity::class.java)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) ShieldOrb(Icons.Rounded.Mic, c.fg, size = 50.dp)
            else IconTile(Icons.Rounded.Mic, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Call guard", "कॉल गार्ड"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (running) tr("Listening · ${levelLabel(st.level)}", "सुन रहा है · ${levelLabel(st.level)}")
                    else tr("Warns you during a call if it sounds like a scam. Works offline.",
                        "कॉल स्कैम जैसी लगे तो कॉल के दौरान चेतावनी देता है। ऑफ़लाइन काम करता है।"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (running) c.fg else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Icons for the shields list, kept here so MainActivity only wires state and actions. */
object ShieldIcons {
    val link = Icons.Rounded.Link
    val sms = Icons.Rounded.Sms
    val chat = Icons.Rounded.Forum
    val popup = Icons.Rounded.NotificationsActive
}
