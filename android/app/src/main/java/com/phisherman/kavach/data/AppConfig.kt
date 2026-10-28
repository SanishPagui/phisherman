package com.phisherman.kavach.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Runtime constants from assets/app_config.json — verify portal/helpline on demo day. */
@Serializable
data class AppConfig(
    val version: String = "cfg-1",
    val portalUrl: String = "https://cybercrime.gov.in",
    val helplineNumber: String = "1930",
    val biasMaxShift: Float = 0.15f,
    val biasClampMin: Float = 0.05f,
    val biasClampMax: Float = 0.95f,
) {
    companion object {
        @Volatile private var cached: AppConfig? = null

        fun get(ctx: Context): AppConfig = cached ?: synchronized(this) {
            cached ?: runCatching {
                ctx.assets.open("app_config.json").bufferedReader().use {
                    Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), it.readText())
                }
            }.getOrDefault(AppConfig()).also { cached = it }
        }
    }
}
