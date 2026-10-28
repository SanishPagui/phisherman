package com.phisherman.kavach.voice

import com.phisherman.kavach.engine.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shared observable state between CallGuardService and the Compose UI. */
object VoiceState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _verdict = MutableStateFlow<Verdict?>(null)
    val verdict: StateFlow<Verdict?> = _verdict

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    fun setRunning(on: Boolean) { _running.value = on }
    fun setVerdict(v: Verdict?) { _verdict.value = v }
    fun setTranscript(t: String) { _transcript.value = t }
    fun reset() { _verdict.value = null; _transcript.value = "" }
}
