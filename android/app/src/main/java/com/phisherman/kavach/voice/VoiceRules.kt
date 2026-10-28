package com.phisherman.kavach.voice

import kotlinx.serialization.Serializable

/** Parsed assets/voice_rules.json (PS4 Section 9.3). Hand-set priors, tuned on scripts. */
@Serializable
data class VoiceRules(
    val version: String = "voice-1",
    val windowSeconds: Int = 60,
    val bands: Bands = Bands(40, 70),
    val categories: List<Category> = emptyList(),
    val combinationBonus: CombinationBonus? = null,
) {
    @Serializable
    data class Bands(val suspicious: Int, val dangerous: Int)

    @Serializable
    data class Category(
        val id: String,
        val label: String,
        val labelHi: String? = null,
        val weight: Int,
        val patterns: List<String>,
    )

    @Serializable
    data class CombinationBonus(
        val value: Int,
        val label: String,
        val labelHi: String? = null,
        val requireAny: List<String>,
        val withAny: List<String>,
    )

    companion object {
        val EMPTY = VoiceRules()
    }
}
