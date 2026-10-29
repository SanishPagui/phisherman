package com.phisherman.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.phisherman.data.Prefs
import com.phisherman.sms.SmsNotifier

/**
 * Automatic call guard: when a phone call starts, start listening; when it ends, stop.
 * Needs READ_PHONE_STATE (call state only, never the number) and the auto-guard setting on.
 * Note: Android usually gives other apps silence from the microphone during a call; the service
 * measures the input level so the app can tell the user if that is happening on their phone.
 */
object CallWatcher {
    private const val TAG = "CallWatcher"
    private var registered: Any? = null
    @Volatile var inCall = false
        private set

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
                    TelephonyManager.CALL_STATE_OFFHOOK -> if (!inCall) { inCall = true; startGuard(app) }
                    TelephonyManager.CALL_STATE_IDLE -> if (inCall) { inCall = false; stopGuard(app) }
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
    }

    fun stop(ctx: Context) {
        val cb = registered as? TelephonyCallback ?: return
        ctx.applicationContext.getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(cb)
        registered = null
    }

    private fun startGuard(ctx: Context) {
        val mode = CallGuard.state.value.mode
        if (mode == GuardMode.MIC && CallGuardService.running) { Log.d(TAG, "already listening"); return }
        // A demo script, or a session whose service died, must not block the real call.
        if (mode != GuardMode.OFF) CallGuard.end(ctx)
        // Android 14+ gives a microphone service started from the background no mic access.
        // With "display over other apps" we briefly bring a transparent activity to the front
        // and start the service from there, so it counts as started from the foreground.
        if (Settings.canDrawOverlays(ctx)) {
            Log.i(TAG, "starting call guard via foreground starter")
            SmsNotifier.launchOverOtherApps(ctx, Intent(ctx, CallGuardStarterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            return
        }
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CallGuardService::class.java).setAction(CallGuardService.ACTION_AUTO))
        } catch (e: Exception) {
            // Android 12+ can refuse to start a microphone service from the background
            Log.w(TAG, "could not start call guard automatically", e)
            CallGuard.onError("Android did not allow the call guard to start automatically: ${e.javaClass.simpleName}")
        }
    }

    private fun stopGuard(ctx: Context) {
        if (CallGuard.state.value.mode != GuardMode.MIC) return
        ctx.startService(Intent(ctx, CallGuardService::class.java).setAction(CallGuardService.ACTION_STOP))
    }
}
