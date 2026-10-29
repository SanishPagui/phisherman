package com.phisherman.sms

import com.phisherman.engine.Channel
import com.phisherman.engine.Level
import com.phisherman.engine.Reason
import com.phisherman.engine.Verdict
import com.phisherman.link.LinkConfig
import com.phisherman.link.UrlFeatures
import kotlin.math.roundToInt

data class SmsThresholds(val suspicious: Double, val dangerous: Double)

/** Result plus the details the UI and tests need. */
data class SmsDecision(val verdict: Verdict, val prob: Double, val linkLevel: Level?, val urls: List<String>)

/**
 * SMS decision logic; must match SmsEngine.decide in ml/sms_engine.py.
 * linkVerdict scores one URL (with scheme) through the link engine; null = not a web link.
 */
class SmsDecider(
    private val model: SmsModel,
    private val cfg: LinkConfig,
    private val thresholds: SmsThresholds,
    private val linkVerdict: (String) -> Verdict?,
) {
    /** trustedSender: a saved contact or verified business name in Google / Samsung Messages. */
    fun decide(sender: String?, body: String, trustedSender: Boolean = false): SmsDecision {
        val t0 = System.nanoTime()
        val (norm, urls) = SmsNormalizer.normalize(body)
        val p = model.prob(norm)
        val hits = mutableListOf<String>()
        val from = (sender ?: "").trim()
        // a message that is basically just a link: the link verdict decides (PS4 12.2)
        var score = if (urls.isNotEmpty() && SmsModel.tokens(norm).size < SHORT_MSG_WORDS) 0.0 else p
        if (urls.isNotEmpty() && URGENT_RE.containsMatchIn(norm)) { hits += "R_URGENT_LINK"; score += BONUS }
        if (MOBILE_RE.matches(from.replace(" ", "")) && CLAIM_RE.containsMatchIn(norm)) { hits += "R_SENDER_MISMATCH"; score += BONUS }
        score = minOf(score, 1.0)
        var level = when {
            score >= thresholds.dangerous -> 2
            score >= thresholds.suspicious -> 1
            else -> 0
        }
        // a short text with no link and no rule hit is too little evidence for the word model alone
        if (urls.isEmpty() && hits.isEmpty() && SmsModel.tokens(norm).size < SHORT_TEXT_WORDS && level < 2) level = 0

        var linkLevel: Int? = null
        var worstLink: Verdict? = null
        for (u in urls) {
            val v = linkVerdict(withScheme(u)) ?: continue
            val li = v.level.ordinal
            if (linkLevel == null || li > linkLevel) { linkLevel = li; worstLink = v }
        }

        if (asks(ASK_RE, norm) || asks(HI_ASK_RE, norm)) { hits += "R_OTP_ASK"; level = 2 }
        if (APK_FILE_RE.containsMatchIn(norm)) { hits += "R_APK_FILE"; level = 2 }
        val apkDisguised = APK_DISGUISED_RE.containsMatchIn(norm)
        if (REMOTE_APPS_RE.containsMatchIn(norm) || ".apk" in urls.joinToString(" ") ||
            (INSTALL_APP_RE.containsMatchIn(norm) && linkLevel != null && linkLevel > 0)) {
            hits += "R_APP_INSTALL"; level = 2
        }
        var brandHit: Pair<String, String>? = null
        for (t in cfg.brandTokens) {
            if (brandRegex(t).containsMatchIn(norm) && urls.isNotEmpty()) {
                val official = cfg.brandDomains[t].orEmpty().toSet()
                val bad = urls.map { UrlFeatures.registrableDomain(withScheme(it), cfg) }.firstOrNull { it.isNotEmpty() && it !in official }
                if (bad != null) {
                    hits += "R_BRAND_LINK"; level = maxOf(level, 1); brandHit = t to bad
                    break
                }
            }
        }
        if (linkLevel != null && linkLevel > level) { level = linkLevel; hits += "LINK" }
        val linksSafe = urls.isEmpty() || linkLevel == 0
        if ((HEADER_RE.matches(from) || trustedSender) && linksSafe && hits.none { it == "R_OTP_ASK" || it == "R_APP_INSTALL" || it == "R_BRAND_LINK" || it == "R_APK_FILE" }) {
            if (level > 0) hits += "R_ALLOW_SENDER"
            level = 0
        }

        val lvl = Level.entries[level]
        var display = (score * 100).roundToInt().coerceIn(0, 100)
        display = when (lvl) {
            Level.DANGEROUS -> maxOf(display, 90, worstLink?.score ?: 0)
            Level.SUSPICIOUS -> maxOf(display, 50)
            Level.SAFE -> minOf(display, 30)
        }
        val reasons = if (lvl == Level.SAFE) emptyList() else SmsReasons.build(hits, brandHit, worstLink, cfg, apkDisguised)
        val verdict = Verdict(Channel.SMS, display, lvl, reasons.take(3), p.toFloat(), hits.ifEmpty { listOf("model") },
            (System.nanoTime() - t0) / 1_000_000, from.ifEmpty { "pasted message" })
        return SmsDecision(verdict, p, linkLevel?.let { Level.entries[it] }, urls)
    }

    private val brandRegexes = HashMap<String, Regex>()
    private fun brandRegex(t: String) = brandRegexes.getOrPut(t) { Regex("(?<![a-z0-9])" + Regex.escape(t) + "(?![a-z0-9])") }

    private fun asks(rx: Regex, s: String): Boolean = rx.findAll(s).any { m ->
        val window = s.substring(maxOf(0, m.range.first - 20), m.range.last + 1)
        !NEGATION_RE.containsMatchIn(window)
    }

    companion object {
        const val BONUS = 0.15
        const val SHORT_MSG_WORDS = 4
        const val SHORT_TEXT_WORDS = 8
        private const val A = "(?<![a-z])"
        private const val Z = "(?![a-z])"
        private const val ASK = "(?:share|sharing|send|tell|give|forward|provide|reply with|enter)"
        private const val CRED = "(?:otp|upi pin|atm pin|pin|cvv|password|passcode|card number|card details|bank details|account details|expiry date)"
        private val ASK_RE = Regex("$A$ASK$Z[^.!?]{0,40}?$A$CRED$Z")
        private val HI_ASK_RE = Regex("$A$CRED$Z[^.!?]{0,30}?$A(?:batao|bhejo|share karo|share karein|share kare|de do|dijiye|bata do)$Z")
        private val NEGATION_RE = Regex("$A(?:not|never|don't|dont|mat|na|nahi|no one)$Z")
        /** An Android app file in a message or attachment, e.g. "KYC_Update.apk" or disguised "Form.pdf.apk" */
        private val APK_FILE_RE = Regex("[a-z0-9_()\\-.]+\\.(?:apk|xapk|apks|apkm)(?![a-z0-9])")
        private val APK_DISGUISED_RE = Regex("\\.(?:pdf|jpe?g|png|docx?|xlsx?|mp4|txt)\\.(?:apk|xapk|apks|apkm)(?![a-z0-9])")
        private val REMOTE_APPS_RE =Regex("$A(?:anydesk|teamviewer|quicksupport|quick support|rustdesk|airdroid)$Z")
        private val INSTALL_APP_RE = Regex("$A(?:install|download)$Z[^.!?]{0,50}?$A(?:app|apk|application)$Z")
        private val URGENT_RE = Regex(
            "$A(?:urgent|jaldi|today|tonight|expire|expires|expiring|expired|last warning|final notice|" +
                "block|suspend|deactivated|disconnected|cancelled|within [0-9]+ hours?|aaj|abhi)$Z|तुरंत|आज")
        private val CLAIM_RE = Regex(
            "$A(?:bank|sbi|hdfc|icici|axis|kotak|paytm|phonepe|rbi|income tax|uidai|aadhaar|police|cbi|customs|" +
                "trai|electricity|bijli|india post|courier|irctc|customer care|kyc)$Z|बैंक|खाता")
        private val MOBILE_RE = Regex("^(?:\\+?91|0)?[6-9][0-9]{9}$")
        private val HEADER_RE = Regex("^[A-Z]{2}-[A-Z0-9]{3,9}(?:-[A-Z])?$")

        fun withScheme(u: String) = if ("://" in u) u else "http://$u"
    }
}

object SmsReasons {
    fun build(hits: List<String>, brandHit: Pair<String, String>?, link: Verdict?, cfg: LinkConfig, apkDisguised: Boolean = false): List<Reason> {
        val out = mutableListOf<Reason>()
        if ("R_APK_FILE" in hits) out += if (apkDisguised) Reason("R_APK_FILE",
            "An app file pretending to be a document. Do NOT open or install it.",
            "दस्तावेज़ जैसा दिखने वाली ऐप फ़ाइल। इसे न खोलें, न इंस्टॉल करें।")
        else Reason("R_APK_FILE",
            "Contains an Android app file (.apk). Banks and companies never send apps like this.",
            "इसमें Android ऐप फ़ाइल (.apk) है। बैंक कभी ऐसे ऐप नहीं भेजते।")
        if ("R_OTP_ASK" in hits) out += Reason("R_OTP_ASK",
            "Asks you to share an OTP, PIN or card details. Banks never ask for these.",
            "OTP, PIN या कार्ड की जानकारी माँगता है। बैंक कभी ये नहीं माँगते।")
        if ("R_APP_INSTALL" in hits) out += Reason("R_APP_INSTALL",
            "Asks you to install an app or a screen-sharing tool",
            "कोई ऐप या स्क्रीन-शेयरिंग टूल इंस्टॉल करने को कहता है")
        if (brandHit != null) {
            val name = cfg.brandNames[brandHit.first] ?: brandHit.first.uppercase()
            out += Reason("R_BRAND_LINK", "Mentions $name, but the link goes to ${brandHit.second}",
                "$name का नाम लेता है, पर लिंक ${brandHit.second} पर जाता है")
        }
        if (link != null && link.level != Level.SAFE) out += Reason("LINK",
            "Contains a risky link: ${link.subject}", "इसमें जोखिम भरा लिंक है: ${link.subject}")
        if ("R_SENDER_MISMATCH" in hits) out += Reason("R_SENDER_MISMATCH",
            "Claims to be a bank or official body, but comes from a personal mobile number",
            "बैंक या सरकारी संस्था होने का दावा, पर एक निजी मोबाइल नंबर से आया है")
        if ("R_URGENT_LINK" in hits) out += Reason("R_URGENT_LINK",
            "Pressures you to act fast and click a link",
            "जल्दी करने का दबाव डालता है और लिंक पर क्लिक करवाता है")
        out += Reason("model", "The wording matches scam messages we have seen",
            "इसकी भाषा धोखाधड़ी वाले संदेशों जैसी है")
        return out
    }
}
