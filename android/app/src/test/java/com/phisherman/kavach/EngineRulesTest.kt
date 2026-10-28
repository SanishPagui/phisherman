package com.phisherman.kavach

import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.engine.RiskEngine
import com.phisherman.kavach.engine.Thresholds
import com.phisherman.kavach.engine.maxLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure-Kotlin unit tests for the engine's rule layer (no Android, no model). Verifies that
 * structural brand/URL rules escalate a modest model probability (PS4 Section 3.3 step 3).
 */
class EngineRulesTest {

    @Before
    fun setup() { RiskEngine.thresholds = Thresholds.DEFAULT }

    private fun features(block: FloatArray.() -> Unit): FloatArray =
        FloatArray(18).apply(block)

    @Test
    fun ipHostForcesDangerousEvenWhenModelIsLow() {
        val f = features { this[RiskEngine.F.HAS_IP] = 1f }
        val v = RiskEngine.fromLink(0.05f, f, RiskEngine.LinkSignals("203.0.113.10", ""), 1)
        assertEquals(Level.DANGEROUS, v.level)
        assertTrue(v.reasons.any { it.id == "L_IP_HOST" })
    }

    @Test
    fun typosquatBrandIsDangerous() {
        val f = features {
            this[RiskEngine.F.BRAND_MIN_LEV] = 0.12f
            this[RiskEngine.F.TLD_RISK] = 1f
        }
        val v = RiskEngine.fromLink(0.2f, f, RiskEngine.LinkSignals("paytrn.example", "paytrn.example", "paytm", "paytm.com"), 1)
        assertEquals(Level.DANGEROUS, v.level)
        assertTrue(v.reasons.any { it.id == "L_BRAND_LOOKALIKE" })
    }

    @Test
    fun deepNestingWithBrandIsDangerous() {
        val f = features {
            this[RiskEngine.F.SUBDOMAIN_DEPTH] = 4f
            this[RiskEngine.F.BRAND_IN_SUB] = 1f
        }
        val v = RiskEngine.fromLink(0.1f, f, RiskEngine.LinkSignals("login.sbi.com.attacker.example", "attacker.example"), 1)
        assertEquals(Level.DANGEROUS, v.level)
    }

    @Test
    fun cleanLinkWithLowProbStaysSafe() {
        val v = RiskEngine.fromLink(0.02f, FloatArray(18), RiskEngine.LinkSignals("wikipedia.org", "wikipedia.org"), 1)
        assertEquals(Level.SAFE, v.level)
        assertTrue(v.reasons.isEmpty())
    }

    @Test
    fun maxLevelPicksHigher() {
        assertEquals(Level.DANGEROUS, maxLevel(Level.SAFE, Level.DANGEROUS))
        assertEquals(Level.SUSPICIOUS, maxLevel(Level.SUSPICIOUS, Level.SAFE))
    }
}
