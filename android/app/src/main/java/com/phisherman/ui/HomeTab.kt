package com.phisherman.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phisherman.HomeActions
import com.phisherman.Protection
import com.phisherman.data.Decision
import com.phisherman.data.DecisionLog
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.link.Forwarder
import com.phisherman.link.LinkAnalyser
import com.phisherman.sms.SmsAnalyser
import com.phisherman.ui.theme.AppSettings
import com.phisherman.ui.theme.Brand
import com.phisherman.ui.theme.LocalHindi
import com.phisherman.ui.theme.LocalIsDark
import com.phisherman.ui.theme.ThemeMode
import com.phisherman.ui.theme.brandBrush
import com.phisherman.ui.theme.heroBrush
import com.phisherman.ui.theme.tr
import com.phisherman.voice.CallGuard
import com.phisherman.voice.CallGuardActivity
import com.phisherman.voice.GuardMode
import kotlinx.coroutines.launch

@Composable
fun HomeTab(protection: Protection, actions: HomeActions, decisions: List<Decision>, onOpenHistory: () -> Unit) {
    val flagged = decisions.count { it.level != "SAFE" }
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopRow()
        Hero(protection.onCount, 5, flagged, decisions.size)

        // PS4 10.2: a warning in the last 15 minutes gets a reminder, in case the user went ahead anyway
        decisions.firstOrNull { it.level != "SAFE" && System.currentTimeMillis() - it.ts < 15 * 60_000 }?.let { d ->
            val mins = ((System.currentTimeMillis() - d.ts) / 60_000).coerceAtLeast(1)
            // chat subjects are "sender · app"; the sender can be empty
            val who = d.subject.trim().removePrefix("·").trim().ifEmpty { d.channel.lowercase() }
            NoticeCard(Level.DANGEROUS, Icons.Rounded.Warning, tr("You were warned $mins min ago", "$mins मिनट पहले चेतावनी दी गई"),
                tr("$who. If you entered details, shared an OTP or paid, call your bank and 1930 now.",
                    "$who. अगर आपने जानकारी दी, OTP बताया या भुगतान किया, तो अभी बैंक और 1930 पर कॉल करें।"))
        }
        LinkAnalyser.loadError?.let {
            NoticeCard(Level.DANGEROUS, Icons.Rounded.ErrorOutline, tr("Detection engine failed to load", "पहचान इंजन लोड नहीं हुआ"), it)
        }
        if ((protection.sms || protection.notifications) && !protection.battery) {
            NoticeCard(Level.SUSPICIOUS, Icons.Rounded.BatteryAlert, tr("Phone may pause Phisherman", "फ़ोन Phisherman को रोक सकता है"),
                tr("Allow it to run in the background so new messages are always checked.",
                    "इसे बैकग्राउंड में चलने दें ताकि नए संदेश हमेशा जाँचे जाएँ।"),
                action = tr("Allow", "अनुमति") to actions.battery)
        }

        SectionLabel(tr("Protection", "सुरक्षा"))
        AppCard(padding = 0.dp) {
            val rows = listOf(
                Row5(Icons.Rounded.Link, tr("Links", "लिंक"), tr("Checked before they open", "खुलने से पहले जाँचे जाते हैं"), protection.links, actions.links),
                Row5(Icons.Rounded.Sms, tr("SMS", "SMS"), tr("Checked as they arrive", "आते ही जाँचे जाते हैं"), protection.sms, actions.sms),
                Row5(Icons.Rounded.NotificationsActive, tr("App notifications", "ऐप नोटिफ़िकेशन"),
                    tr("WhatsApp, RCS, Instagram, Gmail…", "WhatsApp, RCS, Instagram, Gmail…"), protection.notifications, actions.notifications),
                Row5(Icons.Rounded.ChatBubble, tr("Open chats & emails", "खुली चैट और ईमेल"),
                    tr("Messages on screen in chat and mail apps", "चैट और मेल ऐप में स्क्रीन पर संदेश"), protection.openChats, actions.openChats),
                Row5(Icons.Rounded.Call, tr("Calls", "कॉल"), tr("Warns during scam calls", "स्कैम कॉल के दौरान चेतावनी"), protection.calls, actions.calls),
            )
            rows.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ProtectionRow(r)
            }
        }

        SectionLabel(tr("Check something", "कुछ जाँचें"))
        QuickCheck()

        SectionLabel(tr("Calls", "कॉल"))
        CallGuardCard()

        if (decisions.isNotEmpty()) {
            SectionLabel(tr("Latest", "हाल की"))
            AppCard(padding = 0.dp) {
                decisions.take(3).forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DecisionRow(d)
                }
                Text(tr("See all activity", "सारी गतिविधि देखें"),
                    Modifier.fillMaxWidth().clickable(onClick = onOpenHistory).padding(16.dp),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private class Row5(val icon: ImageVector, val title: String, val subtitle: String, val on: Boolean, val onTurnOn: () -> Unit)

@Composable
private fun TopRow() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(brandBrush()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Phisherman", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            color = cs.onBackground, modifier = Modifier.weight(1f))
        // quick light / dark switch; Settings also offers "follow system"
        IconButton(onClick = { AppSettings.setTheme(ctx, if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
            modifier = Modifier.size(42.dp).clip(CircleShape).background(cs.surface)) {
            Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                tr("Switch theme", "थीम बदलें"), tint = cs.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { AppSettings.setHindi(ctx, !AppSettings.hindi) },
            modifier = Modifier.size(42.dp).clip(CircleShape).background(cs.surface)) {
            Icon(Icons.Rounded.Translate, tr("Language", "भाषा"), tint = cs.onSurface)
        }
    }
}

@Composable
private fun Hero(active: Int, total: Int, flagged: Int, checks: Int) {
    val full = active == total
    val accent = if (full) Brand.Mint else Brand.Warn
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(heroBrush()).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldOrb(if (full) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe, accent, size = 80.dp, iconTint = Color(0xFF0B1630))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (full) tr("You're protected", "आप सुरक्षित हैं") else tr("Protection incomplete", "सुरक्षा अधूरी है"),
                    style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(tr("$active of $total protections on", "$total में से $active सुरक्षा चालू"),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(total) { i ->
                        Box(Modifier.width(20.dp).height(5.dp).clip(RoundedCornerShape(50))
                            .background(if (i < active) accent else Color.White.copy(alpha = 0.18f)))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(flagged.toString(), tr("Threats caught", "पकड़े गए ख़तरे"), Color(0xFFFF8A8E), Modifier.weight(1f))
            HeroStat(checks.toString(), tr("Checks done", "जाँचें"), Brand.Mint, Modifier.weight(1f))
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
private fun ProtectionRow(r: Row5) {
    val tint = if (r.on) levelColors(Level.SAFE).fg else levelColors(Level.SUSPICIOUS).fg
    Row(Modifier.fillMaxWidth().clickable(enabled = !r.on, onClick = r.onTurnOn).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconTile(r.icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (r.on) OnChip() else PillAction(tr("Turn on", "चालू करें"), r.onTurnOn)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

/** Paste a link or a whole message, or pick a file; the same engines as everything else. */
@Composable
private fun QuickCheck() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<String, Verdict?>?>(null) }
    var checking by remember { mutableStateOf(false) }
    // looks inside the file, so an app renamed to .pdf / .jpg is still caught
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ctx.startActivity(Intent(ctx, FileCheckActivity::class.java)
            .setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Search, MaterialTheme.colorScheme.primary, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tr("Link, message or file", "लिंक, संदेश या फ़ाइल"), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(tr("Paste anything you're unsure about", "जिस पर शक हो उसे पेस्ट करें"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it; result = null },
            placeholder = { Text(tr("Paste a link or a message", "लिंक या संदेश पेस्ट करें")) },
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
                    when {
                        v == null -> {}
                        v.level == Level.SAFE -> DecisionLog.recordAsync(ctx, v)
                        isLink -> ctx.startActivity(WarningActivity.intent(ctx, text, v))
                        else -> {
                            DecisionLog.recordAsync(ctx, v)
                            ctx.startActivity(WarningActivity.smsIntent(ctx, text, v))
                        }
                    }
                    result = (if (isLink) text else "") to v
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        OutlineButton(tr("Check a file (hidden apps / APKs)", "फ़ाइल जाँचें (छिपी ऐप / APK)"), { pickFile.launch(arrayOf("*/*")) },
            icon = Icons.Rounded.UploadFile)
        AnimatedVisibility(result != null, enter = fadeIn() + expandVertically()) {
            result?.let { (url, v) ->
                Column {
                    Spacer(Modifier.height(14.dp))
                    if (v == null) Text(tr("That is not a web link (http or https).", "यह वेब लिंक (http या https) नहीं है।"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else ScanResult(url, v)
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
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(levelIcon(v.level), Color.White, background = c.fg, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (v.level == Level.SAFE) tr("Looks safe", "सुरक्षित लगता है") else levelLabel(v.level),
                    style = MaterialTheme.typography.titleLarge, color = c.onContainer)
                Text(tr("Checked in ${v.latencyMs} ms", "${v.latencyMs} ms में जाँचा"), style = MaterialTheme.typography.bodySmall,
                    color = c.onContainer.copy(alpha = 0.75f))
            }
            if (v.level == Level.SAFE && url.isNotEmpty()) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(c.fg).clickable { Forwarder.openInRealBrowser(ctx, url) }
                    .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Open", "खोलें"), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        v.reasons.forEach { ReasonRow(if (hindi) it.textHi ?: it.textEn else it.textEn, c.fg, c.onContainer) }
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
                    else tr("Warns you during a call if it sounds like a scam. Try a demo call.",
                        "कॉल स्कैम जैसी लगे तो चेतावनी देता है। डेमो कॉल आज़माएँ।"),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (running) c.fg else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
