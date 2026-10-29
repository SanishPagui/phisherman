package com.phisherman.ui

import android.content.Context
import com.phisherman.engine.Level
import com.phisherman.link.LinkAnalyser
import com.phisherman.sms.SmsAnalyser
import com.phisherman.voice.CallGuard
import com.phisherman.voice.CallSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Confusion counts with the same formulas as summary() in ml/eval_report.py (positive = scam). */
data class Confusion(val tp: Int = 0, val fp: Int = 0, val fn: Int = 0, val tn: Int = 0) {
    val n get() = tp + fp + fn + tn
    val precision get() = tp.toDouble() / maxOf(tp + fp, 1)
    val recall get() = tp.toDouble() / maxOf(tp + fn, 1)
    /** Fraud detection rate: share of fraud attempts detected; the same number as recall. */
    val fdr get() = recall
    val accuracy get() = (tp + tn).toDouble() / maxOf(n, 1)
    val fpr get() = fp.toDouble() / maxOf(fp + tn, 1)

    fun add(scam: Boolean, flagged: Boolean) = when {
        scam && flagged -> copy(tp = tp + 1)
        scam -> copy(fn = fn + 1)
        flagged -> copy(fp = fp + 1)
        else -> copy(tn = tn + 1)
    }
}

/** One channel's benchmark: the phone's result, Python's on the same rows (null for calls). */
data class BenchResult(
    val channel: String,
    val phone: Confusion,
    val python: Confusion?,
    val agree: Int,        // rows where the phone gave exactly the same level as Python
    val p50Ms: Double,
    val p95Ms: Double,
)

/**
 * "Run on this phone": scores assets/bench.tsv (written by ml/bench_export.py, with Python's
 * verdicts) through the app's own engines, plus the scripted calls in voice_scripts.tsv through
 * the call guard, so the Results numbers are checked on the device itself. Runs offline.
 */
object Benchmark {
    private class Row(val channel: String, val scam: Boolean, val sender: String, val text: String, val py: String)

    suspend fun run(ctx: Context, onProgress: (Float) -> Unit): List<BenchResult> = withContext(Dispatchers.Default) {
        val rows = ctx.assets.open("bench.tsv").bufferedReader(Charsets.UTF_8).readLines().drop(1)
            .filter { it.isNotBlank() }.map { it.split("\t") }
            .map { Row(it[0], it[1] == "1", it[2], it[3], it[4]) }
        val calls = ctx.assets.open("voice_scripts.tsv").bufferedReader(Charsets.UTF_8).readLines().drop(1)
            .filter { it.isNotBlank() }.map { it.split("\t") }
        val total = (rows.size + calls.size).toFloat()
        var done = 0

        val out = mutableListOf<BenchResult>()
        for (ch in listOf("link", "message", "email")) {
            var phone = Confusion(); var python = Confusion(); var agree = 0
            val ms = mutableListOf<Double>()
            for (r in rows.filter { it.channel == ch }) {
                val t0 = System.nanoTime()
                val level = if (ch == "link") LinkAnalyser.analyse(ctx, r.text)?.level
                else SmsAnalyser.analyse(ctx, r.sender.ifEmpty { null }, r.text).level
                ms += (System.nanoTime() - t0) / 1e6
                phone = phone.add(r.scam, level != null && level != Level.SAFE)
                python = python.add(r.scam, r.py != "SAFE" && r.py != "None")
                if ((level?.name ?: "None") == r.py) agree++
                onProgress(++done / total)
            }
            out += BenchResult(ch, phone, python, agree, pct(ms, 50), pct(ms, 95))
        }

        // calls: replay each script line by line, like a real call, through the call guard session
        val rules = CallGuard.rules(ctx)
        val model = CallGuard.model(ctx)
        var phone = Confusion()
        val ms = mutableListOf<Double>()
        for (c in calls) {
            var clock = 0L
            val session = CallSession(rules, model) { clock }
            var max = Level.SAFE
            val t0 = System.nanoTime()
            for (line in c[3].split(" || ")) {
                clock += 6_000
                session.addFinal(line.removePrefix("[PAY] "))
                val lv = session.update().level
                if (lv > max) max = lv
            }
            ms += (System.nanoTime() - t0) / 1e6
            phone = phone.add(c[1] == "scam", max >= Level.SUSPICIOUS)
            onProgress(++done / total)
        }
        out += BenchResult("call", phone, null, 0, pct(ms, 50), pct(ms, 95))
        out
    }

    private fun pct(xs: List<Double>, p: Int): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return s[minOf(s.size - 1, s.size * p / 100)]
    }
}
