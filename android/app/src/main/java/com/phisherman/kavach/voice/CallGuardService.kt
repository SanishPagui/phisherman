package com.phisherman.kavach.voice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.phisherman.kavach.engine.Engine
import com.phisherman.kavach.notify.Notifier

/**
 * Foreground service that listens in short speech-recognition sessions and feeds a
 * rolling transcript window to the VoiceAnalyser (PS4 Section 9.4). Shown with a visible
 * microphone notification — background mic use must be visible. Sessions end on silence,
 * so it restarts itself. Falls back cleanly if on-device recognition is unavailable.
 */
class CallGuardService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val window = ArrayDeque<Pair<Long, String>>() // timestamp, text
    private var listening = false

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            // A typed/file transcript line — the reliable demo/fallback path (Section 9.4).
            ACTION_FEED -> {
                intent.getStringExtra(EXTRA_TEXT)?.let { push(it) }
                return START_STICKY
            }
        }
        val notif = Notifier.callGuardNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifier.CALL_GUARD_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(Notifier.CALL_GUARD_ID, notif)
        }
        VoiceState.setRunning(true)
        VoiceAnalyser.reset()
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "speech recognition unavailable — use the typed-transcript fallback")
            return
        }
        val r = if (Build.VERSION.SDK_INT >= 33 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(b: Bundle) = pushFromBundle(b)
            override fun onResults(b: Bundle) { pushFromBundle(b); restart() }
            override fun onError(error: Int) { restart() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer = r
        listening = true
        runCatching { r.startListening(i) }
    }

    private fun restart() {
        if (!listening) return
        handler.postDelayed({ if (listening) { recognizer?.cancel(); startListening() } }, 300)
    }

    private fun pushFromBundle(b: Bundle) {
        val t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
        push(t)
    }

    private fun push(text: String) {
        val now = System.currentTimeMillis()
        window.addLast(now to text)
        val horizon = VoiceAnalyser.windowSeconds * 1000L
        while (window.isNotEmpty() && now - window.first().first > horizon) window.removeFirst()
        val joined = window.joinToString(" ") { it.second }
        VoiceState.setTranscript(joined)
        val verdict = Engine.updateVoice(joined)
        VoiceState.setVerdict(verdict)
        if (verdict.level == com.phisherman.kavach.engine.Level.DANGEROUS) {
            Notifier.updateCallGuardDanger(this)
        }
    }

    private fun stopEverything() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        VoiceState.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallGuardService"
        const val ACTION_STOP = "com.phisherman.kavach.STOP_GUARD"
        const val ACTION_FEED = "com.phisherman.kavach.FEED_TRANSCRIPT"
        const val EXTRA_TEXT = "text"

        fun start(ctx: Context) {
            val i = Intent(ctx, CallGuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, CallGuardService::class.java).setAction(ACTION_STOP))
        }

        /** Feed a line of a typed or played-file transcript (reliable demo path). */
        fun feed(ctx: Context, line: String) {
            ctx.startService(
                Intent(ctx, CallGuardService::class.java)
                    .setAction(ACTION_FEED).putExtra(EXTRA_TEXT, line)
            )
        }
    }
}
