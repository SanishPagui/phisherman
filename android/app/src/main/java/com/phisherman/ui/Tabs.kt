package com.phisherman.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phisherman.data.Decision
import com.phisherman.data.Prefs
import com.phisherman.engine.Level
import com.phisherman.link.Forwarder
import com.phisherman.ui.theme.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- History

@Composable
fun HistoryTab(decisions: List<Decision>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    var flaggedOnly by remember { mutableStateOf(false) }
    val flagged = decisions.count { it.level != Level.SAFE.name }
    val shown = if (flaggedOnly) decisions.filter { it.level != Level.SAFE.name } else decisions

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        ScreenHeader(tr("History", "इतिहास"), tr("Every check made on this phone", "इस फ़ोन पर की गई हर जाँच")) {
            if (decisions.isNotEmpty()) IconButton(onClick = onClear,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)) {
                Icon(Icons.Rounded.DeleteOutline, tr("Clear history", "इतिहास साफ़ करें"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
            Chip("${tr("All", "सभी")} · ${decisions.size}", !flaggedOnly) { flaggedOnly = false }
            Chip("${tr("Flagged", "चिह्नित")} · $flagged", flaggedOnly) { flaggedOnly = true }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { DecisionRow(it) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Text(label,
        Modifier.clip(shape).background(if (selected) cs.onBackground else cs.surface)
            .border(1.dp, if (selected) cs.onBackground else cs.outlineVariant, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = if (selected) cs.background else cs.onSurface)
}

@Composable
private fun DecisionRow(d: Decision) {
    val level = runCatching { Level.valueOf(d.level) }.getOrDefault(Level.SAFE)
    val c = levelColors(level)
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    AppCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(when (d.channel) { "VOICE" -> Icons.Rounded.Mic; "SMS" -> Icons.Rounded.Sms; else -> Icons.Rounded.Link }, c.fg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.subject.ifEmpty { d.channel }, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                val action = when (d.userAction) {
                    "BACK" -> tr(" · went back", " · वापस गए"); "OPEN_ANYWAY" -> tr(" · opened anyway", " · फिर भी खोला")
                    "REPORTED" -> tr(" · reported", " · रिपोर्ट किया"); else -> ""
                }
                Text("${channelName(d.channel)} · ${fmt.format(Date(d.ts))}$action", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            LevelBadge(level)
        }
    }
}

@Composable
private fun channelName(ch: String) = when (ch) {
    "VOICE" -> tr("Call", "कॉल"); "SMS" -> tr("Message", "संदेश"); else -> tr("Link", "लिंक")
}

// ---------------------------------------------------------------- Results

@Composable
fun ResultsTab(results: AppResults, voice: Metric, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(tr("Results", "परिणाम"),
            tr("How well Phisherman detects phishing, measured on test data it never trained on.",
                "Phisherman फ़िशिंग को कितनी अच्छी तरह पकड़ता है, उस टेस्ट डेटा पर जिस पर इसे ट्रेन नहीं किया गया।"))

        // Headline: fraud detection rate (= recall) per channel.
        AppCard {
            Text(tr("Fraud detection rate", "धोखा पहचान दर"), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(tr("Share of real phishing caught (recall)", "पकड़ी गई असली फ़िशिंग का हिस्सा (रिकॉल)"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigStat(pctWhole(results.link.test.recall), tr("Links", "लिंक"), Icons.Rounded.Link, Modifier.weight(1f))
                BigStat(pctWhole(results.sms.test.recall), tr("SMS", "SMS"), Icons.Rounded.Sms, Modifier.weight(1f))
                BigStat(pctWhole(voice.recall), tr("Calls", "कॉल"), Icons.Rounded.Mic, Modifier.weight(1f))
            }
        }

        MetricCard(Icons.Rounded.Link, tr("Link model", "लिंक मॉडल"), results.link.dataset, results.link.test)
        MetricCard(Icons.Rounded.Science, tr("Links · unseen dataset", "लिंक · अनदेखा डेटासेट"),
            results.link.unseenDataset, results.link.unseen,
            note = tr("A different source, never used for training: the honest real-world check.",
                "एक अलग स्रोत, ट्रेनिंग में कभी इस्तेमाल नहीं हुआ: असली दुनिया की ईमानदार जाँच।"))
        MetricCard(Icons.Rounded.Sms, tr("SMS engine", "SMS इंजन"), results.sms.dataset, results.sms.test)
        MetricCard(Icons.Rounded.Mic, tr("Call guard", "कॉल गार्ड"),
            tr("${voice.n} scripted calls, scored live on this phone", "${voice.n} स्क्रिप्टेड कॉल, इसी फ़ोन पर अभी जाँची गईं"), voice)

        val hn = results.link.hardNegatives
        if (hn.n > 0) NoteCard(tr("False alarms on ${hn.n} real bank and government login pages: ${pct(hn.fprEngine, zero = "0%")}.",
            "${hn.n} असली बैंक और सरकारी लॉगिन पेज पर गलत चेतावनियाँ: ${pct(hn.fprEngine, zero = "0%")}।"))
        if (results.generated.isNotEmpty()) Text(tr("Measured ${results.generated}", "मापा गया ${results.generated}"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BigStat(value: String, label: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun MetricCard(icon: ImageVector, title: String, subtitle: String, m: Metric, note: String? = null) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, cs.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
            }
            Text("N ${"%,d".format(m.n)}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ValueBar(tr("Precision", "प्रिसिज़न"), m.precision, pct(m.precision), cs.primary)
            ValueBar(tr("Recall (fraud detection rate)", "रिकॉल (धोखा पहचान दर)"), m.recall, pct(m.recall), Color(0xFF0EA5A4))
            ValueBar(tr("False alarm rate", "गलत चेतावनी दर"), m.fpr, pct(m.fpr, zero = "0%"), levelColors(Level.DANGEROUS).fg)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cell(tr("Caught", "पकड़े"), m.tp, levelColors(Level.SAFE).fg, Modifier.weight(1f))
            Cell(tr("Missed", "छूटे"), m.fn, levelColors(Level.DANGEROUS).fg, Modifier.weight(1f))
            Cell(tr("False alarm", "गलत चेतावनी"), m.fp, levelColors(Level.SUSPICIOUS).fg, Modifier.weight(1f))
            Cell(tr("Safe", "सुरक्षित"), m.tn, cs.onSurfaceVariant, Modifier.weight(1f))
        }
        if (note != null) {
            Spacer(Modifier.height(12.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Cell(label: String, value: Int, accent: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%,d".format(value), style = MaterialTheme.typography.titleMedium, color = accent, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun NoteCard(text: String) {
    val c = levelColors(Level.SAFE)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.container).padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, null, tint = c.fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.onContainer)
    }
}

private fun pct(x: Double, zero: String = "—"): String = if (x <= 0.0) zero else "%.1f%%".format(x * 100)
private fun pctWhole(x: Double): String = if (x <= 0.0) "—" else "%.0f%%".format(x * 100)

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsTab(hindi: Boolean, onHindiChange: (Boolean) -> Unit, onClearHistory: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val browsers = remember { Forwarder.browsers(ctx) }
    var browser by remember { mutableStateOf(Forwarder.targetBrowser(ctx)) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(tr("Settings", "सेटिंग्स"))

        SectionLabel(tr("General", "सामान्य"))
        AppCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Language, cs.primary)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(tr("Hindi", "हिन्दी"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(tr("Show the app and warnings in Hindi", "ऐप और चेतावनियाँ हिन्दी में दिखाएँ"),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Switch(checked = hindi, onCheckedChange = onHindiChange)
            }
        }

        SectionLabel(tr("Safe links open in", "सुरक्षित लिंक इसमें खुलेंगे"))
        AppCard(padding = 8.dp) {
            if (browsers.isEmpty()) Text(tr("No other browser installed. Install Chrome so safe links can open.",
                "कोई अन्य ब्राउज़र नहीं है। Chrome इंस्टॉल करें।"), Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            browsers.forEach { b ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable { browser = b.packageName; Prefs.setChosenBrowser(ctx, b.packageName) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.OpenInBrowser, cs.onSurfaceVariant, size = 36.dp)
                    Text(b.label, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    RadioButton(selected = browser == b.packageName,
                        onClick = { browser = b.packageName; Prefs.setChosenBrowser(ctx, b.packageName) })
                }
            }
        }

        SectionLabel(tr("Data & privacy", "डेटा और गोपनीयता"))
        AppCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(Icons.Rounded.Lock, cs.primary)
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Private by design", "डिज़ाइन से निजी"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(tr("All checks run on this phone, offline. History keeps only the verdict, the website or sender and the time: never message text, full links or audio.",
                        "सारी जाँच इसी फ़ोन पर, ऑफ़लाइन होती है। इतिहास में सिर्फ़ निर्णय, वेबसाइट या भेजने वाला और समय रहता है: संदेश, पूरा लिंक या ऑडियो कभी नहीं।"),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
        OutlineButton(tr("Clear history", "इतिहास साफ़ करें"), onClearHistory, icon = Icons.Rounded.DeleteOutline,
            color = levelColors(Level.DANGEROUS).fg)

        Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
}
