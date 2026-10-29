package com.phisherman.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phisherman.R
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.ui.WarningActivity

object SmsNotifier {
    private const val CHANNEL = "sms_warnings"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Scam message warnings", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Shown right after a suspicious SMS arrives" })
        }
    }

    fun show(ctx: Context, sender: String, body: String, v: Verdict) {
        val id = body.trim().hashCode()
        val warning = WarningActivity.smsIntent(ctx, body, v).putExtra(WarningActivity.EXTRA_NOTIFICATION_ID, id)
        // Dangerous messages take over the screen when the user allowed "display over other apps".
        // The notification below is still posted as a fallback (the launch can be blocked);
        // WarningActivity cancels it once it is on screen.
        if (v.level == Level.DANGEROUS && Settings.canDrawOverlays(ctx)) launchOverOtherApps(ctx, warning)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(ctx, id, warning,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (v.level == Level.DANGEROUS) "⚠ Scam message from $sender" else "Suspicious message from $sender"
        val reason = v.reasons.firstOrNull()?.textEn ?: "This message looks risky"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\nDo not click links or share OTP, PIN or card details."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .apply { if (v.level == Level.DANGEROUS) setFullScreenIntent(open, true) }  // lock screen
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    /**
     * Since Android 15 an app holding "display over other apps" may start an activity from the
     * background only while one of its overlay windows is visible. Show a 1 px overlay, start
     * the activity, then remove the overlay. Also used by CallWatcher to start the call guard.
     */
    fun launchOverOtherApps(ctx: Context, intent: Intent) {
        val main = Handler(Looper.getMainLooper())
        main.post {
            val wm = ctx.getSystemService(WindowManager::class.java)
            val view = View(ctx)
            val lp = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            )
            val added = runCatching { wm.addView(view, lp) }.isSuccess
            main.postDelayed({
                runCatching { ctx.startActivity(intent) }
                if (added) main.postDelayed({ runCatching { wm.removeView(view) } }, 1500)
            }, if (added) 150 else 0)
        }
    }
}
