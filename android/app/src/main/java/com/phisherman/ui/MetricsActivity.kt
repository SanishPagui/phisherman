package com.phisherman.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.phisherman.ui.theme.heroBrush
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phisherman.data.AppDatabase
import com.phisherman.engine.Level
import com.phisherman.engine.ScamLlm
import com.phisherman.ui.theme.PhishermanTheme
import com.phisherman.ui.theme.tr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/** Results (PS4 11): precision, recall, fraud detection rate, accuracy per channel, from
 *  assets/results.json (written by ml/eval_report.py), plus speed measured on this phone.
 *  Shown as the Results tab; this activity opens the same screen on its own. */
class MetricsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val results = loadResults(this)
        setContent {
            PhishermanTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                    ResultsContent(results)
                }
            }
        }
    }
}

fun loadResults(ctx: Context): JsonObject? = runCatching {
    Json.parseToJsonElement(ctx.assets.open("results.json").bufferedReader().use { it.readText() }).jsonObject
}.getOrNull()

private data class Latency(val channel: String, val n: Int, val p50: Long, val p95: Long)

@Composable
fun ResultsContent(r: JsonObject?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var latency by remember { mutableStateOf<List<Latency>>(emptyList()) }
    LaunchedEffect(Unit) {
        latency = AppDatabase.get(ctx).decisions().latencies().groupBy { it.channel }.map { (ch, rows) ->
            val s = rows.map { it.latencyMs }.sorted()
            Latency(ch, s.size, s[s.size / 2], s[minOf(s.size - 1, (s.size * 95) / 100)])
        }
    }
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(tr("Results", "परिणाम"),
            tr("How well Phisherman spots scams, measured on messages and links it never saw while learning.",
                "Phisherman स्कैम कितनी अच्छी तरह पकड़ता है, उन संदेशों और लिंक पर जो इसने सीखते समय कभी नहीं देखे।"))
        if (r == null) {
            NoticeCard(Level.SUSPICIOUS, Icons.Rounded.Info, "results.json is missing", "Run ml/eval_report.py and rebuild.")
            return@Column
        }
        val channels = r["channels"]!!.jsonArray.map { it.jsonObject }
        DetectionSummary(r["overall"]?.jsonObject, channels)
        BenchmarkCard()
        channels.forEach { ChannelCard(it) }
        r["link_threshold"]?.jsonObject?.let { ThresholdCard(it) }
        SpeedCard(latency)
        Glossary()
        Text(tr("Generated ${r["generated"]!!.jsonPrimitive.content}. Small or synthetic test sets are marked; treat those as indicative.",
            "${r["generated"]!!.jsonPrimitive.content} को बनाया गया। छोटे या सिंथेटिक टेस्ट सेट चिह्नित हैं; उन्हें संकेत मानें।"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

private fun iconFor(key: String): ImageVector = when (key) {
    "link" -> Icons.Rounded.Link; "sms" -> Icons.Rounded.Sms; "ai" -> Icons.Rounded.Psychology; "call" -> Icons.Rounded.Call
    "email" -> Icons.Rounded.Email
    else -> Icons.Rounded.Info
}

/** Headline: the overall numbers (PS4 deliverable 4), then fraud detection rate per channel. */
@Composable
private fun DetectionSummary(overall: JsonObject?, channels: List<JsonObject>) {
    val cs = MaterialTheme.colorScheme
    if (overall != null) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(heroBrush()).padding(22.dp)) {
            Text(tr("OVERALL · ALL CHANNELS", "कुल · सभी चैनल"), style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMetric(pct(overall.d("precision")), tr("Precision", "प्रिसिज़न"), Modifier.weight(1f))
                HeroMetric(pct(overall.d("recall")), tr("Recall", "रिकॉल"), Modifier.weight(1f))
                HeroMetric(pct(overall.d("fdr")), tr("Fraud detection", "धोखा पहचान"), Modifier.weight(1f), accent = Color(0xFF3DDC97))
            }
            Spacer(Modifier.height(12.dp))
            Text(tr("${"%,d".format(overall.i("n"))} held-out items (${"%,d".format(overall.i("scams"))} scams) across links, messages, email and calls. Accuracy ${pct(overall.d("accuracy"))}, false alarms ${pct(overall.d("fpr"))}.",
                "लिंक, संदेश, ईमेल और कॉल में ${"%,d".format(overall.i("n"))} टेस्ट आइटम (${"%,d".format(overall.i("scams"))} स्कैम)। सटीकता ${pct(overall.d("accuracy"))}, गलत चेतावनी ${pct(overall.d("fpr"))}।"),
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
        }
    }
    AppCard {
        Text(tr("Fraud detection rate by channel", "चैनल के अनुसार धोखा पहचान दर"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
        Text(tr("Share of real scams caught on held-out tests (any warning)", "अलग रखे टेस्ट में पकड़े गए असली स्कैम का हिस्सा (कोई भी चेतावनी)"),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            channels.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        val h = c["headline"]!!.jsonObject
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer).padding(12.dp)) {
                            Icon(iconFor(c["icon"]!!.jsonPrimitive.content), null, tint = cs.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(pctWhole(h.d("fdr")), style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer,
                                maxLines = 1, softWrap = false)
                            Text(c["name"]!!.jsonPrimitive.content, style = MaterialTheme.typography.labelMedium,
                                color = cs.onPrimaryContainer, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String, modifier: Modifier, accent: Color = Color.White) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

/** Runs the labelled sample through this phone's engines and shows it next to the Python numbers. */
@Composable
private fun BenchmarkCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var results by remember { mutableStateOf<List<BenchResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.PhoneAndroid, cs.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tr("Run on this phone", "इस फ़ोन पर चलाएँ"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(tr("Scores labelled links, messages, emails and calls with the app's own engines, offline, and compares with the Python reference.",
                    "लेबल वाले लिंक, संदेश, ईमेल और कॉल को ऐप के अपने इंजन से ऑफ़लाइन जाँचता है और Python से तुलना करता है।"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (running) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = cs.primary, trackColor = cs.surfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(tr("Checking… ${(progress * 100).toInt()}%", "जाँच हो रही है… ${(progress * 100).toInt()}%"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        } else {
            GradientButton(if (results == null) tr("Run benchmark", "बेंचमार्क चलाएँ") else tr("Run again", "फिर से चलाएँ"),
                icon = Icons.Rounded.PlayArrow, onClick = {
                    running = true; error = null; progress = 0f
                    scope.launch {
                        results = runCatching { Benchmark.run(ctx) { progress = it } }
                            .onFailure { error = it.message ?: it.javaClass.simpleName }.getOrNull()
                        running = false
                    }
                })
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = levelColors(Level.DANGEROUS).fg) }
        results?.let { list ->
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { b -> BenchRow(b) }
            }
            Spacer(Modifier.height(10.dp))
            Text(tr("Phone = this phone's engines. Python = the reference engines on the same items. Small sets of examples that were never trained on.",
                "फ़ोन = इस फ़ोन के इंजन। Python = उन्हीं आइटम पर संदर्भ इंजन। छोटे सेट जिन पर कभी ट्रेन नहीं किया गया।"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun BenchRow(b: BenchResult) {
    val cs = MaterialTheme.colorScheme
    val name = when (b.channel) {
        "link" -> tr("Links", "लिंक"); "message" -> tr("Messages & chats", "संदेश और चैट")
        "email" -> tr("Email", "ईमेल"); else -> tr("Calls", "कॉल")
    }
    val icon = when (b.channel) { "link" -> Icons.Rounded.Link; "message" -> Icons.Rounded.Sms; "email" -> Icons.Rounded.Email; else -> Icons.Rounded.Call }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface, modifier = Modifier.weight(1f))
            Text("N ${b.phone.n} · ${"%.0f".format(b.p50Ms)} ms", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(tr("Precision", "प्रिसिज़न"), pct(b.phone.precision), cs.primary, Modifier.weight(1f))
            StatTile(tr("Recall / FDR", "रिकॉल / FDR"), pct(b.phone.fdr), Color(0xFF0EA5A4), Modifier.weight(1f))
            StatTile(tr("Accuracy", "सटीकता"), pct(b.phone.accuracy), Color(0xFF6366F1), Modifier.weight(1f))
        }
        b.python?.let { py ->
            Spacer(Modifier.height(6.dp))
            Text(tr("Python: precision ${pct(py.precision)}, recall ${pct(py.recall)} · same verdict on ${b.agree} of ${b.phone.n}",
                "Python: प्रिसिज़न ${pct(py.precision)}, रिकॉल ${pct(py.recall)} · ${b.phone.n} में से ${b.agree} पर वही निर्णय"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChannelCard(c: JsonObject) {
    val cs = MaterialTheme.colorScheme
    val h = c["headline"]!!.jsonObject
    var more by remember { mutableStateOf(false) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(iconFor(c["icon"]!!.jsonPrimitive.content), cs.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(c["name"]!!.jsonPrimitive.content, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(h["set"]!!.jsonPrimitive.content, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
            }
            Text("N ${"%,d".format(h.i("n"))}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ValueBar(tr("Precision", "प्रिसिज़न"), h.d("precision"), pct(h.d("precision")), cs.primary)
            ValueBar(tr("Recall · fraud detection rate", "रिकॉल · धोखा पहचान दर"), h.d("fdr"), pct(h.d("fdr")), Color(0xFF0EA5A4))
            ValueBar(tr("Accuracy", "सटीकता"), h.d("accuracy"), pct(h.d("accuracy")), Color(0xFF6366F1))
            ValueBar(tr("False alarms", "गलत चेतावनी"), h.d("fpr"), pct(h.d("fpr")), levelColors(Level.DANGEROUS).fg)
        }
        Spacer(Modifier.height(12.dp))
        // how early: the same test set counting only red (dangerous) warnings, where measured
        c["red"]?.jsonObject?.let { red ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(levelColors(Level.DANGEROUS).container)
                .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GppBad, null, tint = levelColors(Level.DANGEROUS).fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Red warnings only: caught ${pct(red.d("recall"))}, precision ${pct(red.d("precision"))}",
                    "सिर्फ़ लाल चेतावनी: ${pct(red.d("recall"))} पकड़े, प्रिसिज़न ${pct(red.d("precision"))}"),
                    style = MaterialTheme.typography.bodySmall, color = levelColors(Level.DANGEROUS).onContainer)
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(tr("F1 ${pct(h.d("f1"))} · tested on ${"%,d".format(h.i("n"))} items, ${"%,d".format(h.i("scams"))} of them scams",
            "F1 ${pct(h.d("f1"))} · ${"%,d".format(h.i("n"))} पर जाँचा, जिनमें ${"%,d".format(h.i("scams"))} स्कैम"),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        val extra = c["more"]!!.jsonArray.map { it.jsonObject }
        val notes = c["notes"]!!.jsonArray.map { it.jsonPrimitive.content }
        if (extra.isNotEmpty() || notes.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).clickable { more = !more }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (more) tr("Show less", "कम दिखाएँ") else tr("Other test sets & notes", "अन्य टेस्ट सेट और नोट्स"),
                    color = cs.primary, style = MaterialTheme.typography.labelLarge)
                Icon(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = cs.primary)
            }
        }
        if (more) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                extra.forEach { m ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).padding(12.dp)) {
                        Text(m["set"]!!.jsonPrimitive.content, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        Text("Precision ${pct(m.d("precision"))} · Recall ${pct(m.d("recall"))} · Accuracy ${pct(m.d("accuracy"))} · N ${"%,d".format(m.i("n"))}",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                notes.forEach { ReasonRow(it, cs.onSurfaceVariant, cs.onSurfaceVariant) }
            }
        }
    }
}

/** PS4 11.4: move the link threshold and watch precision trade against recall. */
@Composable
private fun ThresholdCard(t: JsonObject) {
    val cs = MaterialTheme.colorScheme
    val curve = t["curve"]!!.jsonArray.map { it.jsonObject }
    val chosen = t.d("chosen")
    val start = curve.indexOfFirst { it.d("threshold") >= chosen }.coerceAtLeast(0)
    var pos by remember { mutableFloatStateOf(start.toFloat()) }
    val p = curve[pos.roundToInt().coerceIn(0, curve.lastIndex)]
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Tune, cs.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tr("Try it: link strictness", "आज़माएँ: लिंक सख़्ती"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(tr("Stricter catches more phishing but raises more false alarms. The app uses ${"%.2f".format(chosen)}.",
                    "ज़्यादा सख़्ती ज़्यादा फ़िशिंग पकड़ती है पर गलत चेतावनियाँ भी बढ़ती हैं। ऐप ${"%.2f".format(chosen)} इस्तेमाल करता है।"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Slider(value = pos, onValueChange = { pos = it }, valueRange = 0f..curve.lastIndex.toFloat(), steps = (curve.size - 2).coerceAtLeast(0))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(tr("Precision", "प्रिसिज़न"), pct(p.d("precision")), cs.primary, Modifier.weight(1f))
            StatTile(tr("Recall", "रिकॉल"), pct(p.d("recall")), Color(0xFF0EA5A4), Modifier.weight(1f))
            StatTile(tr("False alarms", "गलत"), pct(p.d("fpr")), levelColors(Level.DANGEROUS).fg, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Threshold ${"%.2f".format(p.d("threshold"))} · ${"%,d".format(t.i("n"))} held-out websites",
            "सीमा ${"%.2f".format(p.d("threshold"))} · ${"%,d".format(t.i("n"))} अलग रखी वेबसाइटें"),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier) {
    val animatedAlpha by animateFloatAsState(1f, tween(300), label = "stat")
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent.copy(alpha = animatedAlpha), maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun SpeedCard(latency: List<Latency>) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Speed, cs.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tr("Speed on this phone", "इस फ़ोन पर गति"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text("${Build.MANUFACTURER} ${Build.MODEL} · ${tr("measured from real checks", "असली जाँचों से मापा गया")}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (latency.isEmpty()) Text(tr("No checks recorded yet.", "अभी तक कोई जाँच दर्ज नहीं।"),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            latency.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach {
                        val name = when (it.channel) {
                            "LINK" -> tr("Links", "लिंक"); "SMS" -> tr("Messages", "संदेश"); "VOICE" -> tr("Calls", "कॉल")
                            "FILE" -> tr("Files", "फ़ाइलें"); else -> it.channel
                        }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).padding(12.dp)) {
                            Text("${it.p50} ms", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                            Text(tr("$name · p95 ${it.p95} ms · ${it.n} checks", "$name · p95 ${it.p95} ms · ${it.n} जाँच"),
                                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (ScamLlm.lastLatencyMs > 0) {
            Spacer(Modifier.height(8.dp))
            Text(tr("On-device AI: last answer in ${ScamLlm.lastLatencyMs} ms", "ऑन-डिवाइस AI: पिछला जवाब ${ScamLlm.lastLatencyMs} ms में"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Glossary() {
    val c = levelColors(Level.SAFE)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.container).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = c.fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("What the numbers mean", "आँकड़ों का मतलब"), style = MaterialTheme.typography.titleSmall, color = c.onContainer)
        }
        listOf(
            tr("Precision: when Phisherman warns, how often it is right.", "प्रिसिज़न: Phisherman की चेतावनी कितनी बार सही होती है।"),
            tr("Recall: of all the scams, how many it caught.", "रिकॉल: सभी स्कैम में से कितने पकड़े।"),
            tr("Fraud detection rate: share of fraud attempts detected. It is the same number as recall, named the way fraud teams report it.",
                "धोखा पहचान दर: पकड़े गए धोखे के प्रयासों का हिस्सा। यह रिकॉल जैसा ही आँकड़ा है।"),
            tr("Red warnings only: the same test, counting only the strongest (dangerous) alerts.",
                "सिर्फ़ लाल चेतावनी: वही टेस्ट, केवल सबसे तेज़ (ख़तरनाक) चेतावनियाँ गिनकर।"),
            tr("Accuracy: share of all answers (scam or safe) that were right.", "सटीकता: सभी जवाबों (स्कैम या सुरक्षित) में सही का हिस्सा।"),
            tr("False alarms: share of safe items wrongly warned about.", "गलत चेतावनी: सुरक्षित चीज़ों पर गलत चेतावनी का हिस्सा।"),
        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onContainer) }
    }
}

private fun JsonObject.d(k: String) = this[k]!!.jsonPrimitive.double
private fun JsonObject.i(k: String) = this[k]!!.jsonPrimitive.int
private fun pct(x: Double) = if (x >= 0.9995) "100%" else "%.1f%%".format(x * 100)
private fun pctWhole(x: Double) = "%.0f%%".format(x * 100)
