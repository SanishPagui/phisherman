package com.phisherman.kavach.data

import android.content.Context
import com.phisherman.kavach.engine.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin façade over the Room DAO. Records verdicts for metrics and demo history.
 * Stores only verdict metadata, never message bodies (PS4 Section 14.2).
 */
object DecisionLog {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun record(ctx: Context, verdict: Verdict, subject: String? = null, action: String? = null): Long =
        withContext(Dispatchers.IO) {
            AppDatabase.get(ctx).decisionDao().insert(
                Decision(
                    ts = System.currentTimeMillis(),
                    channel = verdict.channel.name,
                    level = verdict.level.name,
                    score = verdict.score,
                    subject = subject ?: verdict.subject,
                    reasons = verdict.reasons.joinToString(",") { it.id }
                        .ifEmpty { verdict.ruleHits.joinToString(",") },
                    userAction = action,
                )
            )
        }

    fun recordAsync(ctx: Context, verdict: Verdict, subject: String? = null) {
        scope.launch { record(ctx, verdict, subject) }
    }

    fun setActionAsync(ctx: Context, id: Long, action: String) {
        scope.launch { AppDatabase.get(ctx).decisionDao().setAction(id, action) }
    }
}
