package com.phisherman.kavach.link

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.phisherman.kavach.engine.Channel
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.engine.Reasons
import com.phisherman.kavach.engine.RiskEngine
import com.phisherman.kavach.engine.Verdict
import kotlinx.serialization.json.Json
import java.util.Collections

/**
 * Scores a URL fully offline: allowlist → cache → 18 features → ONNX model → RiskEngine
 * rules. Fails OPEN (returns SUSPICIOUS with a visible reason) if the model asset is
 * missing or errors — it never silently passes (edge case 12.1 "Dead or empty model").
 */
@kotlinx.serialization.Serializable
private data class FeatureOrderFile(val version: String, val order: List<String>)

object UrlAnalyser {

    private const val TAG = "UrlAnalyser"
    private const val SHORTENER_IDX = 11

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "input"
    private var probName: String? = null
    private var brands: Brands = Brands.EMPTY
    @Volatile private var ready = false

    // Small LRU cache of recent verdicts keyed by normalised URL (PS4 Section 7.5).
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Verdict>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Verdict>) = size > 300
        }
    )

    fun init(ctx: Context) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            brands = ctx.assets.open("brands.json").bufferedReader().use {
                json.decodeFromString(Brands.serializer(), it.readText())
            }
            Allowlist.seedFromBrands(brands)

            // Refuse a stale model: feature_order.json must match this build's feature code.
            val fo = ctx.assets.open("feature_order.json").bufferedReader().use {
                json.decodeFromString(FeatureOrderFile.serializer(), it.readText())
            }
            check(fo.version == UrlFeatures.VERSION && fo.order == UrlFeatures.FEATURE_ORDER) {
                "feature_order.json (${fo.version}) does not match UrlFeatures (${UrlFeatures.VERSION})"
            }

            val e = OrtEnvironment.getEnvironment()
            val bytes = ctx.assets.open("link_model.onnx").use { it.readBytes() }
            val s = e.createSession(bytes, OrtSession.SessionOptions())
            inputName = s.inputNames.firstOrNull() ?: "input"
            probName = when {
                s.outputNames.contains("probabilities") -> "probabilities"
                s.outputNames.size >= 2 -> s.outputNames.elementAt(1)
                else -> s.outputNames.firstOrNull()
            }
            env = e
            session = s
            ready = true
            Log.i(TAG, "model ready; inputs=${s.inputNames} outputs=${s.outputNames}")
        } catch (t: Throwable) {
            ready = false
            Log.e(TAG, "model init failed — link checks will fail open as SUSPICIOUS", t)
        }
    }

    val isReady: Boolean get() = ready

    fun score(url: String): Verdict {
        val key = url.trim()
        cache[key]?.let { return it }
        val t0 = System.nanoTime()

        val result = runCatching { UrlFeatures.compute(key, brands) }.getOrNull()
            ?: return failOpen(url, t0)

        // Allowlisted → instant SAFE, skip the model.
        Allowlist.match(result.registrable)?.let { dom ->
            val v = Verdict.safe(
                Channel.LINK, result.host, Reasons.KNOWN_SAFE, latencyMs(t0)
            )
            cache[key] = v
            return v
        }

        val sess = session
        val environment = env
        val prob: Float = if (ready && sess != null && environment != null) {
            runCatching {
                OnnxTensor.createTensor(environment, arrayOf(result.vector)).use { input ->
                    sess.run(Collections.singletonMap(inputName, input)).use { out ->
                        extractProb(out)
                    }
                }
            }.getOrElse {
                Log.e(TAG, "inference failed", it)
                return failOpen(url, t0).also { fv -> cache[key] = fv }
            }
        } else {
            return failOpen(url, t0)
        }

        val signals = RiskEngine.LinkSignals(
            host = result.host,
            registrable = result.registrable,
            matchedBrand = result.closestBrand ?: result.brandInSubToken,
            realDomainForBrand = (result.closestBrand ?: result.brandInSubToken)
                ?.let { brands.officialDomainFor(it) },
        )
        val verdict = RiskEngine.fromLink(prob, result.vector, signals, latencyMs(t0))
        cache[key] = verdict
        return verdict
    }

    private fun extractProb(out: OrtSession.Result): Float {
        val name = probName ?: out.iterator().next().key
        val value = out.get(name).orElse(null)?.value
        // zipmap off → [N,2] float tensor.
        @Suppress("UNCHECKED_CAST")
        return when (value) {
            is Array<*> -> {
                val row = value[0]
                if (row is FloatArray) row.getOrElse(1) { row.getOrElse(0) { 0f } } else 0f
            }
            else -> 0f
        }
    }

    private fun failOpen(url: String, t0: Long): Verdict = Verdict(
        channel = Channel.LINK,
        score = 55,
        level = Level.SUSPICIOUS,
        reasons = listOf(Reasons.MODEL_UNAVAILABLE),
        modelProb = null,
        ruleHits = listOf("L_MODEL_FALLBACK"),
        latencyMs = latencyMs(t0),
        subject = runCatching { UrlFeatures.compute(url, brands).host }.getOrDefault(url),
    )

    private fun latencyMs(t0: Long): Long = (System.nanoTime() - t0) / 1_000_000
}
