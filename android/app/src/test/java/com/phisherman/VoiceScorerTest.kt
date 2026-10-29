package com.phisherman

import com.phisherman.engine.Level
import com.phisherman.voice.CallSession
import com.phisherman.voice.Judgement
import com.phisherman.voice.VoiceRules
import com.phisherman.voice.VoiceScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** PS4 13.4 P4: scam scripts reach red before the payment demand; normal calls never reach red. */
class VoiceScorerTest {
    private val rules = VoiceRules(File("src/main/assets/voice_rules.json").readText())

    private data class Script(val id: String, val kind: String, val lines: List<String>)

    private val scripts = File("src/main/assets/voice_scripts.tsv").readLines(Charsets.UTF_8).drop(1)
        .filter { it.isNotBlank() }.map { l -> l.split("\t").let { Script(it[0], it[1], it[3].split(" || ")) } }

    /** Feed lines 8 s apart, like a real call; returns the level after each line. */
    private fun play(s: Script): List<Level> {
        var t = 0L
        val session = CallSession(rules) { t }
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

    /** Without speakerphone the mic hears only the user: their own words must be enough to turn red. */
    @Test
    fun userGivingAwayACodeTurnsRedOnItsOwn() {
        val sc = VoiceScorer(rules)
        for (said in listOf("okay sir my OTP is 4 5 8 2", "haan ruko otp bata raha hoon", "okay I am installing AnyDesk now",
                "I have transferred the money", "मैं पैसे भेज रहा हूँ")) {
            assertEquals(said, Level.DANGEROUS, sc.score(said).level)
        }
        // A bank's automated OTP call reads the code out; that is delivery, not the user giving it away.
        assertTrue(sc.score("your OTP is 4 5 8 2, it is valid for ten minutes").level < Level.DANGEROUS)
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
}
