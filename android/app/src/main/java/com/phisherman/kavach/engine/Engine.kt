package com.phisherman.kavach.engine

import android.content.Context
import android.util.Log
import com.phisherman.kavach.data.Prefs
import com.phisherman.kavach.link.UrlAnalyser
import com.phisherman.kavach.sms.SmsAnalyser
import com.phisherman.kavach.sms.SmsModel
import com.phisherman.kavach.voice.VoiceAnalyser
import kotlinx.serialization.json.Json

/**
 * Single entry point used by the Android adapters (LinkRouterActivity, SmsReceiver,
 * CallGuardService). Loads every on-device asset once and exposes the three scoring
 * calls plus the live threshold-bias control shown to judges.
 */
object Engine {

    private const val TAG = "Engine"
    private var baseThresholds: Thresholds = Thresholds.DEFAULT
    @Volatile var initialised = false
        private set

    fun init(ctx: Context) {
        if (initialised) return
        val json = Json { ignoreUnknownKeys = true }
        baseThresholds = runCatching {
            ctx.assets.open("thresholds.json").bufferedReader().use {
                json.decodeFromString(Thresholds.serializer(), it.readText())
            }
        }.getOrDefault(Thresholds.DEFAULT)

        UrlAnalyser.init(ctx)
        SmsModel.init(ctx)
        SmsAnalyser.init(ctx)
        VoiceAnalyser.init(ctx)

        applyBias(ctx, Prefs.thresholdBias(ctx))
        initialised = true
        Log.i(TAG, "engine ready (link=${UrlAnalyser.isReady}, sms=${SmsModel.isReady})")
    }

    fun scoreUrl(url: String): Verdict = UrlAnalyser.score(url)

    fun scoreSms(sender: String, body: String): Verdict = SmsAnalyser.analyse(sender, body)

    fun updateVoice(transcript: String): Verdict = VoiceAnalyser.update(transcript)

    /**
     * Live precision/recall trade-off (PS4 Section 6.7). bias in [-1,1]:
     *  -1 lowers thresholds (catch more, more false alarms),
     *  +1 raises them (fewer false alarms, more misses).
     */
    fun applyBias(ctx: Context, bias: Float) {
        val cfg = com.phisherman.kavach.data.AppConfig.get(ctx)
        val b = bias.coerceIn(-1f, 1f)
        val shift = cfg.biasMaxShift * b
        fun adj(x: Float) = (x + shift).coerceIn(cfg.biasClampMin, cfg.biasClampMax)
        RiskEngine.thresholds = baseThresholds.copy(
            link = Thresholds.Band(adj(baseThresholds.link.suspicious), adj(baseThresholds.link.dangerous)),
            sms = Thresholds.Band(adj(baseThresholds.sms.suspicious), adj(baseThresholds.sms.dangerous)),
        )
    }

    val thresholds: Thresholds get() = RiskEngine.thresholds
}
