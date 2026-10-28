package com.phisherman.kavach.sms

import android.content.Context
import com.phisherman.kavach.engine.Channel
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.engine.Reason
import com.phisherman.kavach.engine.Reasons
import com.phisherman.kavach.engine.RiskEngine
import com.phisherman.kavach.engine.Verdict
import com.phisherman.kavach.link.Brands
import com.phisherman.kavach.link.UrlAnalyser
import com.phisherman.kavach.link.UrlFeatures
import kotlinx.serialization.json.Json

/**
 * SMS verdict = TF-IDF/LogReg model probability + explainable rules (PS4 Section 8.4),
 * plus the risk of any link found in the message. Rules catch what a small statistical
 * model learns slowly: OTP-sharing requests, app-install lures, sender/brand mismatches.
 */
object SmsAnalyser {

    private var brands: Brands = Brands.EMPTY

    fun init(ctx: Context) {
        brands = runCatching {
            val json = Json { ignoreUnknownKeys = true }
            ctx.assets.open("brands.json").bufferedReader().use {
                json.decodeFromString(Brands.serializer(), it.readText())
            }
        }.getOrDefault(Brands.EMPTY)
    }

    private val CREDENTIAL = Regex("\\b(otp|o\\.?t\\.?p|pin|cvv|password|passcode|atm pin|net ?banking password)\\b")
    private val SHARE_VERB = Regex("\\b(share|send|tell|give|forward|provide|enter|submit|batao|bataiye|bhejo|bhej|do)\\b")
    private val NEGATION = Regex("(do ?n'?t share|do not share|never share|kabhi ?mat|kisi ?ko ?mat ?batana|not share)")
    private val URGENCY = Regex("\\b(block(ed)?|suspend(ed)?|expire[sd]?|expiry|last warning|urgent|immediately|turant|jaldi|within \\d+ ?(hour|hr|minute)|account will be|band ho)\\b")
    private val AUTHORITY = Regex("\\b(bank|rbi|kyc|account|aadhaar|aadhar|income tax|govt|government|police|customs)\\b")
    private val APP_INSTALL = Regex("\\b(install|download|update)\\b.{0,30}\\b(app|apk|anydesk|teamviewer|quicksupport)\\b|\\b(anydesk|teamviewer|quicksupport)\\b")
    private val PERSONAL_NUMBER = Regex("^(\\+?91)?[6-9]\\d{9}$")
    private val SENDER_HEADER = Regex("^[a-z]{2}-[a-z0-9]{3,}$", RegexOption.IGNORE_CASE)

    fun analyse(sender: String, body: String): Verdict {
        val t0 = System.nanoTime()
        val text = body.lowercase()
        val reasons = mutableListOf<Reason>()
        var forced: Level? = null

        val hasCredential = CREDENTIAL.containsMatchIn(text)
        val hasShare = SHARE_VERB.containsMatchIn(text)
        val negated = NEGATION.containsMatchIn(text)

        // R_OTP_ASK — asks the user to share a secret, and not a "do not share" notice.
        if (hasCredential && hasShare && !negated) {
            reasons += Reasons.OTP_ASK; forced = Level.DANGEROUS
        }
        // R_APP_INSTALL
        if (APP_INSTALL.containsMatchIn(text)) {
            reasons += Reasons.APP_INSTALL; forced = Level.DANGEROUS
        }

        // Embedded link → score with the same URL model; drives the message up.
        val urls = Normalizer.extractUrls(body)
        var linkVerdict: Verdict? = null
        var brandLinkMismatch = false
        if (urls.isNotEmpty()) {
            linkVerdict = UrlAnalyser.score(urls.first())
            // R_BRAND_LINK — text names a brand but the link host isn't that brand's domain.
            val namedBrand = brands.tokens.firstOrNull { text.contains(it) }
            if (namedBrand != null) {
                val reg = runCatching { UrlFeatures.compute(urls.first(), brands).registrable }.getOrDefault("")
                val official = brands.officialDomainFor(namedBrand)
                if (official != null && reg != official && !brands.isOfficial(reg)) {
                    reasons += Reasons.BRAND_LINK_MISMATCH; brandLinkMismatch = true
                    if (forced == null) forced = Level.SUSPICIOUS
                }
            }
            // R_URGENT_LINK — urgency words plus a link.
            if (URGENCY.containsMatchIn(text)) {
                reasons += Reasons.URGENT_LINK
                if (forced == null) forced = Level.SUSPICIOUS
            }
        }

        // R_SENDER_MISMATCH — personal 10-digit number claiming to be a bank/govt.
        if (PERSONAL_NUMBER.matches(sender.replace(" ", "")) && AUTHORITY.containsMatchIn(text)) {
            reasons += Reasons.SENDER_MISMATCH
            if (forced == null || forced == Level.SAFE) forced = Level.SUSPICIOUS
        }

        // R_ALLOW_SENDER — verified alphanumeric header, no link, no OTP ask → cap SAFE.
        val looksVerifiedHeader = SENDER_HEADER.matches(sender.trim())
        val anyRiskRuleFired = reasons.isNotEmpty() || (linkVerdict != null && !linkVerdict.isSafe)
        if (looksVerifiedHeader && urls.isEmpty() && !anyRiskRuleFired) {
            return Verdict.safe(Channel.SMS, sender, latencyMs = (System.nanoTime() - t0) / 1_000_000)
        }

        val prob = SmsModel.probability(Normalizer.normalize(body)) ?: 0f
        val latency = (System.nanoTime() - t0) / 1_000_000
        return RiskEngine.fromSms(
            prob = prob,
            ruleReasons = reasons,
            forcedLevel = forced,
            linkVerdict = linkVerdict,
            latencyMs = latency,
            sender = sender,
        )
    }
}
