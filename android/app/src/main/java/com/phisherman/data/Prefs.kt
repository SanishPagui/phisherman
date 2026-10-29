package com.phisherman.data

import android.content.Context

object Prefs {
    private const val FILE = "phisherman"
    private const val KEY_BROWSER = "browser_package"
    private const val KEY_HINDI = "hindi"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun chosenBrowser(ctx: Context): String? = sp(ctx).getString(KEY_BROWSER, null)
    fun setChosenBrowser(ctx: Context, pkg: String) = sp(ctx).edit().putString(KEY_BROWSER, pkg).apply()

    /** Phisherman switched Live Caption on for a call and has not switched it back off yet. */
    fun captionsOnByUs(ctx: Context): Boolean = sp(ctx).getBoolean("captions_on_by_us", false)
    fun setCaptionsOnByUs(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("captions_on_by_us", on).commit()

    fun autoCallGuard(ctx: Context): Boolean = sp(ctx).getBoolean("auto_call_guard", false)
    fun setAutoCallGuard(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("auto_call_guard", on).apply()

    /** SYSTEM, LIGHT or DARK (ui.theme.ThemeMode). */
    fun themeMode(ctx: Context): String = sp(ctx).getString("theme_mode", "SYSTEM") ?: "SYSTEM"
    fun setThemeMode(ctx: Context, mode: String) = sp(ctx).edit().putString("theme_mode", mode).apply()

    fun hindi(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_HINDI, false)
    fun setHindi(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_HINDI, on).apply()
}
