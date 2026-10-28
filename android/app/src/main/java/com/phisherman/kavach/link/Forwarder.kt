package com.phisherman.kavach.link

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.phisherman.kavach.data.Prefs

/**
 * Forwards a safe (or user-approved) link to the user's REAL browser. Never forwards to
 * ourselves — that would loop endlessly (edge case 12.1 "Forwarding loop").
 */
object Forwarder {

    fun openInRealBrowser(ctx: Context, uri: Uri): Boolean {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val flags = if (android.os.Build.VERSION.SDK_INT >= 23)
            PackageManager.MATCH_ALL else 0
        val others = ctx.packageManager
            .queryIntentActivities(probe, flags)
            .map { it.activityInfo.packageName }
            .filter { it != ctx.packageName }
            .distinct()

        val chosen = Prefs.chosenBrowser(ctx)
        val target = chosen?.takeIf { it in others } ?: others.firstOrNull()

        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (target != null) i.setPackage(target)
        return try {
            ctx.startActivity(i)
            true
        } catch (t: Throwable) {
            // No other browser installed (edge case 12.1): let the caller show a message.
            false
        }
    }

    /** Browsers available to forward to, for the Settings picker. */
    fun availableBrowsers(ctx: Context): List<Pair<String, String>> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val flags = if (android.os.Build.VERSION.SDK_INT >= 23) PackageManager.MATCH_ALL else 0
        return ctx.packageManager.queryIntentActivities(probe, flags)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(ctx.packageManager).toString() }
            .distinctBy { it.first }
    }
}
