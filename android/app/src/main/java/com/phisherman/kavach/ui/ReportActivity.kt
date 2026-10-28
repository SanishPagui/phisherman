package com.phisherman.kavach.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.phisherman.kavach.data.DecisionLog
import com.phisherman.kavach.data.Prefs
import com.phisherman.kavach.ui.screens.ReportScreen
import com.phisherman.kavach.ui.theme.PhishermanTheme

/**
 * Report & recovery (PS4 Section 10). Opens the national cybercrime portal and the 1930
 * dialler. Uses ACTION_DIAL (not a direct call) so no phone permission is needed and the
 * user stays in control.
 */
class ReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val verdict = VerdictExtras.read(intent)
        if (verdict.decisionId > 0) DecisionLog.setActionAsync(this, verdict.decisionId, "REPORTED")

        setContent {
            PhishermanTheme(hindi = Prefs.hindi(this)) {
                ReportScreen(
                    verdict = verdict,
                    onBack = { finish() },
                    onReportPortal = { open(com.phisherman.kavach.data.AppConfig.get(this).portalUrl) },
                    onCall1930 = { dial(com.phisherman.kavach.data.AppConfig.get(this).helplineNumber) },
                    onCopy = { copy(it) },
                    onShare = { share(it) },
                )
            }
        }
    }

    private fun open(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast("Could not open browser") }
    }

    private fun dial(number: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }.onFailure { toast("Could not open dialler") }
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Phisherman evidence", text))
        toast("Copied")
    }

    private fun share(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                null,
            )
        )
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
