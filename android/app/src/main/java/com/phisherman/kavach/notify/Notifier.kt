package com.phisherman.kavach.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.phisherman.kavach.R
import com.phisherman.kavach.data.Prefs
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.engine.Verdict
import com.phisherman.kavach.ui.ReportActivity
import com.phisherman.kavach.ui.VerdictExtras

/**
 * Notification channels + builders. The SMS alert is a high-importance heads-up with the
 * top reason; tapping it opens the report screen (PS4 Section 3.4). The call guard shows a
 * persistent, visible notification while listening (PS4 Section 3.5).
 */
object Notifier {

    const val ALERTS_CHANNEL = "alerts"
    const val GUARD_CHANNEL = "call_guard"
    const val CALL_GUARD_ID = 42
    private var smsId = 1000

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERTS_CHANNEL, ctx.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_alerts_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(GUARD_CHANNEL, ctx.getString(R.string.channel_guard_name),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = ctx.getString(R.string.channel_guard_desc)
            }
        )
    }

    fun showSms(ctx: Context, sender: String, verdict: Verdict, decisionId: Long) {
        val hindi = Prefs.hindi(ctx)
        val top = verdict.reasons.firstOrNull()?.text(hindi) ?: "Message looks risky"
        val title = when (verdict.level) {
            Level.DANGEROUS -> if (hindi) "⚠️ खतरनाक संदेश" else "⚠️ Dangerous message"
            else -> if (hindi) "संदिग्ध संदेश" else "Suspicious message"
        }
        val tap = PendingIntent.getActivity(
            ctx, decisionId.toInt().coerceAtLeast(1),
            VerdictExtras.put(Intent(ctx, ReportActivity::class.java), verdict, decisionId = decisionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, ALERTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$title · $sender")
            .setContentText(top)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$top\n\nTap to review and report."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(0xFFE5484D.toInt())
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        notify(ctx, smsId++, n)
    }

    fun callGuardNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, GUARD_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(ctx.getString(R.string.call_guard_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setColor(0xFF3DDC97.toInt())
            .build()

    fun updateCallGuardDanger(ctx: Context) {
        val n = NotificationCompat.Builder(ctx, GUARD_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (Prefs.hindi(ctx)) "⚠️ फ़ोन रखें — OTP/PIN साझा न करें" else "⚠️ Hang up — never share OTP or PIN")
            .setContentText(if (Prefs.hindi(ctx)) "यह कॉल स्कैम जैसी लगती है" else "This call looks like a scam")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setColor(0xFFE5484D.toInt())
            .build()
        notify(ctx, CALL_GUARD_ID, n)
    }

    private fun notify(ctx: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        }
        androidx.core.app.NotificationManagerCompat.from(ctx).notify(id, n)
    }
}
