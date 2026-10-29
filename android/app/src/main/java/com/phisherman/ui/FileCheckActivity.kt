package com.phisherman.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phisherman.data.DecisionLog
import com.phisherman.engine.Channel
import com.phisherman.engine.Level
import com.phisherman.engine.Reason
import com.phisherman.engine.Verdict
import com.phisherman.ui.theme.PhishermanTheme
import com.phisherman.ui.theme.tr
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import java.util.zip.ZipInputStream

/**
 * Checks a file's real contents, not its name: an Android app (APK) renamed to .pdf / .jpg is
 * still a ZIP with AndroidManifest.xml and classes.dex inside. Opened by sharing a file to
 * Phisherman (e.g. from WhatsApp) or from "Check a file" on the home screen. Read on the phone only.
 */
class FileCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> intent?.data
        }
        val result = uri?.let { runCatching { FileInspector.inspect(this, it) }.getOrNull() }
        result?.let { DecisionLog.recordAsync(this, it.verdict) }
        setContent { PhishermanTheme { FileResult(result) { finish() } } }
    }
}

data class FileCheck(val name: String, val verdict: Verdict, val title: String, val detail: String)

object FileInspector {
    private val APK_EXT = setOf("apk", "xapk", "apks", "apkm")

    fun inspect(ctx: Context, uri: Uri): FileCheck {
        val name = displayName(ctx, uri) ?: uri.lastPathSegment ?: "file"
        val ext = name.substringAfterLast('.', "").lowercase()
        val head = ByteArray(8)
        val read = ctx.contentResolver.openInputStream(uri)!!.use { it.read(head) }
        val isZip = read >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2].toInt() == 3 && head[3].toInt() == 4
        val isApk = isZip && ctx.contentResolver.openInputStream(uri)!!.use { looksLikeApk(ZipInputStream(it)) }
        val isPdf = read >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "%PDF"

        fun v(level: Level, id: String, en: String, hi: String) =
            Verdict(Channel.FILE, if (level == Level.DANGEROUS) 99 else if (level == Level.SUSPICIOUS) 60 else 5, level,
                listOf(Reason(id, en, hi)), null, listOf(id), 0, name)
        return when {
            isApk && ext !in APK_EXT -> FileCheck(name, v(Level.DANGEROUS, "apk_disguised",
                "An Android app pretending to be a .$ext file", ".$ext जैसी दिखने वाली Android ऐप"),
                "Fake file: this is really an app", "\"$name\" looks like a .$ext file but it is an Android app (APK). Do NOT open or install it.")
            isApk -> FileCheck(name, v(Level.DANGEROUS, "apk_file", "An Android app file (.apk)", "Android ऐप फ़ाइल (.apk)"),
                "This is an app file (APK)", "Banks, KYC teams and customer care never send apps on WhatsApp, SMS or email. Do not install it.")
            ext == "pdf" && !isPdf -> FileCheck(name, v(Level.SUSPICIOUS, "fake_pdf", "Named .pdf but is not a PDF", ".pdf नाम है पर PDF नहीं है"),
                "This is not really a PDF", "The name says PDF but the contents are something else. Be careful opening it.")
            else -> FileCheck(name, v(Level.SAFE, "no_app_inside", "No app hidden inside", "अंदर कोई ऐप नहीं"),
                "No app hidden in this file", "It is not an app. Still, never enter your OTP, PIN or card details because a file told you to.")
        }
    }

    /** An APK is a ZIP containing AndroidManifest.xml and classes.dex (checks the first entries). */
    private fun looksLikeApk(zip: ZipInputStream): Boolean {
        var manifest = false; var dex = false; var n = 0
        while (n++ < 2000) {
            val e = zip.nextEntry ?: break
            if (e.name == "AndroidManifest.xml") manifest = true
            if (e.name.endsWith(".dex") || e.name.endsWith(".apk")) dex = true  // .apk inside = split / xapk bundle
            if (manifest && dex) return true
        }
        return manifest
    }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

@androidx.compose.runtime.Composable
private fun FileResult(r: FileCheck?, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val level = r?.verdict?.level ?: Level.SUSPICIOUS
    val c = levelColors(level)
    Column(
        Modifier.fillMaxSize().background(cs.background).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(tr("File check", "फ़ाइल जाँच"), tr("We look inside the file, not just its name", "हम फ़ाइल के अंदर देखते हैं, सिर्फ़ नाम नहीं"))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.container).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            ShieldOrb(levelIcon(level), c.fg, size = 110.dp)
            Spacer(Modifier.height(12.dp))
            LevelBadge(level)
            Spacer(Modifier.height(12.dp))
            Text(if (r == null) tr("Could not read this file", "यह फ़ाइल पढ़ी नहीं जा सकी") else r.title,
                style = MaterialTheme.typography.headlineSmall, color = c.onContainer, textAlign = TextAlign.Center)
            if (r != null) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.clip(RoundedCornerShape(50)).background(cs.surface.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, null, tint = c.onContainer, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(r.name, style = MaterialTheme.typography.labelLarge, color = c.onContainer, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(r?.detail ?: tr("Share the file to Phisherman again, or pick it from the home screen.",
                "फ़ाइल फिर से Phisherman पर शेयर करें, या होम स्क्रीन से चुनें।"),
                style = MaterialTheme.typography.bodyLarge, color = c.onContainer, textAlign = TextAlign.Center)
        }
        AppCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(Icons.Rounded.Lock, cs.primary, size = 40.dp)
                Text(tr("An app renamed to .pdf or .jpg is still caught. The file is checked on this phone and is not uploaded.",
                    ".pdf या .jpg नाम दी गई ऐप भी पकड़ी जाती है। फ़ाइल इसी फ़ोन पर जाँची जाती है और अपलोड नहीं होती।"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
            }
        }
        GradientButton(tr("Done", "हो गया"), onDone)
        Spacer(Modifier.height(12.dp))
    }
}