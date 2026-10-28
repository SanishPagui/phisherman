package com.phisherman.kavach.link

/**
 * A small set of trusted registrable domains that return SAFE instantly, without
 * calling the model (PS4 Section 7.5). Reduces false alarms and latency. Seeded entirely from
 * brands.json (official + allowlist); nothing is hardcoded here.
 */
object Allowlist {

    @Volatile
    private var extra: Set<String> = emptySet()

    fun seedFromBrands(brands: Brands) {
        extra = (brands.official + brands.allowlist).toSet()
    }

    /** Returns the matched registrable domain if allowlisted, else null. */
    fun match(registrable: String): String? {
        if (registrable.isEmpty()) return null
        if (registrable in extra) return registrable
        return null
    }
}
