package com.phisherman.kavach.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phisherman.kavach.data.AppDatabase
import com.phisherman.kavach.data.Decision
import com.phisherman.kavach.data.DecisionLog
import com.phisherman.kavach.engine.Channel
import com.phisherman.kavach.engine.Engine
import com.phisherman.kavach.engine.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)

    val recent: StateFlow<List<Decision>> =
        db.decisionDao().recent(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flaggedCount: StateFlow<Int> =
        db.decisionDao().flaggedCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _pasteResult = MutableStateFlow<Verdict?>(null)
    val pasteResult: StateFlow<Verdict?> = _pasteResult

    private val _analysing = MutableStateFlow(false)
    val analysing: StateFlow<Boolean> = _analysing

    /** Paste box: a URL goes through the link model, anything else through the SMS model. */
    fun analyse(text: String) {
        val input = text.trim()
        if (input.isEmpty()) return
        _analysing.value = true
        viewModelScope.launch {
            val verdict = withContext(Dispatchers.Default) {
                if (!Engine.initialised) Engine.init(getApplication())
                if (looksLikeUrl(input)) Engine.scoreUrl(input)
                else Engine.scoreSms("paste", input)
            }
            DecisionLog.record(getApplication(), verdict, verdict.subject.ifEmpty { "paste" })
            _pasteResult.value = verdict
            _analysing.value = false
        }
    }

    fun clearPaste() { _pasteResult.value = null }

    fun clearHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) { db.decisionDao().clear() }
    }

    private fun looksLikeUrl(text: String): Boolean {
        if (text.contains(" ")) return false
        return text.startsWith("http://") || text.startsWith("https://") ||
            Regex("^[\\w.-]+\\.[a-z]{2,}(/.*)?$", RegexOption.IGNORE_CASE).matches(text)
    }
}
