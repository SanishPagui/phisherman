package com.phisherman.kavach.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirrors assets/results.json produced by ml/eval_report.py (PS4 Section 11.3). */
@Serializable
data class Results(
    val generated: String = "",
    val dataNote: String = "",
    val link: LinkResults = LinkResults(),
    val sms: SmsResults = SmsResults(),
    val voice: VoiceResults = VoiceResults(),
    val latencyMs: Latency = Latency(),
) {
    @Serializable
    data class Metric(
        val precision: Double = 0.0,
        val recall: Double = 0.0,
        val fpr: Double = 0.0,
        val f1: Double = 0.0,
        val n: Int = 0,
        val tp: Int = 0, val fp: Int = 0, val fn: Int = 0, val tn: Int = 0,
    )

    @Serializable
    data class LinkResults(
        val dataset: String = "",
        val threshold: Double? = null,
        val test: Metric = Metric(),
        val unseen: Metric = Metric(),
    )

    @Serializable
    data class SmsResults(
        val dataset: String = "",
        val nSynthetic: Int = 0,
        val test: Metric = Metric(),
    )

    @Serializable
    data class VoiceResults(
        val dataset: String = "",
        val note: String = "",
        val avgTimeToRedSeconds: Double? = null,
    )

    @Serializable
    data class Latency(
        val linkP50: Double? = null, val linkP95: Double? = null,
        val smsP50: Double? = null, val smsP95: Double? = null,
        val note: String = "",
    )

    companion object {
        fun load(ctx: Context): Results = runCatching {
            val json = Json { ignoreUnknownKeys = true }
            ctx.assets.open("results.json").bufferedReader().use {
                json.decodeFromString(serializer(), it.readText())
            }
        }.getOrDefault(Results())
    }
}
