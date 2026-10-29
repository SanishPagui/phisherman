package com.phisherman.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.phisherman.data.DecisionLog
import com.phisherman.data.Prefs
import com.phisherman.engine.Channel
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.link.Forwarder
import com.phisherman.link.LinkAnalyser
import com.phisherman.ui.theme.AppSettings
import com.phisherman.ui.theme.PhishermanTheme
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Full-screen warning shown before a risky link opens. Go back is the default. */
class WarningActivity : ComponentActivity() {
    private var decisionId = 0L
    private var actionTaken = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val verdict = Json.decodeFromString<Verdict>(intent.getStringExtra(EXTRA_VERDICT) ?: return finish())
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val body = intent.getStringExtra(EXTRA_BODY)
        if (url.isEmpty() && body == null) return finish()
        decisionId = savedInstanceState?.getLong(STATE_ID) ?: 0L
        // SMS verdicts are already logged by the receiver; link warnings log here
        if (decisionId == 0L && verdict.channel == Channel.LINK) {
            lifecycleScope.launch { decisionId = DecisionLog.record(this@WarningActivity, verdict) }
        }

        setContent {
            // the warning keeps its own red / amber colours; the theme only styles the dialog
            PhishermanTheme {
            WarningScreen(
                url = url, body = body, verdict = verdict, hindi = AppSettings.hindi,
                onToggleLanguage = { AppSettings.setHindi(this, !AppSettings.hindi) },
                onGoBack = { act("BACK"); finish() },
                onOpenAnyway = {
                    act("OPEN_ANYWAY")
                    LinkAnalyser.markOpenedAnyway(url)
                    if (!Forwarder.openInRealBrowser(this, url)) {
                        Toast.makeText(this, "No other browser installed", Toast.LENGTH_LONG).show()
                    }
                    finish()
                },
            )
            }
        }
    }

    private fun act(action: String) {
        actionTaken = true
        DecisionLog.setActionAsync(this, decisionId, action)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_ID, decisionId)
    }

    override fun onDestroy() {
        if (isFinishing && !actionTaken) DecisionLog.setActionAsync(this, decisionId, "BACK")
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_VERDICT = "verdict"
        private const val EXTRA_BODY = "body"
        private const val STATE_ID = "decision_id"

        /** Warning for a message (opened from the SMS notification or the paste box). */
        fun smsIntent(ctx: Context, body: String, verdict: Verdict): Intent =
            Intent(ctx, WarningActivity::class.java)
                .putExtra(EXTRA_BODY, body.take(1000))
                .putExtra(EXTRA_VERDICT, Json.encodeToString(Verdict.serializer(), verdict))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        fun intent(ctx: Context, url: String, verdict: Verdict): Intent =
            Intent(ctx, WarningActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_VERDICT, Json.encodeToString(Verdict.serializer(), verdict))
                // replace any older warning still in the background; otherwise Android would bring
                // the old one (same component, extras ignored) to the front for a new link
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}


private val DangerTop = Color(0xFFE5484D)
private val DangerBottom = Color(0xFF7F0D14)
private val AmberTop = Color(0xFFD98A00)
private val AmberBottom = Color(0xFF6B3F00)

/** Red (dangerous) or amber (suspicious) full-screen warning. The safest action is the big button. */
@Composable
private fun WarningScreen(
    url: String,
    body: String?,
    verdict: Verdict,
    hindi: Boolean,
    onToggleLanguage: () -> Unit,
    onGoBack: () -> Unit,
    onOpenAnyway: () -> Unit,
) {
    val dangerous = verdict.level == Level.DANGEROUS
    val top = if (dangerous) DangerTop else AmberTop
    val bottom = if (dangerous) DangerBottom else AmberBottom
    var confirm by remember { mutableStateOf(false) }
    BackHandler(onBack = onGoBack)

    val t = { en: String, hi: String -> if (hindi) hi else en }
    val isMessage = verdict.channel != Channel.LINK
    val host = verdict.subject
    val reg = if (isMessage) host else LinkAnalyser.registrableDomain(host)
    val prefix = if (isMessage) "" else host.removeSuffix(reg)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
    )

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Phisherman", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(if (hindi) "English" else "हिंदी", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onToggleLanguage).padding(horizontal = 14.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(132.dp).scale(pulse).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (dangerous) Icons.Rounded.GppBad else Icons.Rounded.GppMaybe, null, tint = Color.White, modifier = Modifier.size(58.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                isMessage && dangerous -> t("SCAM ALERT", "धोखाधड़ी चेतावनी")
                isMessage -> t("SUSPICIOUS MESSAGE", "संदिग्ध संदेश")
                dangerous -> t("DANGEROUS LINK BLOCKED", "ख़तरनाक लिंक रोका गया")
                else -> t("THIS LINK LOOKS UNSAFE", "यह लिंक असुरक्षित लगता है")
            },
            color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp,
            letterSpacing = 1.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isMessage) t("This message looks like a scam", "यह संदेश स्कैम जैसा लगता है")
            else t("We stopped this link before it opened", "हमने यह लिंक खुलने से पहले रोक दिया"),
            color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, textAlign = TextAlign.Center,
        )

        // where the link really goes / who sent it: the registrable domain in bold
        Spacer(Modifier.height(18.dp))
        Text(
            buildAnnotatedString {
                if (isMessage) withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f))) { append(t("From ", "भेजने वाला ")) }
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f))) { append(prefix) }
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(reg.ifEmpty { host }) }
            },
            fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )

        if (verdict.reasons.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(22.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(t("WHY WE WARNED YOU", "हमने चेतावनी क्यों दी"), color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                verdict.reasons.take(3).forEach { r ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 8.dp).size(8.dp).background(Color.White, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(if (hindi) (r.textHi ?: r.textEn) else r.textEn, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp)
                    }
                }
            }
        }

        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(body.take(300), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(14.dp))
        }

        Spacer(Modifier.height(18.dp))
        Text(
            if (isMessage) t("Do NOT click links, call back, or share any OTP, PIN or card details.",
                "लिंक पर क्लिक न करें, वापस कॉल न करें, और OTP, PIN या कार्ड की जानकारी न दें।")
            else t("Never enter your OTP, PIN, password or card details on this site.",
                "इस साइट पर कभी भी अपना OTP, PIN, पासवर्ड या कार्ड की जानकारी न डालें।"),
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGoBack, modifier = Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = bottom),
        ) {
            Text(if (isMessage) t("I understand — I won't respond", "समझ गया — जवाब नहीं दूँगा") else t("Go back (safe)", "वापस जाएँ (सुरक्षित)"),
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (!isMessage) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { confirm = true }) {
                Text(t("Open anyway", "फिर भी खोलें"), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t("Open this link anyway?", "फिर भी यह लिंक खोलें?")) },
            text = { Text(t("Never enter your OTP, PIN, password or card details on this site.",
                "इस साइट पर कभी भी अपना OTP, PIN, पासवर्ड या कार्ड की जानकारी न डालें।")) },
            confirmButton = { TextButton(onClick = { confirm = false; onOpenAnyway() }) { Text(t("Open anyway", "फिर भी खोलें")) } },
            dismissButton = { Button(onClick = { confirm = false; onGoBack() }) { Text(t("Go back", "वापस जाएँ")) } },
        )
    }
}