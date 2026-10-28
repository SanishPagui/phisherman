package com.phisherman.kavach.ui

import android.content.Intent
import com.phisherman.kavach.engine.Channel
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.engine.Verdict

/**
 * Lightweight, display-only verdict that survives an Intent boundary (WarningActivity,
 * ReportActivity, notifications). Verdict itself stays in-process; only what the screens
 * need is marshalled as primitive extras.
 */
data class UiVerdict(
    val channel: Channel,
    val level: Level,
    val score: Int,
    val subject: String,
    val url: String?,
    val reasonsEn: List<String>,
    val reasonsHi: List<String>,
    val decisionId: Long,
) {
    fun reasons(hindi: Boolean): List<String> =
        if (hindi) reasonsHi.mapIndexed { i, hi -> hi.ifEmpty { reasonsEn.getOrElse(i) { "" } } }
        else reasonsEn
}

object VerdictExtras {
    private const val CHANNEL = "v_channel"
    private const val LEVEL = "v_level"
    private const val SCORE = "v_score"
    private const val SUBJECT = "v_subject"
    private const val URL = "v_url"
    private const val REASON_EN = "v_reason_en"
    private const val REASON_HI = "v_reason_hi"
    private const val DECISION_ID = "v_decision_id"

    fun put(intent: Intent, verdict: Verdict, url: String? = null, decisionId: Long = -1L): Intent {
        intent.putExtra(CHANNEL, verdict.channel.name)
        intent.putExtra(LEVEL, verdict.level.name)
        intent.putExtra(SCORE, verdict.score)
        intent.putExtra(SUBJECT, verdict.subject)
        url?.let { intent.putExtra(URL, it) }
        intent.putStringArrayListExtra(REASON_EN, ArrayList(verdict.reasons.map { it.textEn }))
        intent.putStringArrayListExtra(REASON_HI, ArrayList(verdict.reasons.map { it.textHi ?: "" }))
        intent.putExtra(DECISION_ID, decisionId)
        return intent
    }

    fun read(intent: Intent): UiVerdict = UiVerdict(
        channel = runCatching { Channel.valueOf(intent.getStringExtra(CHANNEL) ?: "LINK") }.getOrDefault(Channel.LINK),
        level = runCatching { Level.valueOf(intent.getStringExtra(LEVEL) ?: "SUSPICIOUS") }.getOrDefault(Level.SUSPICIOUS),
        score = intent.getIntExtra(SCORE, 0),
        subject = intent.getStringExtra(SUBJECT) ?: "",
        url = intent.getStringExtra(URL),
        reasonsEn = intent.getStringArrayListExtra(REASON_EN) ?: arrayListOf(),
        reasonsHi = intent.getStringArrayListExtra(REASON_HI) ?: arrayListOf(),
        decisionId = intent.getLongExtra(DECISION_ID, -1L),
    )
}
