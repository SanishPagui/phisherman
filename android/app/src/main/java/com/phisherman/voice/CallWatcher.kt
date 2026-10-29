package com.phisherman.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.phisherman.data.Prefs

/**
 * Automatic call guard: when a phone call or an internet call (WhatsApp, Telegram, ...) starts,
 * start listening; when it ends, stop. Internet calls are not reported by the phone network, so
 * they are recognised by two signals together: the app's ongoing call notification (seen by
 * MessageNotificationListener) and the audio system in voice-call mode.
 * Needs READ_PHONE_STATE (call state only, never the number) and the auto-guard setting on.
 * Note: Android usually gives other apps silence from the microphone during a call; the service
 * measures the input level so the app can tell the user if that is happening on their phone.
 */
object CallWatcher {
    private const val TAG = "CallWatcher"
    private var registered: Any? = null
    private var modeListener: android.media.AudioManager.OnModeChangedListener? = null
    @Volatile private var phoneCall = false
    /** The app whose internet call notification is showing (e.g. "WhatsApp"), null if none. */
    @Volatile private var voipNotice: String? = null
    @Volatile private var voipCall = false
    val inCall: Boolean get() = phoneCall || voipCall
    /** "WhatsApp" during a WhatsApp call, null during a phone call. */
    val callApp: String? get() = if (voipCall && !phoneCall) voipNotice else null

    fun canWatch(ctx: Context) = Build.VERSION.SDK_INT >= 31 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(ctx: Context) {
        if (registered != null || !canWatch(ctx) || !Prefs.autoCallGuard(ctx)) return
        val app = ctx.applicationContext
        val tm = app.getSystemService(TelephonyManager::class.java)
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                Log.d(TAG, "call state $state")
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> if (!phoneCall) {
                        val was = inCall
                        phoneCall = true
                        if (!was) callStarted(app)
                    }
                    TelephonyManager.CALL_STATE_IDLE -> if (phoneCall) {
                        phoneCall = false
                        if (!inCall) callEnded(app)
                    }
                }
            }
        }
        try {
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(app), cb)
            registered = cb
            Log.i(TAG, "watching calls")
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot watch calls", e)
        }
        val am = app.getSystemService(android.media.AudioManager::class.java)
        val ml = android.media.AudioManager.OnModeChangedListener { mode -> Log.d(TAG, "audio mode $mode"); updateVoip(app) }
        am.addOnModeChangedListener(ContextCompat.getMainExecutor(app), ml)
        modeListener = ml
    }

    fun stop(ctx: Context) {
        val app = ctx.applicationContext
        modeListener?.let { app.getSystemService(android.media.AudioManager::class.java).removeOnModeChangedListener(it) }
        modeListener = null
        val cb = registered as? TelephonyCallback ?: return
        app.getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(cb)
        registered = null
    }

    /** From the notification listener: an internet-call app shows (or removed) its call notification. */
    fun onVoipNotification(ctx: Context, app: String?) {
        if (voipNotice == app) return
        voipNotice = app
        Log.d(TAG, "internet call notification: ${app ?: "gone"}")
        val appCtx = ctx.applicationContext
        ContextCompat.getMainExecutor(appCtx).execute { updateVoip(appCtx) }
    }

    /** An internet call is live while its notification shows AND the audio is in voice-call mode. */
    private fun updateVoip(app: Context) {
        if (registered == null) return  // auto guard off
        val mode = app.getSystemService(android.media.AudioManager::class.java).mode
        val live = voipNotice != null && mode == android.media.AudioManager.MODE_IN_COMMUNICATION
        if (live == voipCall) return
        val was = inCall
        voipCall = live
        Log.i(TAG, if (live) "internet call started ($voipNotice)" else "internet call ended")
        if (!was && inCall) callStarted(app)
        if (was && !inCall) callEnded(app)
    }

    private fun callStarted(app: Context) {
        captionsOnForCall(app)
        startGuard(app)
    }

    private fun callEnded(app: Context) {
        stopGuard(app)
        CallAlerts.stop(app)  // whatever raised the alert, hanging up ends it
        captionsOffAfterCall(app)
    }

    private const val CAPTIONS = "odi_captions_enabled"

    private fun canWriteSecure(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * With WRITE_SECURE_SETTINGS (granted once over adb), switch Live Caption on just for the call.
     * "We turned it on" is saved, not kept in memory, so captions are switched back off even if
     * the app was killed during the call (see restoreCaptions).
     */
    private fun captionsOnForCall(ctx: Context) {
        if (!canWriteSecure(ctx) || liveCaptionOn(ctx)) return
        try {
            android.provider.Settings.Secure.putInt(ctx.contentResolver, CAPTIONS, 1)
            Prefs.setCaptionsOnByUs(ctx, true)
            Log.i(TAG, "Live Caption switched on for this call")
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot switch Live Caption on", e)
        }
    }

    private fun captionsOffAfterCall(ctx: Context) {
        if (!Prefs.captionsOnByUs(ctx)) return
        runCatching { android.provider.Settings.Secure.putInt(ctx.contentResolver, CAPTIONS, 0) }
        Prefs.setCaptionsOnByUs(ctx, false)
        Log.i(TAG, "Live Caption switched back off")
    }

    /** On app start: if Phisherman left captions on (app killed mid-call) and no call is active, undo it. */
    fun restoreCaptions(ctx: Context) {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val idle = runCatching { tm.callStateForSubscription == TelephonyManager.CALL_STATE_IDLE }.getOrDefault(true)
        if (idle && !voipCall) captionsOffAfterCall(ctx)
    }

    /** Live Caption on: Android's own captioning can hear calls, so read its box instead of the mic. */
    fun liveCaptionOn(ctx: Context): Boolean =
        android.provider.Settings.Secure.getInt(ctx.contentResolver, "odi_captions_enabled", 0) == 1

    private fun startGuard(ctx: Context) {
        val mode = CallGuard.state.value.mode
        if (mode == GuardMode.MIC || mode == GuardMode.CAPTION) { Log.i(TAG, "call started; already guarding"); return }
        if (mode == GuardMode.SCRIPT) CallGuard.end(ctx)  // a real call takes over from a demo script
        if (liveCaptionOn(ctx) && com.phisherman.sms.ChatScreenReader.isEnabled(ctx)) {
            // Android gives our microphone silence during calls; captions arrive via ChatScreenReader
            Log.i(TAG, "call started; guarding through Live Caption")
            CallGuard.begin(ctx, GuardMode.CAPTION)
            CallGuardNotifier.updateOngoing(ctx, com.phisherman.engine.Level.SAFE)
            return
        }
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CallGuardService::class.java).setAction(CallGuardService.ACTION_AUTO))
            Log.i(TAG, "call started; call guard requested")
        } catch (e: Exception) {
            // Android 12+ can refuse to start a microphone service from the background
            Log.w(TAG, "could not start call guard automatically", e)
            CallGuard.onError("Android did not allow the call guard to start automatically: ${e.javaClass.simpleName}")
        }
    }

    private fun stopGuard(ctx: Context) {
        when (CallGuard.state.value.mode) {
            GuardMode.MIC -> ctx.startService(Intent(ctx, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP))
            GuardMode.CAPTION -> CallGuard.end(ctx)
            else -> {}
        }
    }
}
