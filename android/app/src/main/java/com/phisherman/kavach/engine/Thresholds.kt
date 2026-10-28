package com.phisherman.kavach.engine

import kotlinx.serialization.Serializable

/** Decision thresholds loaded from assets/thresholds.json (produced by train_link.py). */
@Serializable
data class Thresholds(
    val version: String = "thr-1",
    val link: Band = Band(0.3f, 0.5f),
    val sms: Band = Band(0.45f, 0.70f),
    val rules: Rules = Rules(),
) {
    @Serializable
    data class Band(val suspicious: Float, val dangerous: Float)

    /** Structural-rule cutoffs and score floors; source of truth is thresholds.json. */
    @Serializable
    data class Rules(
        val deepNestDepth: Float = 3f,
        val typosquatLev: Float = 0.25f,
        val highEntropy: Float = 3.6f,
        val ipFloor: Int = 80,
        val atFloor: Int = 65,
        val deepNestFloor: Int = 60,
        val brandInSubFloor: Int = 70,
        val typosquatFloor: Int = 78,
        val shortenerFloor: Int = 45,
    )

    companion object {
        val DEFAULT = Thresholds()
    }
}
