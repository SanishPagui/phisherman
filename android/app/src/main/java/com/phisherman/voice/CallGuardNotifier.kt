package com.phisherman.voice

import com.phisherman.ui.theme.I18n
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phisherman.R
import com.phisherman.engine.Level

object CallGuardNotifier {
    const val ONGOING_ID = 4201
    private const val RED_ID = 4202
    private const val CH_ONGOING = "call_guard"
    private const val CH_ALERT = "call_alerts"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_ONGOING) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_ONGOING, I18n.t("Call guard (listening)"), NotificationManager.IMPORTANCE_LOW)
                .apply { description = I18n.t("Shown while Phisherman listens to a call on this phone") })
        }
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_ALERT, I18n.t("Scam call alerts"), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = I18n.t("Shown when a call sounds like a scam"); enableVibration(false) })  // CallAlerts vibrates
        }
    }

    private fun openGuard(ctx: Context) = PendingIntent.getActivity(ctx, 1,
        Intent(ctx, CallGuardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE)

    private fun action(ctx: Context, what: String, code: Int) = PendingIntent.getService(ctx, code,
        Intent(ctx, CallGuardService::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE)

    fun ongoing(ctx: Context, level: Level): Notification {
        ensureChannels(ctx)
        val text = when (level) {
            Level.DANGEROUS -> I18n.t("Scam signs heard. Hang up. Never share OTP or PIN.")
            Level.SUSPICIOUS -> I18n.t("Some warning signs heard. Be careful.")
            Level.SAFE -> I18n.t("Checking what is said, on this phone. Nothing is recorded.")
        }
        return NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(I18n.t("Phisherman call guard is listening"))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openGuard(ctx))
            .addAction(0, "Stop", action(ctx, CallGuardService.ACTION_STOP, 2))
            .build()
    }

    fun updateOngoing(ctx: Context, level: Level) {
        if (!canNotify(ctx)) return
        NotificationManagerCompat.from(ctx).notify(ONGOING_ID, ongoing(ctx, level))
    }

    fun red(ctx: Context, reason: String?) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(I18n.t("⚠ This call sounds like a scam"))
            .setContentText(I18n.t("Hang up. Never share OTP or PIN."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(I18n.t("Hang up. Never share OTP, PIN or card details, and do not install any app.") + "\n${reason ?: ""}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openGuard(ctx))
            .addAction(0, I18n.t("I'm safe, stop alert"), action(ctx, CallGuardService.ACTION_ACK, 3))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(RED_ID, n)
    }

    /** The automatic start was refused by Android: one tap on this opens the guard to start it by hand. */
    fun blocked(ctx: Context) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(I18n.t("Call guard: tap to start listening"))
            .setContentText(I18n.t("Never share OTP, PIN or card details on a call."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(PendingIntent.getActivity(ctx, 5,
                Intent(ctx, CallGuardActivity::class.java).putExtra(CallGuardActivity.EXTRA_START, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(RED_ID + 1, n)
    }

    fun cancelOngoing(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ONGOING_ID)

    fun cancelRed(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(RED_ID)

    private fun canNotify(ctx: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
