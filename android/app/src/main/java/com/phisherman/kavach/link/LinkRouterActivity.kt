package com.phisherman.kavach.link

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.phisherman.kavach.data.DecisionLog
import com.phisherman.kavach.engine.Channel
import com.phisherman.kavach.engine.Engine
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.ui.VerdictExtras
import com.phisherman.kavach.ui.WarningActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Intercepts http/https taps from any app (PS4 Section 7.2). The decision happens BEFORE
 * any page loads: safe links flash straight through to the real browser; risky links open
 * the warning screen. Translucent theme means a safe link shows only a brief flash.
 */
class LinkRouterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        // Reject anything that is not http/https (ignore tel:, upi:, intent: — Section 12.1).
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https")) {
            finish()
            return
        }

        lifecycleScope.launch {
            if (!Engine.initialised) {
                withContext(Dispatchers.Default) { Engine.init(applicationContext) }
            }
            val url = uri.toString()
            val verdict = withContext(Dispatchers.Default) { Engine.scoreUrl(url) }
            val id = DecisionLog.record(this@LinkRouterActivity, verdict, uri.host ?: verdict.subject)

            if (verdict.level == Level.SAFE) {
                val ok = Forwarder.openInRealBrowser(this@LinkRouterActivity, uri)
                if (!ok) {
                    Toast.makeText(
                        this@LinkRouterActivity,
                        "No browser available to open this safe link",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val warn = VerdictExtras.put(
                    Intent(this@LinkRouterActivity, WarningActivity::class.java),
                    verdict, url = url, decisionId = id
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(warn)
            }
            finish()
        }
    }
}
