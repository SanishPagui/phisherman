package com.phisherman.kavach

import com.phisherman.kavach.link.Brands
import com.phisherman.kavach.link.UrlFeatures
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity gate (PS4 Section 6.7): the Kotlin feature port must reproduce the Python
 * reference (ml/features.py) within 1e-4 on all 30 parity URLs. Fixtures are produced by
 * ml/make_parity.py and copied into test/resources. Probability parity (0.01) is checked
 * separately on-device, since ONNX Runtime is Android-only.
 */
class UrlFeaturesParityTest {

    private fun resource(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
        assertNotNull("Missing test resource $name — run `python ml/make_parity.py`", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    @Test
    fun featuresMatchPythonReference() {
        val brands = Json { ignoreUnknownKeys = true }
            .decodeFromString(Brands.serializer(), resource("brands.json"))
        val lines = resource("parity_expected.csv").trim().lines()
        val header = lines.first().split(",")
        val featureCols = UrlFeatures.FEATURE_ORDER.map { header.indexOf(it) }
        featureCols.forEach { assertTrue("feature column missing in CSV", it >= 0) }

        var maxDiff = 0.0
        var checked = 0
        for (line in lines.drop(1)) {
            val cells = splitCsv(line)
            val url = cells[0]
            val expected = featureCols.map { cells[it].toDouble() }
            val actual = UrlFeatures.compute(url, brands).vector
            for (i in expected.indices) {
                val diff = kotlin.math.abs(expected[i] - actual[i])
                if (diff > maxDiff) maxDiff = diff
                assertTrue(
                    "Feature ${UrlFeatures.FEATURE_ORDER[i]} for <$url>: " +
                        "expected ${expected[i]}, got ${actual[i]} (diff $diff)",
                    diff <= 1e-4,
                )
                checked++
            }
        }
        println("Parity OK: $checked feature values, max diff $maxDiff")
    }

    /** Minimal CSV splitter (parity URLs contain no quoted commas). */
    private fun splitCsv(line: String): List<String> = line.split(",")
}
