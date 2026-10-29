package com.phisherman.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Invisible, finishes at once. Exists only so the call guard's microphone service is started
 * while Phisherman is in the foreground: Android 14+ denies microphone access to a foreground
 * service started from the background, which is where CallWatcher is when a call begins.
 */
class CallGuardStarterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ContextCompat.startForegroundService(this, Intent(this, CallGuardService::class.java).setAction(CallGuardService.ACTION_AUTO))
        } catch (e: Exception) {
            Log.w("CallGuardStarter", "could not start call guard", e)
            CallGuard.onError("Android did not allow the call guard to start: ${e.javaClass.simpleName}")
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
