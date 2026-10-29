package com.phisherman.voice

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.phisherman.data.Prefs
import com.phisherman.engine.Level

/**
 * Listens through the microphone with the ON-DEVICE speech recogniser only (nothing goes to a
 * server), restarts recognition whenever a session ends, and feeds text to CallGuard.
 * Foreground service with a visible notification while listening (PS4 9.4).
 */
class CallGuardService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopGuard(); return START_NOT_STICKY }
            ACTION_ACK -> {
                CallGuard.acknowledge(this)
                if (!listening) stopSelf()  // tapped during a demo script: no mic session to keep alive
                return START_NOT_STICKY
            }
        }
        val n = CallGuardNotifier.ongoing(this, Level.SAFE)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(CallGuardNotifier.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(CallGuardNotifier.ONGOING_ID, n)
        }
        if (!listening) {
            CallGuard.begin(this, GuardMode.MIC)
            listening = true
            running = true
            Log.i(TAG, "listening (${intent?.action ?: "manual"})")
            startListening()
        }
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (!listening) return
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            CallGuard.onError("This phone has no offline speech recognition. Use a demo script instead.")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(b: Bundle) = text(b)?.let { CallGuard.onPartial(this@CallGuardService, it) } ?: Unit
            override fun onResults(b: Bundle) {
                text(b)?.let { CallGuard.onFinal(this@CallGuardService, it) }
                main.postDelayed({ startListening() }, 100)  // sessions end on silence; restart
            }
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        CallGuard.onError("Offline speech for ${language()} is not downloaded. Settings > Google > Speech, or pick English.")
                        if (Build.VERSION.SDK_INT >= 33) r.triggerModelDownload(recognizerIntent())
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> CallGuard.onError("Microphone permission is needed.")
                    else -> Log.d(TAG, "recogniser error $error, restarting")
                }
                main.postDelayed({ startListening() }, 400)
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { CallGuard.onLevel(v) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        recognizer = r
        r.startListening(recognizerIntent())
    }

    private fun language() = if (Prefs.hindi(this)) "hi-IN" else "en-IN"

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun text(b: Bundle): String? = rawText(b)?.also { Log.d(TAG, "heard ${it.length} chars") }

    private fun rawText(b: Bundle): String? = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun stopGuard() {
        // Near -100 dB for a whole call means Android gave us silence while the call was active.
        Log.i(TAG, "stopped, loudest mic input %.0f dB".format(CallGuard.state.value.peakDb))
        listening = false
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        CallGuard.end(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (listening) stopGuard()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallGuardService"
        const val ACTION_STOP = "com.phisherman.voice.STOP"
        const val ACTION_ACK = "com.phisherman.voice.ACK"
        const val ACTION_AUTO = "com.phisherman.voice.AUTO"  // started by CallWatcher when a call begins

        /** True while a microphone session is live; lets CallWatcher detect a stale GuardState. */
        @Volatile var running = false
            private set
    }
}
