package com.phisherman.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phisherman.data.AppDatabase
import com.phisherman.data.Decision
import com.phisherman.data.Prefs
import com.phisherman.engine.Level
import com.phisherman.link.Forwarder
import com.phisherman.ui.theme.AppSettings
import com.phisherman.ui.theme.ThemeMode
import com.phisherman.ui.theme.tr
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Activity (history)

@Composable
fun ActivityTab(decisions: List<Decision>) {
    var flaggedOnly by remember { mutableStateOf(false) }
    val flagged = decisions.count { it.level != "SAFE" }
    val shown = if (flaggedOnly) decisions.filter { it.level != "SAFE" } else decisions

    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader(tr("Activity", "गतिविधि"), tr("Every check made on this phone", "इस फ़ोन पर की गई हर जाँच"))
        Spacer(Modifier.height(14.dp))
        if (decisions.isEmpty()) {
            AppCard(Modifier.padding(top = 24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Rounded.History, MaterialTheme.colorScheme.primary, size = 64.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(tr("Nothing checked yet. Paste a link or message on Home to try it.",
                        "अभी तक कोई जाँच नहीं। होम पर लिंक या संदेश पेस्ट करके आज़माएँ।"),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("${tr("All", "सभी")} · ${decisions.size}", !flaggedOnly) { flaggedOnly = false }
            FilterChip("${tr("Flagged", "चिह्नित")} · $flagged", flaggedOnly) { flaggedOnly = true }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { d -> AppCard(padding = 0.dp) { DecisionRow(d) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Text(label,
        Modifier.clip(shape).background(if (selected) cs.onBackground else cs.surface)
            .border(1.dp, if (selected) cs.onBackground else cs.outlineVariant, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = if (selected) cs.background else cs.onSurface)
}

/** One logged verdict: channel icon tinted by risk, subject, channel · time · what the user did. */
@Composable
fun DecisionRow(d: Decision) {
    val level = runCatching { Level.valueOf(d.level) }.getOrDefault(Level.SAFE)
    val c = levelColors(level)
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(when (d.channel) {
            "VOICE" -> Icons.Rounded.Call; "SMS" -> Icons.Rounded.Sms; "FILE" -> Icons.Rounded.Description; else -> Icons.Rounded.Link
        }, c.fg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.subject.trim().removePrefix("·").trim().ifEmpty { d.channel }, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val action = when (d.userAction) {
                "BACK" -> tr(" · went back", " · वापस गए"); "OPEN_ANYWAY" -> tr(" · opened anyway", " · फिर भी खोला")
                "FORWARDED" -> tr(" · opened", " · खोला"); null -> ""; else -> " · ${d.userAction.lowercase()}"
            }
            Text("${channelName(d.channel)} · ${fmt.format(Date(d.ts))}$action", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        LevelBadge(level)
    }
}

@Composable
private fun channelName(ch: String) = when (ch) {
    "VOICE" -> tr("Call", "कॉल"); "SMS" -> tr("Message", "संदेश"); "FILE" -> tr("File", "फ़ाइल"); else -> tr("Link", "लिंक")
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsTab() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val browsers = remember { Forwarder.browsers(ctx) }
    var browser by remember { mutableStateOf(Forwarder.targetBrowser(ctx)) }
    var cleared by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(tr("Settings", "सेटिंग्स"))

        SectionLabel(tr("Appearance", "रूप"))
        AppCard(padding = 16.dp) {
            Text(tr("Theme", "थीम"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(Modifier.height(10.dp))
            Segmented(
                options = listOf(ThemeMode.SYSTEM to tr("System", "सिस्टम"), ThemeMode.LIGHT to tr("Light", "लाइट"),
                    ThemeMode.DARK to tr("Dark", "डार्क")),
                selected = AppSettings.themeMode,
                onSelect = { AppSettings.setTheme(ctx, it) },
                icons = listOf(Icons.Rounded.BrightnessAuto, Icons.Rounded.LightMode, Icons.Rounded.DarkMode),
            )
        }

        SectionLabel(tr("Language", "भाषा"))
        AppCard(padding = 0.dp) {
            SettingRow(Icons.Rounded.Language, tr("Hindi", "हिन्दी"),
                tr("Show the app and warnings in Hindi", "ऐप और चेतावनियाँ हिन्दी में दिखाएँ"),
                onClick = { AppSettings.setHindi(ctx, !AppSettings.hindi) }) {
                Switch(checked = AppSettings.hindi, onCheckedChange = { AppSettings.setHindi(ctx, it) })
            }
        }

        SectionLabel(tr("Safe links open in", "सुरक्षित लिंक इसमें खुलेंगे"))
        AppCard(padding = 0.dp) {
            if (browsers.isEmpty()) Text(tr("No other browser installed. Install Chrome so safe links can open.",
                "कोई अन्य ब्राउज़र नहीं है। Chrome इंस्टॉल करें ताकि सुरक्षित लिंक खुल सकें।"),
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            browsers.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                val pick = { browser = b.packageName; Prefs.setChosenBrowser(ctx, b.packageName) }
                SettingRow(Icons.Rounded.OpenInBrowser, b.label, tint = cs.onSurfaceVariant, onClick = pick) {
                    RadioButton(selected = browser == b.packageName, onClick = pick)
                }
            }
        }

        SectionLabel(tr("Data & privacy", "डेटा और गोपनीयता"))
        AppCard(padding = 0.dp) {
            SettingRow(Icons.Rounded.Lock, tr("Private by design", "डिज़ाइन से निजी"),
                tr("All checks run on this phone, offline. History keeps only the verdict, the website or sender and the time: never message text or audio.",
                    "सारी जाँच इसी फ़ोन पर, ऑफ़लाइन होती है। इतिहास में सिर्फ़ निर्णय, वेबसाइट या भेजने वाला और समय रहता है: संदेश या ऑडियो कभी नहीं।"))
            HorizontalDivider(Modifier.padding(start = 70.dp), color = cs.outlineVariant)
            SettingRow(Icons.Rounded.DeleteOutline,
                if (cleared) tr("History cleared", "इतिहास साफ़ हुआ") else tr("Clear check history", "जाँच इतिहास साफ़ करें"),
                tint = levelColors(Level.DANGEROUS).fg,
                onClick = { scope.launch { AppDatabase.get(ctx).decisions().clear(); cleared = true } })
        }

        AppCard(padding = 0.dp) {
            SettingRow(Icons.Rounded.PhoneAndroid, "${Build.MANUFACTURER} ${Build.MODEL}",
                "Android ${Build.VERSION.RELEASE} · Phisherman", tint = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}
