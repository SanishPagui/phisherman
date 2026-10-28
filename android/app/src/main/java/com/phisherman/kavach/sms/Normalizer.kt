package com.phisherman.kavach.sms

import java.util.regex.Pattern

/**
 * Kotlin port of ml/normalize.py (SOURCE OF TRUTH). Must stay byte-for-byte identical in
 * behaviour to the Python version or the TF-IDF vocabulary lookups diverge (PS4 8.2).
 * Uses UNICODE_CHARACTER_CLASS so \w keeps Devanagari letters, matching Python's re.
 */
object Normalizer {

    const val VERSION = "sms-norm-1"

    private val URL = Pattern.compile(
        "(https?://\\S+|www\\.\\S+|\\b\\S+\\.(?:com|in|net|org|xyz|top|info|link|co)\\b\\S*)",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS
    )
    private val OTP = Pattern.compile("\\b\\d{4,8}\\b")
    private val NUM = Pattern.compile("\\b\\d{2,}\\b")
    private val PUNCT = Pattern.compile("[^\\w\\s]", Pattern.UNICODE_CHARACTER_CLASS)
    private val WS = Pattern.compile("\\s+")

    private val VARIANTS = mapOf(
        "k.y.c" to "kyc", "kyc" to "kyc",
        "aadhar" to "aadhaar", "aadhaar" to "aadhaar",
        "blocked" to "block", "band" to "block", "blokced" to "block",
        "jaldi" to "turant", "turant" to "turant",
        "verifcation" to "verification", "verify" to "verify",
        "a/c" to "account", "acc" to "account", "ac" to "account",
        "rs." to "rupees", "inr" to "rupees",
    )

    fun normalize(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var t = text.lowercase().trim()
        t = URL.matcher(t).replaceAll(" URLTOKEN ")
        t = OTP.matcher(t).replaceAll(" OTPCODE ")
        t = NUM.matcher(t).replaceAll(" NUM ")
        t = t.replace("1", "i").replace("0", "o").replace("3", "e").replace("@", "a")
        t = PUNCT.matcher(t).replaceAll(" ")
        val words = t.split(" ").filter { it.isNotEmpty() }.map { VARIANTS[it] ?: it }
        t = words.joinToString(" ")
        t = WS.matcher(t).replaceAll(" ").trim()
        return t
    }

    private val URL_FIND = URL

    fun extractUrls(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        val out = ArrayList<String>()
        val m = URL_FIND.matcher(text)
        while (m.find()) out.add(m.group())
        return out
    }
}
