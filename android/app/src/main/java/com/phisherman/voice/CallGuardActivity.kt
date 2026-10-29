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
                var hindi by remember { mutableStateOf(Prefs.hindi(this)) }
                GuardScreen(st, scripts, hindi, autoOn, ::toggleAuto,
                    onToggleLanguage = { hindi = !hindi; Prefs.setHindi(this, hindi) },
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

private val Green = Color(0xFF4CC38A)
private val Amber = Color(0xFFF0A940)
private val Red = Color(0xFFE5534B)

@Composable
private fun GuardScreen(
    st: GuardState, scripts: List<DemoScript>, hindi: Boolean, autoOn: Boolean, onToggleAuto: () -> Unit,
    onToggleLanguage: () -> Unit, onStartMic: () -> Unit, onScript: (DemoScript) -> Unit, onStop: () -> Unit, onAck: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val color = when (st.level) { Level.DANGEROUS -> Red; Level.SUSPICIOUS -> Amber; Level.SAFE -> Green }
    val running = st.mode != GuardMode.OFF

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(t("Call guard", "कॉल गार्ड"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onToggleLanguage) { Text(if (hindi) "English" else "हिंदी") }
        }

        if (running && st.level == Level.DANGEROUS) {
            Column(Modifier.fillMaxWidth().background(Red, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("⚠ Hang up. Never share OTP or PIN.", "⚠ फोन काटें। OTP या PIN कभी न बताएं।"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(t("This call sounds like a scam.", "यह कॉल धोखाधड़ी जैसी लग रही है।"), color = Color.White)
                if (st.alerting) Button(onClick = onAck, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Red)) {
                    Text(t("I'm safe, stop alert", "मैं सुरक्षित हूँ, अलर्ट बंद करें"))
                }
            }
        } else if (running && st.level == Level.SUSPICIOUS) {
            Text(t("Careful: warning signs heard", "सावधान: चेतावनी के संकेत"), color = Amber, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (running) t("Risk ${st.score}/100 · ${st.level.name.lowercase()}", "जोखिम ${st.score}/100") else t("Not listening", "सुन नहीं रहा"),
                    fontWeight = FontWeight.SemiBold, color = if (running) color else Color.Gray,
                )
                LinearProgressIndicator(progress = { st.score / 100f }, modifier = Modifier.fillMaxWidth().height(12.dp), color = color)
                if (st.heard.isNotEmpty()) {
                    Text(t("Heard:", "सुना गया:"), fontWeight = FontWeight.SemiBold)
                    CallGuard.reasonsFor(ctx, st.heard).forEach { Text("•  " + if (hindi) (it.textHi ?: it.textEn) else it.textEn) }
                }
                st.llmReason?.let { Text(t("On-device AI: $it", "ऑन-डिवाइस AI: $it"), color = color) }
                if (st.mode == GuardMode.MIC) {
                    // diagnostic: stays near -2 dB or lower if Android is giving us silence during a call
                    Text(t("Microphone level: %.0f dB".format(st.peakDb), "माइक स्तर: %.0f dB".format(st.peakDb)),
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                st.error?.let { Text(it, color = Red) }
            }
        }

        if (running) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("What was said", "क्या कहा गया"), fontWeight = FontWeight.SemiBold)
                    st.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (st.partial.isNotBlank()) Text(st.partial + " …", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(t("Stop call guard", "कॉल गार्ड बंद करें"), fontSize = 18.sp)
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Start automatically on every call", "हर कॉल पर अपने आप शुरू करें"), fontWeight = FontWeight.SemiBold)
                        Text(t("Uses call state only, never your contacts or numbers.", "सिर्फ़ कॉल की स्थिति, कोई नंबर या संपर्क नहीं।"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(checked = autoOn, onCheckedChange = { onToggleAuto() })
                }
            }
            Button(onClick = onStartMic, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(t("Start listening (speakerphone)", "सुनना शुरू करें (स्पीकरफोन)"), fontSize = 18.sp)
            }
            Text(t("Put the call on speaker near this phone. Speech is turned into text on this phone only; no audio is recorded or uploaded.",
                "कॉल को स्पीकर पर रखें। आवाज़ इसी फोन पर टेक्स्ट में बदली जाती है; कुछ भी रिकॉर्ड या अपलोड नहीं होता।"),
                style = MaterialTheme.typography.bodySmall)
            st.lastSummary?.let { v ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t("Last call: ${v.level.name.lowercase()} (risk ${v.score})", "पिछली कॉल: ${v.level.name.lowercase()}"), fontWeight = FontWeight.SemiBold,
                            color = when (v.level) { Level.DANGEROUS -> Red; Level.SUSPICIOUS -> Amber; Level.SAFE -> Green })
                        v.reasons.forEach { Text("•  " + if (hindi) (it.textHi ?: it.textEn) else it.textEn) }
                        if (v.level != Level.SAFE) Text(t("If you shared an OTP or paid, call 1930 now.", "अगर OTP बताया या पैसे भेजे, तो अभी 1930 पर कॉल करें।"), color = Red)
                    }
                }
            }
            Text(t("Demo scripts (no microphone)", "डेमो स्क्रिप्ट (बिना माइक)"), fontWeight = FontWeight.SemiBold)
            scripts.forEach { s ->
                OutlinedButton(onClick = { onScript(s) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${s.id} · ${s.title}", color = if (s.kind == "scam") Red else Green)
                }
            }
        }
    }
}
