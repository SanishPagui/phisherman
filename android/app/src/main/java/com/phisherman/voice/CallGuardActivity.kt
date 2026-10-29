package com.phisherman.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phisherman.data.Prefs
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.ui.AppCard
import com.phisherman.ui.GradientButton
import com.phisherman.ui.IconTile
import com.phisherman.ui.LevelBadge
import com.phisherman.ui.ReasonRow
import com.phisherman.ui.ReportActivity
import com.phisherman.ui.SectionLabel
import com.phisherman.ui.ShieldOrb
import com.phisherman.ui.levelColors
import com.phisherman.ui.levelIcon
import com.phisherman.ui.levelLabel
import com.phisherman.ui.theme.LocalHindi
import com.phisherman.ui.theme.PhishermanTheme
import com.phisherman.ui.theme.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Call guard screen: live risk, what was heard, start / stop, and demo scripts. */
class CallGuardActivity : ComponentActivity() {
    private var scriptJob: Job? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startMic() else CallGuard.onError("Microphone permission is needed to listen.")
    }
    private var autoOn by mutableStateOf(false)
    private val autoPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        setAuto(CallWatcher.canWatch(this))
    }

    private fun setAuto(on: Boolean) {
        Prefs.setAutoCallGuard(this, on)
        autoOn = on && CallWatcher.canWatch(this)
        if (autoOn) CallWatcher.start(this) else CallWatcher.stop(this)
    }

    private fun toggleAuto() {
        if (autoOn) { setAuto(false); return }
        if (CallWatcher.canWatch(this)) setAuto(true)
        else autoPermissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CallGuardNotifier.ensureChannels(this)
        autoOn = Prefs.autoCallGuard(this) && CallWatcher.canWatch(this)
        val scripts = assets.open("voice_scripts.tsv").bufferedReader(Charsets.UTF_8).readLines().drop(1)
            .filter { it.isNotBlank() }.map { it.split("\t") }.map { DemoScript(it[0], it[1], it[2], it[3].split(" || ")) }
        setContent {
            var hindi by remember { mutableStateOf(Prefs.hindi(this)) }
            PhishermanTheme(hindi = hindi) {
                val st by CallGuard.state.collectAsState()
                GuardScreen(st, scripts, autoOn, ::toggleAuto,
                    onBack = ::finish,
                    onToggleLanguage = { hindi = !hindi; Prefs.setHindi(this, hindi) },
                    onStartMic = ::requestMic,
                    onScript = ::playScript,
                    onStop = ::stopAll,
                    onAck = { CallGuard.acknowledge(this) },
                    onReport = { v -> startActivity(ReportActivity.intent(this, v)) })
            }
        }
    }

    private fun requestMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startMic()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startMic() {
        scriptJob?.cancel()
        ContextCompat.startForegroundService(this, Intent(this, CallGuardService::class.java))
    }

    /** Demo / fallback (PS4 5.1, 9.5): feeds a scripted call word by word, no microphone needed. */
    private fun playScript(s: DemoScript) {
        stopAll()
        CallGuard.begin(this, GuardMode.SCRIPT)
        scriptJob = lifecycleScope.launch {
            for (raw in s.lines) {
                val line = raw.removePrefix("[PAY] ")
                val words = line.split(" ")
                for (i in 1..words.size) {
                    CallGuard.onPartial(this@CallGuardActivity, words.take(i).joinToString(" "))
                    delay(170)
                }
                CallGuard.onFinal(this@CallGuardActivity, line)
                delay(1200)
            }
            delay(2500)  // keep the final verdict on screen, then end so a real call can start the guard
            CallGuard.end(this@CallGuardActivity)
        }
    }

    private fun stopAll() {
        scriptJob?.cancel()
        if (CallGuard.state.value.mode == GuardMode.MIC) {
            startService(Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP))
        } else {
            CallGuard.end(this)
        }
    }
}

data class DemoScript(val id: String, val kind: String, val title: String, val lines: List<String>)

@Composable
private fun GuardScreen(
    st: GuardState, scripts: List<DemoScript>, autoOn: Boolean, onToggleAuto: () -> Unit,
    onBack: () -> Unit, onToggleLanguage: () -> Unit, onStartMic: () -> Unit, onScript: (DemoScript) -> Unit,
    onStop: () -> Unit, onAck: () -> Unit, onReport: (Verdict) -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val hindi = LocalHindi.current
    val running = st.mode != GuardMode.OFF
    val c = levelColors(st.level)

    Column(
        Modifier.fillMaxSize().background(cs.background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(cs.surface)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = cs.onSurface)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Call guard", "कॉल गार्ड"), style = MaterialTheme.typography.headlineMedium, color = cs.onBackground)
                Text(tr("Voice scam detection, fully offline", "आवाज़ से स्कैम पहचान, पूरी तरह ऑफ़लाइन"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = onToggleLanguage, modifier = Modifier.clip(CircleShape).background(cs.surface)) {
                Icon(Icons.Rounded.Translate, null, tint = cs.onSurface)
            }
        }

        // Live status: big orb coloured by risk.
        AppCard(container = if (running) c.container else cs.surface) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ShieldOrb(if (running) levelIcon(st.level) else Icons.Rounded.Mic,
                    if (running) c.fg else cs.primary, size = 120.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (!running) tr("Not listening", "सुन नहीं रहा")
                    else when (st.level) {
                        Level.DANGEROUS -> tr("This call sounds like a scam", "यह कॉल स्कैम जैसी लग रही है")
                        Level.SUSPICIOUS -> tr("Careful: warning signs heard", "सावधान: चेतावनी के संकेत")
                        Level.SAFE -> tr("Listening… nothing risky so far", "सुन रहा है… अभी तक कुछ ख़तरनाक नहीं")
                    },
                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    color = if (running) c.onContainer else cs.onSurface,
                )
                if (running) { Spacer(Modifier.height(8.dp)); LevelBadge(st.level) }
                st.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = levelColors(Level.DANGEROUS).fg, textAlign = TextAlign.Center)
                }
            }
        }

        if (running && st.level == Level.DANGEROUS) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(levelColors(Level.DANGEROUS).fg).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if ("disclosure" in st.heard) tr("STOP. Don't share it. Hang up now.", "रुकिए। मत बताइए। फ़ोन काटिए।")
                        else tr("Hang up. Never share OTP or PIN.", "फ़ोन काटें। OTP या PIN कभी न बताएँ।"),
                        color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (st.alerting) Text(tr("I'm safe, stop the alert", "मैं सुरक्षित हूँ, अलर्ट बंद करें"),
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onAck)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = levelColors(Level.DANGEROUS).fg, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (st.heard.isNotEmpty()) {
            AppCard {
                Text(tr("Warning signs heard", "सुने गए चेतावनी संकेत"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CallGuard.reasonsFor(ctx, st.heard).forEach { ReasonRow(if (hindi) it.textHi ?: it.textEn else it.textEn, c.fg) }
                    st.llmReason?.let { ReasonRow(tr("On-device AI: $it", "ऑन-डिवाइस AI: $it"), c.fg) }
                }
            }
        }

        if (running) {
            AppCard {
                Text(tr("What was said", "क्या कहा गया"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.height(8.dp))
                st.lines.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface) }
                if (st.partial.isNotBlank()) Text(st.partial + " …", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (st.mode == GuardMode.MIC) {
                    Spacer(Modifier.height(6.dp))
                    // diagnostic: stays near -2 dB or lower if Android is giving us silence during a call
                    Text(tr("Microphone level: %.0f dB".format(st.peakDb), "माइक स्तर: %.0f dB".format(st.peakDb)),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            GradientButton(tr("Stop call guard", "कॉल गार्ड बंद करें"), onStop, icon = Icons.Rounded.Stop,
                brush = SolidColor(Color(0xFF3A4256)))
        } else {
            GradientButton(tr("Start listening", "सुनना शुरू करें"), onStartMic, icon = Icons.Rounded.Mic)
            Text(tr("No speaker needed: Phisherman listens to what YOU say and stops you before you give away an OTP, PIN or money. Turn on speaker to also check what the caller says. Speech becomes text on this phone only; nothing is recorded or uploaded.",
                "स्पीकर ज़रूरी नहीं: Phisherman सुनता है कि आप क्या कहते हैं और OTP, PIN या पैसे देने से पहले रोकता है। कॉल करने वाले की बात भी जाँचने के लिए स्पीकर चालू करें। आवाज़ इसी फ़ोन पर टेक्स्ट बनती है; कुछ रिकॉर्ड या अपलोड नहीं होता।"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

            AppCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.PhoneInTalk, cs.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tr("Start on every call", "हर कॉल पर शुरू करें"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                        Text(tr("Uses call state only, never your contacts or numbers.", "सिर्फ़ कॉल की स्थिति, कोई नंबर या संपर्क नहीं।"),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(checked = autoOn, onCheckedChange = { onToggleAuto() })
                }
            }

            st.lastSummary?.let { v ->
                val lc = levelColors(v.level)
                SectionLabel(tr("Last call", "पिछली कॉल"))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(lc.container).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(levelIcon(v.level), Color.White, background = lc.fg)
                        Spacer(Modifier.width(12.dp))
                        Text(levelLabel(v.level), style = MaterialTheme.typography.titleLarge, color = lc.onContainer, modifier = Modifier.weight(1f))
                        if (v.level != Level.SAFE) Row(
                            Modifier.clip(RoundedCornerShape(50)).background(lc.fg).clickable { onReport(v) }
                                .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Flag, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Report", "रिपोर्ट"), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    v.reasons.forEach { ReasonRow(if (hindi) it.textHi ?: it.textEn else it.textEn, lc.fg, lc.onContainer) }
                    if (v.level != Level.SAFE) Text(tr("If you shared an OTP or paid, call 1930 now.", "अगर OTP बताया या पैसे भेजे, तो अभी 1930 पर कॉल करें।"),
                        style = MaterialTheme.typography.titleSmall, color = lc.onContainer)
                }
            }

            SectionLabel(tr("Try a demo call (no microphone)", "डेमो कॉल आज़माएँ (बिना माइक)"))
            scripts.forEach { s ->
                val scam = s.kind == "scam"
                val tint = if (scam) levelColors(Level.DANGEROUS).fg else levelColors(Level.SAFE).fg
                AppCard(Modifier.clickable { onScript(s) }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.PlayArrow, tint, size = 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                            Text(if (scam) tr("Scam call", "स्कैम कॉल") else tr("Normal call", "सामान्य कॉल"),
                                style = MaterialTheme.typography.bodySmall, color = tint)
                        }
                        Text(s.id, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
