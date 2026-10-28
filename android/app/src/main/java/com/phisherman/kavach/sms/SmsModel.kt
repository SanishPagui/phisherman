package com.phisherman.kavach.sms

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device reconstruction of the scikit-learn TF-IDF + logistic-regression SMS model
 * exported by ml/train_sms.py. Rebuilds two vectorisers exactly:
 *   word   : analyzer="word",    ngram (1,2), token \b\w\w+\b
 *   char_wb: analyzer="char_wb",  ngram (3,5), per-word space padding
 * Each block is sublinear-tf weighted, L2-normalised independently (as FeatureUnion
 * does), concatenated [word|char], then scored by the linear model. No tokenizer port.
 */
object SmsModel {

    private const val TAG = "SmsModel"

    @Serializable
    data class VecPart(val vocab: Map<String, Int>, val idf: List<Float>, val ngram: List<Int>)

    @Serializable
    data class Json1(
        val version: String,
        val normVersion: String? = null,
        val word: VecPart,
        val char: VecPart,
        val wordDim: Int,
        val coef: List<Float>,
        val intercept: Float,
        val sublinearTf: Boolean = true,
    )

    private var model: Json1? = null
    val isReady: Boolean get() = model != null

    private val WORD_TOKEN = Pattern.compile("\\b\\w\\w+\\b", Pattern.UNICODE_CHARACTER_CLASS)
    private val WS = Pattern.compile("\\s+")

    fun init(ctx: Context) {
        model = try {
            val json = Json { ignoreUnknownKeys = true }
            ctx.assets.open("sms_model.json").bufferedReader().use {
                json.decodeFromString(Json1.serializer(), it.readText())
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sms model load failed — SMS falls back to rules only", t)
            null
        }
    }

    /** Phishing probability in [0,1]. Returns null if the model is unavailable (rules only). */
    fun probability(normalizedText: String): Float? {
        val m = model ?: return null
        val doc = normalizedText.lowercase() // TfidfVectorizer lowercases again
        val wordVec = weightedBlock(wordNgrams(doc), m.word, m.sublinearTf)
        val charVec = weightedBlock(charWbNgrams(doc), m.char, m.sublinearTf)

        var z = m.intercept
        for ((idx, v) in wordVec) z += m.coef[idx] * v
        for ((idx, v) in charVec) z += m.coef[m.wordDim + idx] * v
        return 1f / (1f + exp(-z))
    }

    // --- n-gram extraction -------------------------------------------------

    private fun wordNgrams(doc: String): List<String> {
        val tokens = ArrayList<String>()
        val mt = WORD_TOKEN.matcher(doc)
        while (mt.find()) tokens.add(mt.group())
        val out = ArrayList<String>(tokens)
        // bigrams (ngram_range = 1,2)
        for (i in 0 until tokens.size - 1) out.add(tokens[i] + " " + tokens[i + 1])
        return out
    }

    private fun charWbNgrams(docIn: String): List<String> {
        val doc = WS.matcher(docIn).replaceAll(" ").trim()
        val out = ArrayList<String>()
        if (doc.isEmpty()) return out
        for (word in doc.split(" ")) {
            if (word.isEmpty()) continue
            val w = " $word "
            val wLen = w.length
            for (n in 3..5) {
                var offset = 0
                out.add(w.substring(offset, minOf(offset + n, wLen)))
                while (offset + n < wLen) {
                    offset += 1
                    out.add(w.substring(offset, offset + n))
                }
                if (offset == 0) break // short word counted once (matches sklearn)
            }
        }
        return out
    }

    // --- weighting ---------------------------------------------------------

    /** Count terms in vocab, apply sublinear tf-idf, L2-normalise → map(index -> weight). */
    private fun weightedBlock(ngrams: List<String>, part: VecPart, sublinear: Boolean): Map<Int, Float> {
        if (ngrams.isEmpty()) return emptyMap()
        val counts = HashMap<Int, Int>()
        for (g in ngrams) {
            val idx = part.vocab[g] ?: continue
            counts[idx] = (counts[idx] ?: 0) + 1
        }
        if (counts.isEmpty()) return emptyMap()
        val weights = HashMap<Int, Float>(counts.size * 2)
        var sumSq = 0.0
        for ((idx, c) in counts) {
            val tf = if (sublinear) (1.0 + ln(c.toDouble())) else c.toDouble()
            val w = tf * part.idf[idx]
            weights[idx] = w.toFloat()
            sumSq += w * w
        }
        val norm = sqrt(sumSq)
        if (norm > 0.0) {
            val n = norm.toFloat()
            for (k in weights.keys.toList()) weights[k] = weights[k]!! / n
        }
        return weights
    }
}
