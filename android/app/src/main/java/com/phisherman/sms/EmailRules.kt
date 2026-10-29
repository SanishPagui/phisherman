package com.phisherman.sms

import android.content.Context
import com.phisherman.engine.Level
import com.phisherman.engine.Reason
import com.phisherman.engine.Verdict
import com.phisherman.link.LinkAnalyser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Email-only checks, applied on top of the message engine for mail notifications (Gmail, Outlook,
 * ...) or any sender that carries an email address. They can only raise the verdict, never lower
 * it, so SmsDecider stays in parity with ml/sms_engine.py. Rule lists: assets/email_rules.json.
 */
object EmailRules {
    @Serializable
    private data class Lists(
        val freeMailDomains: List<String> = emptyList(),
        val attachmentLures: List<String> = emptyList(),
        val credentialLures: List<String> = emptyList(),
    )

    val MAIL_APPS = setOf("Gmail", "Outlook", "Yahoo Mail", "Samsung Email", "Proton Mail", "Zoho Mail", "Rediffmail")
    private val ADDRESS = Regex("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})")

    @Volatile private var lists: Lists? = null
    private var attachment: Regex? = null
    private var credential: Regex? = null

    private fun load(ctx: Context): Lists = lists ?: synchronized(this) {
        lists ?: runCatching {
            val text = ctx.assets.open("email_rules.json").bufferedReader().use { it.readText() }.trimStart('﻿')
            Json { ignoreUnknownKeys = true }.decodeFromString(Lists.serializer(), text)
        }.getOrDefault(Lists()).also {
            attachment = anyOf(it.attachmentLures)
            credential = anyOf(it.credentialLures)
            lists = it
        }
    }

    private fun anyOf(p: List<String>) =
        if (p.isEmpty()) null else Regex(p.joinToString("|") { "(?:$it)" }, RegexOption.IGNORE_CASE)

    fun isEmail(sender: String, sourceApp: String?): Boolean = sourceApp in MAIL_APPS || ADDRESS.containsMatchIn(sender)

    fun apply(ctx: Context, v: Verdict, sender: String, body: String): Verdict {
        val l = load(ctx)
        val cfg = LinkAnalyser.config()
        val reasons = mutableListOf<Reason>()
        var forced = Level.SAFE
        val name = sender.substringBefore('<').lowercase()
        val text = body.lowercase()
        val domain = ADDRESS.find(sender)?.groupValues?.get(1)?.lowercase()

        if (cfg != null && domain != null) {
            val reg = LinkAnalyser.registrableDomain(domain)
            fun word(t: String, s: String) = Regex("(?<![a-z0-9])" + Regex.escape(t) + "(?![a-z0-9])").containsMatchIn(s)
            val nameBrand = cfg.brandTokens.firstOrNull { word(it, name) }
            val anyBrand = nameBrand ?: cfg.brandTokens.firstOrNull { word(it, text) }
            if (reg in l.freeMailDomains && anyBrand != null) {
                val brand = cfg.brandNames[anyBrand] ?: anyBrand.uppercase()
                reasons += Reason("E_FREEMAIL_BRAND",
                    "Claims to be $brand but was sent from a free personal email account ($reg)",
                    "$brand होने का दावा, पर मुफ़्त निजी ईमेल खाते ($reg) से भेजा गया")
                forced = Level.DANGEROUS
            } else if (nameBrand != null && reg !in cfg.official && reg !in cfg.brandDomains[nameBrand].orEmpty()) {
                val brand = cfg.brandNames[nameBrand] ?: nameBrand.uppercase()
                reasons += Reason("E_SPOOF",
                    "Sender name says $brand, but the email address is from $reg",
                    "भेजने वाले का नाम $brand है, पर ईमेल पता $reg का है")
                forced = Level.DANGEROUS
            }
        }
        if (attachment?.containsMatchIn(body) == true) {
            reasons += Reason("E_ATTACHMENT", "Pushes you to open an attachment, a common way to spread malware",
                "अटैचमेंट खोलने को कहता है, मैलवेयर फैलाने का आम तरीका")
            if (forced < Level.SUSPICIOUS) forced = Level.SUSPICIOUS
        }
        if (credential?.containsMatchIn(body) == true) {
            reasons += Reason("E_CREDENTIAL", "Asks you to sign in or verify your account. Real services rarely do this by email.",
                "साइन इन या खाता सत्यापित करने को कहता है। असली सेवाएँ ईमेल से ऐसा शायद ही करती हैं।")
            if (forced < Level.SUSPICIOUS) forced = Level.SUSPICIOUS
        }
        if (reasons.isEmpty()) return v

        // Two independent email signals, or one plus a risky message verdict, is a strong phishing pattern.
        var level = maxOf(v.level, forced)
        if (reasons.size >= 2 || (v.level != Level.SAFE && forced != Level.SAFE)) level = Level.DANGEROUS
        val score = when (level) { Level.DANGEROUS -> maxOf(v.score, 90); Level.SUSPICIOUS -> maxOf(v.score, 50); else -> v.score }
        val merged = (reasons + v.reasons.filter { r -> v.level != Level.SAFE || r.id != "model" }).distinctBy { it.id }
        return v.copy(level = level, score = score, reasons = merged.take(3), ruleHits = v.ruleHits + reasons.map { it.id })
    }
}
