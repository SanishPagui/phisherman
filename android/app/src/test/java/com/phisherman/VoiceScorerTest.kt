package com.phisherman

import com.phisherman.engine.Level
import com.phisherman.link.Blocklist
import com.phisherman.link.LinkConfig
import com.phisherman.link.LinkDecider
import com.phisherman.link.LinkThresholds
import com.phisherman.voice.CallModel
import com.phisherman.voice.CallSession
import com.phisherman.voice.Judgement
import com.phisherman.voice.VoiceRules
import com.phisherman.voice.VoiceScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** PS4 13.4 P4: scam scripts reach red before the payment demand; normal calls never reach red. */
class VoiceScorerTest {
    private val rules = VoiceRules(File("src/main/assets/voice_rules.json").readText())
    private val model = CallModel.load(File("src/main/assets/call_model.json").readText(),
        File("src/main/assets/thresholds.json").readText())

    /**
     * The real link engine (allowlist, blocklist, brand rules). The ONNX model cannot run on the JVM,
     * so its probability is 0 here: a link is only flagged by the lists and brand rules.
     */
    private val linkDecider = run {
        val assets = File("src/main/assets")
        val cfg = LinkConfig(File(assets, "feature_order.json").readText(), File(assets, "brands.json").readText())
        val allow = Json.parseToJsonElement(File(assets, "allowlist.json").readText()).jsonObject["domains"]!!
            .jsonArray.map { it.jsonPrimitive.content }.toSet() + cfg.official
        val meta = Json.parseToJsonElement(File(assets, "blocklist.json").readText()).jsonObject
        val bl = Blocklist(File(assets, "blocklist.bloom").readBytes(), meta["m_bits"]!!.jsonPrimitive.long,
            meta["k"]!!.jsonPrimitive.long.toInt())
        LinkDecider(cfg, allow, bl, LinkThresholds(0.5f, 0.9f)) { 0f }
    }

    private data class Script(val id: String, val kind: String, val lines: List<String>)

    private val scripts = File("src/main/assets/voice_scripts.tsv").readLines(Charsets.UTF_8).drop(1)
        .filter { it.isNotBlank() }.map { l -> l.split("\t").let { Script(it[0], it[1], it[3].split(" || ")) } }

    /** Feed lines 8 s apart, like a real call; returns the level after each line. */
    private fun play(s: Script): List<Level> {
        var t = 0L
        val session = CallSession(rules, model, linkDecider::decide) { t }
        return s.lines.map { line ->
            t += 8_000
            session.addFinal(line.removePrefix("[PAY] "))
            session.update().level
        }
    }

    @Test
    fun scamScriptsTurnRedBeforeThePaymentDemand() {
        val scams = scripts.filter { it.kind == "scam" }
        assertTrue(scams.size >= 6)
        val bad = scams.mapNotNull { s ->
            val levels = play(s)
            val pay = s.lines.indexOfFirst { it.startsWith("[PAY]") }
            val firstRed = levels.indexOf(Level.DANGEROUS)
            if (firstRed < 0 || (pay >= 0 && firstRed >= pay)) "${s.id}: levels=$levels pay at $pay" else null
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun normalCallsNeverTurnRedAndCareCallsStayBelowRed() {
        val bad = scripts.filter { it.kind != "scam" }.mapNotNull { s ->
            val levels = play(s)
            val limit = if (s.kind == "normal") Level.SAFE else Level.SUSPICIOUS
            if (levels.any { it > limit }) "${s.id} (${s.kind}): $levels" else null
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** Genuine bank calls (G1-G3) mention OTPs and cards but never ASK for them: no alert at all. */
    @Test
    fun genuineBankCallsStaySafe() {
        val bad = scripts.filter { it.id.startsWith("G") }.mapNotNull { s ->
            val levels = play(s)
            if (levels.any { it != Level.SAFE }) "${s.id}: $levels" else null
        }
        assertEquals(3, scripts.count { it.id.startsWith("G") })
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
        val sc = VoiceScorer(rules)
        assertTrue("credential" !in sc.score("you will receive an OTP, enter it only in the app").categories)
        assertTrue("credential" in sc.score("you will receive an OTP, tell me the OTP").categories)
    }

    @Test
    fun negatedCredentialIsNotARequest() {
        val sc = VoiceScorer(rules)
        assertEquals(Level.SAFE, sc.score("please never share your OTP with anyone").level)
        assertEquals(Level.SAFE, sc.score("OTP kisi ko mat batana").level)
        assertTrue(sc.score("o.t.p batao jaldi").categories.contains("credential"))
    }

    @Test
    fun redStaysRedAndLlmOnlyRaises() {
        var t = 0L
        val s = CallSession(rules) { t }
        s.addFinal("I am the bank manager, your account will be blocked, tell me the OTP")
        assertEquals(Level.DANGEROUS, s.update().level)
        t += 120_000  // window has moved past it
        s.addFinal("okay thank you")
        assertEquals(Level.DANGEROUS, s.update().level)

        val s2 = CallSession(rules) { t }
        s2.addFinal("hello how are you")
        s2.applyJudgement(Judgement(Level.SUSPICIOUS, "family_impersonation"))
        assertEquals(Level.SUSPICIOUS, s2.update().level)
        s2.applyJudgement(Judgement(Level.SAFE, "none"))  // cannot lower
        assertEquals(Level.SUSPICIOUS, s2.update().level)
    }

    /** A picnic call must never go red just because the AI said SCAM. */
    @Test
    fun aiAloneNeverMakesACallRed() {
        var t = 0L
        val s = CallSession(rules) { t }
        s.addFinal("hey are you coming to the picnic on sunday, bring the sandwiches")
        s.applyJudgement(Judgement(Level.DANGEROUS, "other_scam"))
        assertEquals(Level.SUSPICIOUS, s.update().level)

        // with real warning signs from the rules, the AI can lift amber to red
        val s2 = CallSession(rules) { t }
        s2.addFinal("I am calling from the bank head office, please tell me the OTP")
        assertEquals(Level.DANGEROUS, s2.update().level)  // rules alone: authority + credential + combo
        val s3 = CallSession(rules) { t }
        s3.addFinal("please tell me the OTP you got")
        assertEquals(Level.SUSPICIOUS, s3.update().level)
        s3.applyJudgement(Judgement(Level.DANGEROUS, "credential_request"))
        assertEquals(Level.DANGEROUS, s3.update().level)
    }

    /** call_model.json scored in Kotlin matches ml/train_calls.py (same scorer as the SMS model). */
    @Test
    fun callModelMatchesPython() {
        val sms = com.phisherman.sms.SmsModel(File("src/main/assets/call_model.json").readText())
        val rows = javaClass.classLoader!!.getResource("call_parity_expected.tsv")!!.readText(Charsets.UTF_8)
            .lines().drop(1).filter { it.isNotBlank() }.map { it.split("	") }
        assertTrue(rows.size >= 10)
        for ((text, expected) in rows) {
            val p = sms.prob(com.phisherman.sms.SmsNormalizer.normalize(text).text)
            assertEquals(text, expected.toDouble(), p, 1e-6)
        }
    }

    /** S7: the caller dictates a fake SBI site; the guard names the site and turns red before payment. */
    @Test
    fun dictatedFakeLinkIsCaught() {
        var t = 0L
        val session = CallSession(rules, model, linkDecider::decide) { t }
        for (l in listOf("Hello sir, calling from your bank.", "Please type s b i dash rewards dot com slash claim.")) {
            t += 8_000; session.addFinal(l); session.update()
        }
        assertTrue(session.links.toString(), "sbi-rewards.com/claim" in session.riskyLinks)
        assertTrue(CallSession.DICTATED_LINK in session.seen)
    }

    /** Live WhatsApp test, 2026-10-29: Live Caption heard "give me" as "giving us". An urgent OTP demand is red. */
    @Test
    fun urgentOtpDemandFromLiveCaptionIsRed() {
        var t = 0L
        val session = CallSession(rules, null) { t }
        var level = Level.SAFE
        for (l in listOf("What's going on? Any problem?", "Hurt sir. Giving us some OTP, please right now.")) {
            t += 6_000; session.addFinal(l); level = session.update().level
        }
        assertEquals(Level.DANGEROUS, level)
    }

    /** Live phone call, 2026-10-29: Live Caption wrote "dash" as "bash" and glued "dashreward.com". */
    @Test
    fun garbledDictatedLinkFromLiveCaption() {
        var t = 0L
        val session = CallSession(rules, null, linkDecider::decide) { t }
        var level = Level.SAFE
        for (l in listOf("Is called http slash SBI bash reward.com. That is the link.",
                         "Log in place here. Again, https, less s e, i dashreward.com", "Anything more, you")) {
            t += 6_000; session.addFinal(l); level = session.update().level
        }
        assertTrue(session.links.toString(), "sbi-reward.com" in session.riskyLinks)
        assertEquals(Level.DANGEROUS, level)
    }
}
