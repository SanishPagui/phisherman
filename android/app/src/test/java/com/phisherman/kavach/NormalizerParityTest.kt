package com.phisherman.kavach

import com.phisherman.kavach.sms.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Parity gate for the SMS normaliser (PS4 Section 8.3): the Kotlin port must reproduce the
 * Python reference (ml/normalize.py) exactly, so TF-IDF vocabulary lookups line up. Fixtures
 * are produced by ml/make_sms_parity.py (tab-separated: raw<TAB>normalized).
 */
class NormalizerParityTest {

    @Test
    fun normaliseMatchesPythonReference() {
        val stream = javaClass.classLoader!!.getResourceAsStream("sms_parity_expected.tsv")
        assertNotNull("Missing sms_parity_expected.tsv — run `python ml/make_sms_parity.py`", stream)
        val lines = stream!!.bufferedReader().use { it.readLines() }
        var checked = 0
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            val raw = parts[0]
            val expected = if (parts.size > 1) parts[1] else ""
            assertEquals("normalize(<$raw>)", expected, Normalizer.normalize(raw))
            checked++
        }
        println("Normaliser parity OK: $checked messages")
    }
}
