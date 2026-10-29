package com.phisherman.ui

import android.content.Context
import com.phisherman.engine.Level
import com.phisherman.voice.CallGuard
import com.phisherman.voice.VoiceScorer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Confusion-matrix metrics for one test set (positive class = phishing). */
@Serializable
data class Metric(
    val precision: Double = 0.0,
    val recall: Double = 0.0,      // = fraud detection rate
    val fpr: Double = 0.0,
    val f1: Double = 0.0,
    val n: Int = 0,
    @SerialName("n_phishing") val nPhishing: Int = 0,
    val tp: Int = 0, val fp: Int = 0, val fn: Int = 0, val tn: Int = 0,
) {
    companion object {
        fun of(tp: Int, fp: Int, fn: Int, tn: Int): Metric {
            fun div(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b
            val p = div(tp, tp + fp)
            val r = div(tp, tp + fn)
            return Metric(p, r, div(fp, fp + tn), if (p + r == 0.0) 0.0 else 2 * p * r / (p + r),
                tp + fp + fn + tn, tp + fn, tp, fp, fn, tn)
        }
    }
}

@Serializable
data class HardNegatives(val n: Int = 0, @SerialName("fpr_engine") val fprEngine: Double = 0.0)

@Serializable
data class LinkResults(
    val model: String = "",
    val dataset: String = "",
    @SerialName("unseen_dataset") val unseenDataset: String = "",
    val test: Metric = Metric(),
    @SerialName("test_dangerous") val testDangerous: Metric = Metric(),
    val unseen: Metric = Metric(),
    @SerialName("hard_negatives") val hardNegatives: HardNegatives = HardNegatives(),
)

@Serializable
data class SmsResults(
    val dataset: String = "",
    val test: Metric = Metric(),
    val uci: Metric = Metric(),
    val india: Metric = Metric(),
)

/** assets/results.json, written by ml/export_app_results.py from the training reports. */
@Serializable
data class AppResults(
    val generated: String = "",
    val link: LinkResults = LinkResults(),
    val sms: SmsResults = SmsResults(),
) {
    companion object {
        fun load(ctx: Context): AppResults = runCatching {
            ctx.assets.open("results.json").bufferedReader().use {
                Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), it.readText())
            }
        }.getOrDefault(AppResults())

        /**
         * Voice is measured live: every script in voice_scripts.tsv is scored with the same
         * VoiceScorer the call guard uses. "scam" scripts are positives, all others negatives.
         */
        fun voice(ctx: Context): Metric = runCatching {
            val scorer = VoiceScorer(CallGuard.rules(ctx))
            var tp = 0; var fp = 0; var fn = 0; var tn = 0
            ctx.assets.open("voice_scripts.tsv").bufferedReader(Charsets.UTF_8).readLines().drop(1)
                .filter { it.isNotBlank() }.map { it.split("\t") }.forEach { cols ->
                    val scam = cols[1] == "scam"
                    val text = cols[3].split(" || ").joinToString(" ") { it.removePrefix("[PAY] ") }
                    val flagged = scorer.score(text).level != Level.SAFE
                    when {
                        scam && flagged -> tp++
                        scam -> fn++
                        flagged -> fp++
                        else -> tn++
                    }
                }
            Metric.of(tp, fp, fn, tn)
        }.getOrDefault(Metric())
    }
}
