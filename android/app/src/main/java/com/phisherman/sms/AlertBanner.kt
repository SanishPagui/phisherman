package com.phisherman.sms

import com.phisherman.ui.theme.I18n
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.ui.WarningActivity

/**
 * A red banner drawn over the current app (e.g. an open WhatsApp chat) by the accessibility
 * service. Unlike a notification it cannot be shrunk to an edge flash by the phone's pop-up
 * settings. Tap = full warning screen; auto-hides after 15 s.
 */
class AlertBanner(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show(body: String, v: Verdict) {
        val title = if (v.level == Level.DANGEROUS) I18n.t("⚠ Scam message from ${v.subject}") else I18n.t("Suspicious message from ${v.subject}")
        warn(title, v.reasons.firstOrNull()?.let { I18n.reason(it) } ?: I18n.t("This message looks risky"),
            I18n.t("Don't click links or share OTP, PIN or card details."), v.level) { openDetails(body, v) }
    }

    /** Any warning banner; onDetails = null hides the VIEW DETAILS action. */
    fun warn(title: String, line1: String, line2: String, level: Level, onDetails: (() -> Unit)?) = main.post {
        hide()
        val dp = { x: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x.toFloat(), service.resources.displayMetrics).toInt() }
        val red = if (level == Level.DANGEROUS) Color.rgb(0xB3, 0x26, 0x1E) else Color.rgb(0xB2, 0x6A, 0x00)
        val box = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply { setColor(red); cornerRadius = dp(18).toFloat() }
            elevation = dp(8).toFloat()
        }
        fun text(s: String, size: Float, bold: Boolean = false) = TextView(service).apply {
            this.text = s; setTextColor(Color.WHITE); textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(text(title, 18f, bold = true))
        box.addView(text(line1, 15f))
        box.addView(text(line2, 13f))
        val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(8), 0, 0) }
        row.addView(text("DISMISS", 14f, bold = true).apply { setPadding(dp(12), dp(6), dp(12), dp(6)); setOnClickListener { hide() } })
        if (onDetails != null) {
            row.addView(text(I18n.t("VIEW DETAILS"), 14f, bold = true).apply {
                setPadding(dp(12), dp(6), dp(4), dp(6))
                setOnClickListener { onDetails() }
            })
            box.setOnClickListener { onDetails() }
        }
        box.addView(row)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP; x = 0; y = dp(96); horizontalMargin = 0.03f }  // below Samsung's pop-up pill
        try {
            wm.addView(box, lp)
            view = box
        } catch (e: Exception) {
            return@post
        }
        vibrate()
        main.postDelayed({ hide() }, 15_000)
        Unit
    }

    private fun openDetails(body: String, v: Verdict) {
        hide()
        service.startActivity(WarningActivity.smsIntent(service, body, v).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun hide() {
        main.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun vibrate() = com.phisherman.voice.CallAlerts.pulse(service)
}
