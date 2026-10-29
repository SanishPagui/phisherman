package com.phisherman.link

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.phisherman.data.DecisionLog
import com.phisherman.engine.Level
import com.phisherman.ui.WarningActivity
import kotlinx.coroutines.launch

/**
 * Invisible entry point for link taps (VIEW, when Phisherman is the default browser) and links
 * shared to the app (SEND). Scores the link before any page loads, then forwards or warns.
 */
class LinkRouterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = extractUrl(intent)
        if (url == null) {
            if (intent?.action == Intent.ACTION_SEND) Toast.makeText(this, "No web link found to check", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            val off = !com.phisherman.data.Prefs.enabled(this@LinkRouterActivity, com.phisherman.data.Prefs.Feature.LINKS)
            if (off || LinkAnalyser.recentlyOpenedAnyway(url)) {
                forward(url)
                return@launch
            }
            val verdict = LinkAnalyser.analyse(this@LinkRouterActivity, url)
            when {
                verdict == null -> finish()
                verdict.level == Level.SAFE -> {
                    DecisionLog.recordAsync(this@LinkRouterActivity, verdict, "FORWARDED")
                    forward(url)
                }
                else -> {
                    startActivity(WarningActivity.intent(this@LinkRouterActivity, url, verdict))
                    finish()
                }
            }
        }
    }

    private fun forward(url: String) {
        if (!Forwarder.openInRealBrowser(this, url)) {
            Toast.makeText(this, "No other browser installed. Install Chrome to open links.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        private const val MAX_URL = 8192
        private val URL_IN_TEXT = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

        /** Only http(s), only reasonable lengths; anything else is ignored. */
        fun extractUrl(intent: Intent?): String? {
            val raw = when (intent?.action) {
                Intent.ACTION_VIEW -> intent.dataString
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { URL_IN_TEXT.find(it)?.value }
                else -> null
            } ?: return null
            val scheme = raw.substringBefore(':').lowercase()
            if (scheme != "http" && scheme != "https") return null
            return raw.takeIf { it.length <= MAX_URL }
        }
    }
}
