package com.phisherman.kavach.link

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Kotlin port of ml/features.py (SOURCE OF TRUTH). Must reproduce every feature within
 * 1e-4 of the Python reference — asserted by UrlFeaturesParityTest against
 * parity_expected.csv (PS4 Section 6.7). Parsing is written once here and must mirror
 * Python's urlsplit behaviour exactly (host after '@', port stripped, trailing dot).
 */
object UrlFeatures {

    const val VERSION = "link-1"

    val FEATURE_ORDER = listOf(
        "url_len", "host_len", "dot_count", "subdomain_depth", "digit_ratio",
        "hyphen_count_host", "special_chars", "has_at", "has_ip_host", "is_https",
        "host_entropy", "is_shortener", "susp_words", "path_len", "num_params",
        "brand_min_lev", "brand_in_subdomain", "tld_risk",
    )

    private const val MAX_URL_LEN_FOR_FEATURES = 4000

    data class Result(
        val vector: FloatArray,
        val host: String,
        val registrable: String,
        val isIp: Boolean,
        val schemeMissing: Boolean,
        val closestBrand: String?,     // nearest brand token (meaningful when brand_min_lev small)
        val brandInSubToken: String?,  // a brand token present in the host when domain isn't official
    )

    // ---------------------------------------------------------------- helpers

    private fun entropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val n = s.length
        val freq = HashMap<Char, Int>()
        for (ch in s) freq[ch] = (freq[ch] ?: 0) + 1
        var e = 0.0
        for (v in freq.values) {
            val p = v.toDouble() / n
            e -= p * (ln(p) / ln(2.0))
        }
        return e
    }

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            val ca = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ca == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun dehomoglyph(s: String, brands: Brands): String {
        var replaced = s
        for (pair in brands.homoglyphMulti) if (pair.size == 2) replaced = replaced.replace(pair[0], pair[1])
        val sb = StringBuilder(replaced.length)
        for (ch in replaced) sb.append(brands.homoglyphSingle[ch.toString()] ?: ch.toString())
        return sb.toString()
    }

    private fun splitHost(host: String, multi: Set<String>): Pair<String, List<String>> {
        val labels = host.split(".")
        val n = if (labels.size >= 3 && labels.takeLast(2).joinToString(".") in multi) 3 else 2
        if (labels.size < n) return host to emptyList()
        return labels.takeLast(n).joinToString(".") to labels.dropLast(n)
    }

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private fun isIpHost(host: String): Boolean = IPV4.matches(host) || host.contains(':')

    /** Non-overlapping occurrence count, matching Python str.count. */
    private fun countNonOverlapping(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return count
    }

    /** Percent-decode like Python urllib unquote (UTF-8, does NOT turn '+' into space). */
    private fun percentDecode(s: String): String {
        if (!s.contains('%')) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    bytes.add(code.toByte()); i += 3; continue
                }
            }
            for (b in c.toString().toByteArray(Charsets.UTF_8)) bytes.add(b)
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    // ---------------------------------------------------------------- parse

    private data class Parsed(
        val scheme: String, val netloc: String, val host: String,
        val rawPath: String, val query: String, val schemeMissing: Boolean,
    )

    private fun parse(url: String): Parsed {
        val raw = url
        val schemeMissing = !raw.contains("://")
        val u = if (schemeMissing) "http://$raw" else raw
        val scheme = if (schemeMissing) "http" else raw.substringBefore("://").lowercase()
        val afterScheme = u.substringAfter("://")
        val cut = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val netloc = if (cut >= 0) afterScheme.substring(0, cut) else afterScheme
        val rest = if (cut >= 0) afterScheme.substring(cut) else ""
        val rawPath = rest.takeWhile { it != '?' && it != '#' }
        val query = if (rest.contains('?')) rest.substringAfter('?').takeWhile { it != '#' } else ""

        var host = netloc.substringAfterLast('@')
        host = if (host.startsWith('[')) {
            val end = host.indexOf(']')
            if (end > 1) host.substring(1, end) else host.drop(1)
        } else {
            host.substringBefore(':')
        }
        host = host.lowercase().trimEnd('.')
        return Parsed(scheme, netloc, host, rawPath, query, schemeMissing)
    }

    // ---------------------------------------------------------------- compute

    fun compute(url: String, brands: Brands): Result {
        val raw = url.trim()
        val urlLen = raw.length
        val work = if (raw.length > MAX_URL_LEN_FOR_FEATURES) raw.substring(0, MAX_URL_LEN_FOR_FEATURES) else raw

        val p = parse(work)
        val host = p.host
        val isIp = isIpHost(host)
        val (reg, sub) = if (isIp) "" to emptyList<String>() else splitHost(host, brands.multiSet)
        val label = if (reg.isNotEmpty()) reg.substringBefore('.') else ""
        val decodedPath = percentDecode(p.rawPath).lowercase()
        val low = host + decodedPath

        // Brand features.
        val folded = dehomoglyph(label, brands)
        var levMin = 1.0
        var closestBrand: String? = null
        for (t in brands.tokens) {
            if (folded.isNotEmpty()) {
                val d = lev(folded, t).toDouble() / max(folded.length, t.length)
                if (d < levMin) { levMin = d; closestBrand = t }
            }
        }
        val isOfficial = brands.isOfficial(reg)
        val brandInSubToken = if (!isOfficial) brands.tokens.firstOrNull { host.contains(it) } else null
        val inSub = if (brandInSubToken != null) 1 else 0

        val digitCount = work.count { it in '0'..'9' }
        val specials = "@?=&%_".sumOf { c -> countNonOverlapping(work, c.toString()) }
        val suspCount = brands.suspWords.sumOf { w -> countNonOverlapping(low, w) }
        val numParams = p.query.split("&").count { it.isNotEmpty() }
        val tld = reg.substringAfterLast('.', "")
        val tldRisk = if (tld in brands.riskyTldSet || host.contains("xn--")) 1 else 0

        val vector = floatArrayOf(
            urlLen.toFloat(),                                   // 1 url_len
            host.length.toFloat(),                              // 2 host_len
            host.count { it == '.' }.toFloat(),                 // 3 dot_count
            sub.size.toFloat(),                                 // 4 subdomain_depth
            (digitCount.toDouble() / max(work.length, 1)).toFloat(), // 5 digit_ratio
            host.count { it == '-' }.toFloat(),                 // 6 hyphen_count_host
            specials.toFloat(),                                 // 7 special_chars
            (if (p.netloc.contains('@')) 1 else 0).toFloat(),   // 8 has_at
            (if (isIp) 1 else 0).toFloat(),                     // 9 has_ip_host
            (if (p.scheme == "https") 1 else 0).toFloat(),      // 10 is_https
            entropy(host).toFloat(),                            // 11 host_entropy
            (if (host in brands.shortenerSet) 1 else 0).toFloat(),       // 12 is_shortener
            suspCount.toFloat(),                                // 13 susp_words
            p.rawPath.length.toFloat(),                         // 14 path_len
            numParams.toFloat(),                                // 15 num_params
            levMin.toFloat(),                                   // 16 brand_min_lev
            inSub.toFloat(),                                    // 17 brand_in_subdomain
            tldRisk.toFloat(),                                  // 18 tld_risk
        )

        return Result(
            vector = vector,
            host = host,
            registrable = reg,
            isIp = isIp,
            schemeMissing = p.schemeMissing,
            closestBrand = closestBrand,
            brandInSubToken = brandInSubToken,
        )
    }
}
