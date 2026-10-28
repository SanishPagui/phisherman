package com.phisherman.kavach.data

import android.content.Context

/** Lightweight SharedPreferences wrapper for small app settings. */
object Prefs {
    private const val FILE = "kavach_prefs"
    private const val KEY_BROWSER = "chosen_browser"
    private const val KEY_HINDI = "hindi"
    private const val KEY_THRESHOLD_BIAS = "threshold_bias" // -1.0..1.0, recall<->precision

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun chosenBrowser(ctx: Context): String? = prefs(ctx).getString(KEY_BROWSER, null)
    fun setChosenBrowser(ctx: Context, pkg: String?) =
        prefs(ctx).edit().putString(KEY_BROWSER, pkg).apply()

    fun hindi(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HINDI, false)
    fun setHindi(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_HINDI, on).apply()

    /** -1 favours recall (more catches), +1 favours precision (fewer false alarms). */
    fun thresholdBias(ctx: Context): Float = prefs(ctx).getFloat(KEY_THRESHOLD_BIAS, 0f)
    fun setThresholdBias(ctx: Context, v: Float) =
        prefs(ctx).edit().putFloat(KEY_THRESHOLD_BIAS, v).apply()
}
