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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phisherman.data.Prefs
import com.phisherman.engine.Level
import com.phisherman.ui.AppCard
import com.phisherman.ui.GradientButton
import com.phisherman.ui.IconTile
import com.phisherman.ui.LevelBadge
import com.phisherman.ui.ReasonRow
import com.phisherman.ui.SectionLabel
import com.phisherman.ui.SettingRow
import com.phisherman.ui.ShieldOrb
import com.phisherman.ui.levelColors
import com.phisherman.ui.levelIcon
import com.phisherman.ui.levelLabel
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import com.phisherman.ui.theme.AppSettings
import com.phisherman.ui.theme.PhishermanTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Call guard screen: risk meter, what was heard, start / stop, and demo scripts. */
class CallGuardActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START = "start_listening"
    }

    private var scriptJob: Job? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START, false) && CallGuard.state.value.mode == GuardMode.OFF) requestMic()
    }

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
        // opened from the "tap to start listening" notification during a call: start right away
        if (intent?.getBooleanExtra(EXTRA_START, false) == true && CallGuard.state.value.mode == GuardMode.OFF) requestMic()
        val scripts = assets.open("voice_scripts.tsv").bufferedReader(Charsets.UTF_8).readLines().drop(1)
            .filter { it.isNotBlank() }.map { it.split("\t") }.map { DemoScript(it[0], it[1], it[2], it[3].split(" || ")) }
        setContent {
            PhishermanTheme {
                val st by CallGuard.state.collectAsState()
                GuardScreen(st, scripts, AppSettings.hindi, autoOn, ::toggleAuto,
                    onBack = ::finish,
                    onToggleLanguage = { AppSettings.setHindi(this, !AppSettings.hindi) },
                    onStartMic = ::requestMic,
                    onScript = ::playScript,
                    onStop = ::stopAll,
                    onAck = { CallGuard.acknowledge(this) })
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
            // end the scripted "call" so a real call can start the guard afterwards
            delay(4000)
            if (CallGuard.state.value.mode == GuardMode.SCRIPT) CallGuard.end(this@CallGuardActivity)
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
    st: GuardState, scripts: List<DemoScript>, hindi: Boolean, autoOn: Boolean, onToggleAuto: () -> Unit,
    onBack: () -> Unit, onToggleLanguage: () -> Unit, onStartMic: () -> Unit, onScript: (DemoScript) -> Unit,
    onStop: () -> Unit, onAck: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val running = st.mode != GuardMode.OFF
    val c = levelColors(st.level)
    val danger = levelColors(Level.DANGEROUS)

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
                Text(t("Call guard", "कॉल गार्ड"), style = MaterialTheme.typography.headlineMedium, color = cs.onBackground)
                Text(t("Scam call detection, fully offline", "स्कैम कॉल पहचान, पूरी तरह ऑफ़लाइन"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = onToggleLanguage, modifier = Modifier.clip(CircleShape).background(cs.surface)) {
                Icon(Icons.Rounded.Translate, null, tint = cs.onSurface)
            }
        }

        // live status: big orb coloured by risk
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (running) c.container else cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(28.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            ShieldOrb(if (running) levelIcon(st.level) else Icons.Rounded.Mic, if (running) c.fg else cs.primary, size = 120.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (!running) t("Not listening", "सुन नहीं रहा")
                else when (st.level) {
                    Level.DANGEROUS -> t("This call sounds like a scam", "यह कॉल स्कैम जैसी लग रही है")
                    Level.SUSPICIOUS -> t("Careful: warning signs heard", "सावधान: चेतावनी के संकेत")
                    Level.SAFE -> t("Listening… nothing risky so far", "सुन रहा है… अभी तक कुछ ख़तरनाक नहीं")
                },
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                color = if (running) c.onContainer else cs.onSurface,
            )
            if (running) { Spacer(Modifier.height(8.dp)); LevelBadge(st.level) }
            st.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = danger.fg, textAlign = TextAlign.Center)
            }
        }

        if (running && st.level == Level.DANGEROUS) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(danger.fg).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t("Hang up. Never share OTP or PIN.", "फ़ोन काटें। OTP या PIN कभी न बताएँ।"),
                        color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (st.alerting) Text(t("I'm safe, stop the alert", "मैं सुरक्षित हूँ, अलर्ट बंद करें"),
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onAck)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = danger.fg, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (st.heard.isNotEmpty() || st.llmReason != null) {
            AppCard {
                Text(t("Warning signs heard", "सुने गए चेतावनी संकेत"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CallGuard.reasonsFor(ctx, st.heard).forEach { ReasonRow(if (hindi) it.textHi ?: it.textEn else it.textEn, c.fg) }
                    st.llmReason?.let { ReasonRow(t("On-device AI: $it", "ऑन-डिवाइस AI: $it"), c.fg) }
                }
            }
        }

        if (running) {
            AppCard {
                Text(t("What was said", "क्या कहा गया"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.height(8.dp))
                if (st.lines.isEmpty() && st.partial.isBlank()) Text(t("Waiting for speech…", "आवाज़ का इंतज़ार…"),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                st.lines.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface) }
                if (st.partial.isNotBlank()) Text(st.partial + " …", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (st.mode == GuardMode.MIC) {
                    Spacer(Modifier.height(6.dp))
                    // diagnostic: stays near -2 dB or lower if Android is giving us silence during a call
                    Text(t("Microphone level: %.0f dB".format(st.peakDb), "माइक स्तर: %.0f dB".format(st.peakDb)),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            GradientButton(t("Stop call guard", "कॉल गार्ड बंद करें"), onStop, icon = Icons.Rounded.Stop,
                brush = SolidColor(Color(0xFF3A4256)))
        } else {
            AppCard(padding = 0.dp) {
                SettingRow(Icons.Rounded.PhoneInTalk, t("Start on every call", "हर कॉल पर शुरू करें"),
                    t("Uses call state only, never your contacts or numbers.", "सिर्फ़ कॉल की स्थिति, कोई नंबर या संपर्क नहीं।"),
                    onClick = onToggleAuto) {
                    Switch(checked = autoOn, onCheckedChange = { onToggleAuto() })
                }
            }
            GradientButton(t("Start listening now", "अभी सुनना शुरू करें"), onStartMic, icon = Icons.Rounded.Mic)
            Text(t("Speech is turned into text on this phone only; no audio is recorded or uploaded.",
                "आवाज़ इसी फ़ोन पर टेक्स्ट में बदली जाती है; कुछ भी रिकॉर्ड या अपलोड नहीं होता।"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

            st.lastSummary?.let { v ->
                val lc = levelColors(v.level)
                SectionLabel(t("Last call", "पिछली कॉल"))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(lc.container).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(levelIcon(v.level), Color.White, background = lc.fg)
                        Spacer(Modifier.width(12.dp))
                        Text(levelLabel(v.level), style = MaterialTheme.typography.titleLarge, color = lc.onContainer)
                    }
                    v.reasons.forEach { ReasonRow(if (hindi) it.textHi ?: it.textEn else it.textEn, lc.fg, lc.onContainer) }
                    if (v.level != Level.SAFE) Text(t("If you shared an OTP or paid, call 1930 now.", "अगर OTP बताया या पैसे भेजे, तो अभी 1930 पर कॉल करें।"),
                        style = MaterialTheme.typography.titleSmall, color = lc.onContainer)
                }
            }

            SectionLabel(t("Try a demo call (no microphone)", "डेमो कॉल आज़माएँ (बिना माइक)"))
            scripts.forEach { s ->
                val tint = when (s.kind) { "scam" -> danger.fg; "normal" -> levelColors(Level.SAFE).fg; else -> levelColors(Level.SUSPICIOUS).fg }
                AppCard(Modifier.clickable { onScript(s) }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.PlayArrow, tint, size = 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                            Text(when (s.kind) { "scam" -> t("Scam call", "स्कैम कॉल"); "normal" -> t("Normal call", "सामान्य कॉल")
                                else -> t("Genuine service call", "असली सेवा कॉल") },
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