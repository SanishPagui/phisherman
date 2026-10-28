package com.phisherman.kavach.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.phisherman.kavach.data.DecisionLog
import com.phisherman.kavach.data.Prefs
import com.phisherman.kavach.link.Forwarder
import com.phisherman.kavach.ui.screens.WarningScreen
import com.phisherman.kavach.ui.theme.PhishermanTheme

/** Full-screen warning shown before a risky link opens (PS4 Section 7.4). */
class WarningActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val verdict = VerdictExtras.read(intent)
        val hindi = Prefs.hindi(this)

        setContent {
            PhishermanTheme(hindi = hindi) {
                WarningScreen(
                    verdict = verdict,
                    onGoBack = {
                        logAction(verdict.decisionId, "BACK")
                        finish()
                    },
                    onOpenAnyway = {
                        logAction(verdict.decisionId, "OPEN_ANYWAY")
                        val url = verdict.url
                        if (url != null) {
                            val ok = Forwarder.openInRealBrowser(this, Uri.parse(url))
                            if (!ok) Toast.makeText(this, "No browser available", Toast.LENGTH_LONG).show()
                        }
                        finish()
                    },
                    onReport = {
                        logAction(verdict.decisionId, "REPORTED")
                        startActivity(
                            VerdictExtras.put(
                                Intent(this, ReportActivity::class.java), toVerdictLike(verdict),
                                url = verdict.url, decisionId = verdict.decisionId,
                            )
                        )
                    },
                )
            }
        }
    }

    private fun logAction(id: Long, action: String) {
        if (id > 0) DecisionLog.setActionAsync(this, id, action)
    }

    // ReportActivity reads the same extras, so re-pack the display verdict as-is.
    private fun toVerdictLike(v: UiVerdict) = com.phisherman.kavach.engine.Verdict(
        channel = v.channel,
        score = v.score,
        level = v.level,
        reasons = v.reasonsEn.mapIndexed { i, en ->
            com.phisherman.kavach.engine.Reason("r$i", en, v.reasonsHi.getOrNull(i)?.ifEmpty { null })
        },
        subject = v.subject,
    )
}
