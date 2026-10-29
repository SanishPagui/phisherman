package com.phisherman.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.phisherman.R
import com.phisherman.data.Prefs
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.link.Forwarder
import com.phisherman.ui.theme.LocalHindi
import com.phisherman.ui.theme.PhishermanTheme
import com.phisherman.ui.theme.tr
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fraud prevention (PS4 deliverable 3): report to the national cybercrime portal, call the
 * 1930 helpline, share a defanged evidence pack, and follow the recovery steps.
 */
class ReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val verdict = Json.decodeFromString<Verdict>(intent.getStringExtra(EXTRA_VERDICT) ?: return finish())
        val portal = getString(R.string.report_portal_url)
        val helpline = getString(R.string.report_helpline)
        setContent {
            PhishermanTheme(hindi = Prefs.hindi(this)) {
                ReportScreen(
                    verdict = verdict, helpline = helpline,
                    onBack = ::finish,
                    onPortal = {
                        // Open in the real browser directly; routing through Phisherman would re-check it.
                        if (!Forwarder.openInRealBrowser(this, portal)) {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portal))) }
                        }
                    },
                    onCall = { runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$helpline"))) } },
                    onCopy = { text ->
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Phisherman report", text))
                        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    onShare = { text ->
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_VERDICT = "verdict"

        fun intent(ctx: Context, verdict: Verdict): Intent =
            Intent(ctx, ReportActivity::class.java)
                .putExtra(EXTRA_VERDICT, Json.encodeToString(Verdict.serializer(), verdict))
    }
}

@Composable
private fun ReportScreen(
    verdict: Verdict, helpline: String,
    onBack: () -> Unit, onPortal: () -> Unit, onCall: () -> Unit,
    onCopy: (String) -> Unit, onShare: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val hindi = LocalHindi.current
    val c = levelColors(verdict.level)
    var raw by remember { mutableStateOf(false) }
    val evidence = remember(raw, verdict) { evidence(verdict, raw) }
    val steps = listOf(
        tr("Call your bank now and block your card / UPI", "अभी अपने बैंक को कॉल करें और कार्ड / UPI ब्लॉक करें"),
        tr("Call $helpline within the first hour — money can still be frozen", "पहले घंटे में $helpline पर कॉल करें — पैसा अभी भी रोका जा सकता है"),
        tr("Change passwords and PINs you may have entered", "जो पासवर्ड और PIN डाले हों उन्हें बदलें"),
        tr("Do not delete the message or call log — it is evidence", "संदेश या कॉल लॉग न हटाएँ — यह सबूत है"),
    )

    Column(
        Modifier.fillMaxSize().background(cs.background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(cs.surface)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = cs.onSurface)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tr("Report & recover", "रिपोर्ट और रिकवरी"), style = MaterialTheme.typography.headlineMedium, color = cs.onBackground)
                Text(tr("Stop the fraud before money is lost", "पैसा जाने से पहले धोखा रोकें"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }

        // What was flagged
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.container).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(levelIcon(verdict.level), Color.White, background = c.fg)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(verdict.subject.ifEmpty { verdict.channel.name }, style = MaterialTheme.typography.titleLarge,
                        color = c.onContainer, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    LevelBadge(verdict.level)
                }
            }
            verdict.reasons.take(3).forEach { ReasonRow(if (hindi) it.textHi ?: it.textEn else it.textEn, c.fg, c.onContainer) }
        }

        GradientButton(tr("Report on cybercrime.gov.in", "cybercrime.gov.in पर रिपोर्ट करें"), onPortal,
            icon = Icons.AutoMirrored.Rounded.OpenInNew)
        GradientButton(tr("Call $helpline cyber helpline", "$helpline साइबर हेल्पलाइन पर कॉल करें"), onCall,
            icon = Icons.Rounded.Call, brush = SolidColor(levelColors(Level.DANGEROUS).fg))

        SectionLabel(tr("If you already paid or shared a code", "यदि आपने भुगतान किया या कोड बताया"))
        AppCard(padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(levelColors(Level.SUSPICIOUS).container),
                            contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = levelColors(Level.SUSPICIOUS).onContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                    }
                }
            }
        }

        SectionLabel(tr("Evidence pack", "सबूत पैक"))
        AppCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Description, cs.primary, size = 40.dp)
                Text(tr("Attach this to your complaint", "इसे अपनी शिकायत में जोड़ें"), style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                IconButton(onClick = { onCopy(evidence) }) { Icon(Icons.Rounded.ContentCopy, tr("Copy", "कॉपी")) }
                IconButton(onClick = { onShare(evidence) }) { Icon(Icons.Rounded.Share, tr("Share", "शेयर")) }
            }
            Spacer(Modifier.height(12.dp))
            Text(evidence, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).padding(14.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = cs.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Show the real link / sender (not made safe)", "असली लिंक / भेजने वाला दिखाएँ"),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Switch(checked = raw, onCheckedChange = { raw = it })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Plain-text incident summary. Links are defanged unless the user opts in. */
private fun evidence(v: Verdict, raw: Boolean): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    val subject = if (raw) v.subject else v.subject.replace("http", "hxxp").replace(".", "[.]")
    return buildString {
        appendLine("PHISHERMAN INCIDENT SUMMARY")
        appendLine("Time: $time (local)")
        appendLine("Channel: ${v.channel.name}")
        appendLine("Verdict: ${v.level.name}")
        appendLine("Subject: $subject")
        appendLine("Flagged because:")
        v.reasons.ifEmpty { null }?.forEach { appendLine(" - ${it.textEn}") } ?: appendLine(" - model-rated risk")
        if (!raw) append("(Link / sender defanged so it cannot be clicked.)")
    }.trim()
}
