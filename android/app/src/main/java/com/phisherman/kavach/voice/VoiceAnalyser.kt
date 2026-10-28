package com.phisherman.kavach.voice

import android.content.Context
import com.phisherman.kavach.engine.Channel
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.engine.Reason
import com.phisherman.kavach.engine.Reasons
import com.phisherman.kavach.engine.Verdict
import com.phisherman.kavach.engine.maxLevel
import kotlinx.serialization.json.Json

/**
 * Stateful scorer for one call session (PS4 Section 9.3). Matches phrase categories in a
 * rolling transcript window; the score rises as the scam script unfolds. Hysteresis keeps
 * the warning from flickering: once DANGEROUS, it stays DANGEROUS for the rest of the call.
 */
object VoiceAnalyser {

    private var rules: VoiceRules = VoiceRules.EMPTY
    private var sticky: Level = Level.SAFE

    fun init(ctx: Context) {
        rules = runCatching {
            val json = Json { ignoreUnknownKeys = true }
            ctx.assets.open("voice_rules.json").bufferedReader().use {
                json.decodeFromString(VoiceRules.serializer(), it.readText())
            }
        }.getOrDefault(VoiceRules.EMPTY)
    }

    /** Call at the start of each new call session. */
    fun reset() { sticky = Level.SAFE }

    val windowSeconds: Int get() = rules.windowSeconds

    fun update(windowText: String): Verdict {
        val lower = windowText.lowercase()
        val present = rules.categories.filter { cat ->
            cat.patterns.any { lower.contains(it.lowercase()) }
        }
        var score = present.sumOf { it.weight }

        rules.combinationBonus?.let { cb ->
            val ids = present.map { it.id }.toSet()
            if (ids.any { it in cb.requireAny } && ids.any { it in cb.withAny }) {
                score += cb.value
            }
        }
        score = score.coerceIn(0, 100)

        val raw = when {
            score >= rules.bands.dangerous -> Level.DANGEROUS
            score >= rules.bands.suspicious -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
        sticky = maxLevel(sticky, raw) // hysteresis
        val level = sticky

        val reasons: List<Reason> = present
            .sortedByDescending { it.weight }
            .take(3)
            .map { Reasons.voiceCategory(it.label, it.labelHi) }

        return Verdict(
            channel = Channel.VOICE,
            score = score,
            level = level,
            reasons = if (level == Level.SAFE) emptyList() else reasons,
            ruleHits = present.map { it.id },
            subject = "call",
        )
    }
}
