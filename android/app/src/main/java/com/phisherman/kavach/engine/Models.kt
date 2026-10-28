package com.phisherman.kavach.engine

/**
 * Shared result contract (PS4 Section 3.2). Every analyser — link, SMS, voice —
 * returns a [Verdict], so the warning screen, the decision log and the metrics code
 * are written exactly once and never change when a model changes.
 */

enum class Channel { LINK, SMS, VOICE }

/** Ordered by severity; [ordinal] is used for max() combination. */
enum class Level { SAFE, SUSPICIOUS, DANGEROUS }

/**
 * One user-readable reason. Bilingual: [textHi] may be null if no Hindi string exists,
 * in which case the UI falls back to [textEn].
 */
data class Reason(
    val id: String,
    val textEn: String,
    val textHi: String? = null,
) {
    fun text(hindi: Boolean): String = if (hindi) (textHi ?: textEn) else textEn
}

data class Verdict(
    val channel: Channel,
    val score: Int,                 // 0..100, higher = riskier
    val level: Level,
    val reasons: List<Reason>,      // 1..4, strongest first
    val modelProb: Float? = null,   // raw model probability if a model was used
    val ruleHits: List<String> = emptyList(), // rule ids that fired (log/debug)
    val latencyMs: Long = 0,
    val subject: String = "",       // host / sender / "call" — for the log and UI
) {
    val isSafe: Boolean get() = level == Level.SAFE

    companion object {
        fun safe(channel: Channel, subject: String, reason: Reason? = null, latencyMs: Long = 0) =
            Verdict(
                channel = channel,
                score = 0,
                level = Level.SAFE,
                reasons = listOfNotNull(reason),
                latencyMs = latencyMs,
                subject = subject,
            )
    }
}

/** Pick the higher of two levels (used when an SMS contains a link). */
fun maxLevel(a: Level, b: Level): Level = if (a.ordinal >= b.ordinal) a else b
