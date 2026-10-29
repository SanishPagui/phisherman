package com.phisherman

import com.phisherman.ui.Confusion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** PS4 deliverable 4: the phone's metric formulas match ml/eval_report.py, and the files it reads are sound. */
class MetricsTest {
    @Test
    fun confusionMatchesPythonSummary() {
        // email engine result from ml/email_eval.py: tp 27, fp 10, fn 3, tn 20
        val c = Confusion(tp = 27, fp = 10, fn = 3, tn = 20)
        assertEquals(60, c.n)
        assertEquals(0.7297, c.precision, 1e-4)
        assertEquals(0.9, c.recall, 1e-9)
        assertEquals(c.recall, c.fdr, 0.0)
        assertEquals(0.7833, c.accuracy, 1e-4)
        assertEquals(0.3333, c.fpr, 1e-4)
        assertEquals(0.0, Confusion().precision, 0.0)  // no division by zero on an empty set
    }

    @Test
    fun resultsJsonHasOverallAndEveryChannel() {
        val r = Json.parseToJsonElement(File("src/main/assets/results.json").readText()).jsonObject
        val ids = r["channels"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertTrue(ids.toString(), ids.containsAll(listOf("link", "sms", "email", "calls")))
        val o = r["overall"]!!.jsonObject
        val sum = o["tp"]!!.jsonPrimitive.int + o["fp"]!!.jsonPrimitive.int + o["fn"]!!.jsonPrimitive.int + o["tn"]!!.jsonPrimitive.int
        assertEquals(o["n"]!!.jsonPrimitive.int, sum)
        assertEquals(o["recall"]!!.jsonPrimitive.double, o["fdr"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun benchSampleIsWellFormed() {
        val rows = File("src/main/assets/bench.tsv").readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.map { it.split("\t") }
        assertTrue(rows.all { it.size == 5 && it[1] in setOf("0", "1") })
        for (ch in listOf("link", "message", "email")) {
            val part = rows.filter { it[0] == ch }
            assertTrue("$ch has scams and safe items", part.any { it[1] == "1" } && part.any { it[1] == "0" })
        }
    }
}
