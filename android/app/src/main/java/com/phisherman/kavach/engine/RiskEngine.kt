package com.phisherman.kavach.engine

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Combines analyser outputs into a [Verdict] (PS4 Section 3.1). The model gives a
 * probability; structural brand/URL rules then raise the floor so a clean-looking
 * model score cannot pass an obvious lookalike, deep-nesting or IP-host attack
 * (PS4 Section 3.3 step 3, "then applies brand rules").
 *
 * The threshold bands can be adjusted live from the Settings slider so judges see the
 * precision/recall trade-off (PS4 Section 6.7).
 */
object RiskEngine {

    @Volatile
    var thresholds: Thresholds = Thresholds.DEFAULT

    /** Feature indices — MUST match ml/features.py FEATURE_ORDER and UrlFeatures.kt. */
    object F {
        const val URL_LEN = 0; const val HOST_LEN = 1; const val DOT_COUNT = 2
        const val SUBDOMAIN_DEPTH = 3; const val DIGIT_RATIO = 4; const val HYPHEN_HOST = 5
        const val SPECIAL_CHARS = 6; const val HAS_AT = 7; const val HAS_IP = 8
        const val IS_HTTPS = 9; const val HOST_ENTROPY = 10; const val IS_SHORTENER = 11
        const val SUSP_WORDS = 12; const val PATH_LEN = 13; const val NUM_PARAMS = 14
        const val BRAND_MIN_LEV = 15; const val BRAND_IN_SUB = 16; const val TLD_RISK = 17
    }

    /** Signals the UrlAnalyser hands over alongside the raw feature vector. */
    data class LinkSignals(
        val host: String,
        val registrable: String,
        val matchedBrand: String? = null,     // brand token the host looks like, if any
        val realDomainForBrand: String? = null, // that brand's official domain, if known
    )

    fun levelForScore(score: Int, susp: Int, danger: Int): Level = when {
        score >= danger -> Level.DANGEROUS
        score >= susp -> Level.SUSPICIOUS
        else -> Level.SAFE
    }

    /**
     * Build the link verdict from the model probability + feature vector + signals.
     */
    fun fromLink(
        prob: Float,
        f: FloatArray,
        signals: LinkSignals,
        latencyMs: Long,
    ): Verdict {
        val band = thresholds.link
        val rl = thresholds.rules
        var score = (prob * 100f).roundToInt().coerceIn(0, 100)
        var level = when {
            prob >= band.dangerous -> Level.DANGEROUS
            prob >= band.suspicious -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
        val reasons = mutableListOf<Pair<Reason, Int>>() // reason + severity weight for ordering
        val hits = mutableListOf<String>()

        fun raiseTo(min: Level) { if (min.ordinal > level.ordinal) level = min }
        fun bump(floor: Int) { if (floor > score) score = floor }

        // --- Structural rules ---
        if (f[F.HAS_IP] == 1f) {
            reasons += Reasons.IP_HOST to 85; hits += "L_IP_HOST"; raiseTo(Level.DANGEROUS); bump(rl.ipFloor)
        }
        if (f[F.HAS_AT] == 1f) {
            reasons += Reasons.AT_IN_URL to 80; hits += "L_AT_IN_URL"; raiseTo(Level.SUSPICIOUS); bump(rl.atFloor)
        }
        val deepNest = f[F.SUBDOMAIN_DEPTH] >= rl.deepNestDepth
        if (deepNest) {
            reasons += Reasons.DEEP_NESTING to 75; hits += "L_DEEP_NESTING"; raiseTo(Level.SUSPICIOUS); bump(rl.deepNestFloor)
        }
        if (f[F.BRAND_IN_SUB] == 1f) {
            reasons += Reasons.BRAND_IN_SUBDOMAIN to 78; hits += "L_BRAND_IN_SUBDOMAIN"
            raiseTo(if (deepNest) Level.DANGEROUS else Level.SUSPICIOUS); bump(rl.brandInSubFloor)
        }
        // Typosquat: label is very close to a brand token but not the official domain.
        if (signals.matchedBrand != null && f[F.BRAND_MIN_LEV] <= rl.typosquatLev) {
            reasons += Reasons.brandLookalike(signals.matchedBrand, signals.realDomainForBrand) to 90
            hits += "L_BRAND_LOOKALIKE"; raiseTo(Level.DANGEROUS); bump(rl.typosquatFloor)
        }
        if (f[F.IS_SHORTENER] == 1f) {
            reasons += Reasons.SHORTENER to 50; hits += "L_SHORTENER"; raiseTo(Level.SUSPICIOUS); bump(rl.shortenerFloor)
        }
        if (f[F.TLD_RISK] == 1f) {
            reasons += Reasons.RISKY_TLD to 40; hits += "L_RISKY_TLD"
        }
        if (f[F.HOST_ENTROPY] >= rl.highEntropy && f[F.HAS_IP] == 0f) {
            reasons += Reasons.HIGH_ENTROPY to 35; hits += "L_HIGH_ENTROPY"
        }
        if (f[F.SUSP_WORDS] >= 1f) {
            reasons += Reasons.SUSPICIOUS_WORDS to 30; hits += "L_SUSP_WORDS"
        }
        // Model-driven reason when the model itself is confident.
        if (prob >= band.suspicious) {
            reasons += Reasons.MODEL_HIGH to (20 + (prob * 20).toInt()); hits += "L_MODEL"
        }

        // Keep score consistent with the final level floor.
        if (level == Level.DANGEROUS) bump(max(score, band.dangerous.times(100).toInt()))
        if (level == Level.SUSPICIOUS) bump(max(score, band.suspicious.times(100).toInt()))

        val top = reasons.sortedByDescending { it.second }.map { it.first }.distinctBy { it.id }.take(3)
        return Verdict(
            channel = Channel.LINK,
            score = score,
            level = level,
            reasons = if (level == Level.SAFE) emptyList() else top.ifEmpty { listOf(Reasons.MODEL_HIGH) },
            modelProb = prob,
            ruleHits = hits,
            latencyMs = latencyMs,
            subject = signals.host,
        )
    }

    /**
     * Build the SMS verdict from the model probability + rule hits (reasons) assembled
     * by SmsAnalyser, plus the risk of any link found in the message.
     */
    fun fromSms(
        prob: Float,
        ruleReasons: List<Reason>,
        forcedLevel: Level?,
        linkVerdict: Verdict?,
        latencyMs: Long,
        sender: String,
    ): Verdict {
        val band = thresholds.sms
        var score = (prob * 100f).roundToInt().coerceIn(0, 100)
        var level = when {
            prob >= band.dangerous -> Level.DANGEROUS
            prob >= band.suspicious -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
        val reasons = ruleReasons.toMutableList()

        if (forcedLevel != null && forcedLevel.ordinal > level.ordinal) {
            level = forcedLevel
            score = max(score, if (forcedLevel == Level.DANGEROUS) 80 else 55)
        }
        // Any risky embedded link drives the message verdict up (take the higher).
        if (linkVerdict != null && !linkVerdict.isSafe) {
            level = maxLevel(level, linkVerdict.level)
            score = max(score, linkVerdict.score)
            reasons += Reasons.SMS_HAS_LINK_RISK
        }
        if (prob >= band.suspicious && reasons.none { it.id == "R_MODEL" }) {
            reasons += Reasons.SMS_MODEL_HIGH
        }
        if (level != Level.SAFE && reasons.isEmpty()) reasons += Reasons.SMS_MODEL_HIGH

        return Verdict(
            channel = Channel.SMS,
            score = score,
            level = level,
            reasons = reasons.distinctBy { it.id }.take(3),
            modelProb = prob,
            ruleHits = reasons.map { it.id },
            latencyMs = latencyMs,
            subject = sender,
        )
    }
}
