package com.phisherman.kavach.link

import kotlinx.serialization.Serializable

/** Parsed assets/brands.json (PS4 Section 4.2). */
@Serializable
data class Brands(
    val version: String = "brands-1",
    val tokens: List<String> = emptyList(),
    val official: List<String> = emptyList(),
    val multiSuffixes: List<String> = emptyList(),
    val shorteners: List<String> = emptyList(),
    val suspWords: List<String> = emptyList(),
    val riskyTlds: List<String> = emptyList(),
    val homoglyphMulti: List<List<String>> = emptyList(),
    val homoglyphSingle: Map<String, String> = emptyMap(),
    val allowlist: List<String> = emptyList(),
) {
    val multiSet: Set<String> by lazy { multiSuffixes.toHashSet() }
    val shortenerSet: Set<String> by lazy { shorteners.toHashSet() }
    val riskyTldSet: Set<String> by lazy { riskyTlds.toHashSet() }

    private val officialSet: Set<String> by lazy { official.toHashSet() }

    fun isOfficial(registrable: String): Boolean = registrable in officialSet

    /** Best-guess official domain for a brand token (first official domain containing it). */
    fun officialDomainFor(token: String): String? =
        official.firstOrNull { it.contains(token) }

    companion object {
        val EMPTY = Brands()
    }
}
