package com.phisherman.kavach.ui

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.phisherman.kavach.data.Prefs
import com.phisherman.kavach.engine.Engine
import com.phisherman.kavach.engine.Verdict
import com.phisherman.kavach.ui.screens.HistoryScreen
import com.phisherman.kavach.ui.screens.HomeScreen
import com.phisherman.kavach.ui.screens.MetricsScreen
import com.phisherman.kavach.ui.screens.SettingsScreen
import com.phisherman.kavach.ui.theme.PhishermanTheme
import com.phisherman.kavach.voice.CallGuardService
import com.phisherman.kavach.voice.VoiceState

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { recomputeDefault() }

    private var isDefaultBrowser by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recomputeDefault()
        requestInitialPermissions()
        val shared = extractSharedText(intent)

        setContent {
            var hindi by remember { mutableStateOf(Prefs.hindi(this)) }
            var tab by remember { mutableStateOf(0) }
            var sensitivity by remember { mutableStateOf(Prefs.thresholdBias(this)) }

            val recent by vm.recent.collectAsState()
            val flagged by vm.flaggedCount.collectAsState()
            val pasteResult by vm.pasteResult.collectAsState()
            val analysing by vm.analysing.collectAsState()
            val voiceRunning by VoiceState.running.collectAsState()
            val voiceVerdict by VoiceState.verdict.collectAsState()
            val voiceTranscript by VoiceState.transcript.collectAsState()

            // Share-to-app fallback: if launched with shared text, analyse it once.
            androidx.compose.runtime.LaunchedEffect(shared) {
                if (shared != null) { tab = 0; vm.analyse(shared) }
            }

            PhishermanTheme(hindi = hindi) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(tabTitle(tab, hindi)) },
                            actions = {
                                IconButton(onClick = {
                                    hindi = !hindi; Prefs.setHindi(this@MainActivity, hindi)
                                }) {
                                    Icon(Icons.Rounded.Translate, "Language",
                                        tint = if (hindi) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                        )
                    },
                    bottomBar = { BottomBar(tab, hindi) { tab = it } },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        when (tab) {
                            0 -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                HomeScreen(
                                    isDefaultBrowser = isDefaultBrowser,
                                    flaggedCount = flagged,
                                    checksCount = recent.size,
                                    pasteResult = pasteResult,
                                    analysing = analysing,
                                    voiceRunning = voiceRunning,
                                    voiceVerdict = voiceVerdict,
                                    voiceTranscript = voiceTranscript,
                                    onAnalyse = vm::analyse,
                                    onClearPaste = vm::clearPaste,
                                    onSetDefaultBrowser = ::requestDefaultBrowser,
                                    onStartGuard = ::startGuard,
                                    onStopGuard = { CallGuardService.stop(this@MainActivity) },
                                    onFeedTranscript = { CallGuardService.feed(this@MainActivity, it) },
                                    onReport = ::openReport,
                                )
                            }
                            1 -> HistoryScreen(decisions = recent, onClear = vm::clearHistory)
                            2 -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                MetricsScreen(results = remember { Results.load(this@MainActivity) })
                            }
                            else -> SettingsScreen(
                                hindi = hindi,
                                onHindiChange = { hindi = it; Prefs.setHindi(this@MainActivity, it) },
                                isDefaultBrowser = isDefaultBrowser,
                                onSetDefaultBrowser = ::requestDefaultBrowser,
                                sensitivity = sensitivity,
                                onSensitivityChange = {
                                    sensitivity = it
                                    Prefs.setThresholdBias(this@MainActivity, it)
                                    Engine.applyBias(this@MainActivity, it)
                                },
                                onClearHistory = vm::clearHistory,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recomputeDefault()
    }

    // ---- actions ----

    private fun openReport(verdict: Verdict) {
        startActivity(VerdictExtras.put(Intent(this, ReportActivity::class.java), verdict))
    }

    private fun startGuard() {
        val needed = buildList {
            add(android.Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(android.Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            requestPermission.launch(needed.toTypedArray())
        } else {
            CallGuardService.start(this)
        }
    }

    private fun requestInitialPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            add(android.Manifest.permission.RECEIVE_SMS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) requestPermission.launch(perms.toTypedArray())
    }

    private fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                !rm.isRoleHeld(RoleManager.ROLE_BROWSER)
            ) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                return
            }
        }
        // Fallback: open the default-apps settings page.
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    private fun recomputeDefault() {
        isDefaultBrowser = computeIsDefaultBrowser()
    }

    private fun computeIsDefaultBrowser(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return rm.isRoleHeld(RoleManager.ROLE_BROWSER)
            }
        }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val resolved = packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

@Composable
private fun BottomBar(selected: Int, hindi: Boolean, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = if (hindi) listOf("होम", "इतिहास", "परिणाम", "सेटिंग्स")
        else listOf("Home", "History", "Results", "Settings")
        val icons = listOf(Icons.Rounded.Home, Icons.Rounded.History, Icons.Rounded.BarChart, Icons.Rounded.Settings)
        items.forEachIndexed { i, label ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(icons[i], label) },
                label = { Text(label) },
            )
        }
    }
}

private fun tabTitle(tab: Int, hindi: Boolean): String = when (tab) {
    0 -> "Phisherman"
    1 -> if (hindi) "इतिहास" else "History"
    2 -> if (hindi) "परिणाम" else "Results"
    else -> if (hindi) "सेटिंग्स" else "Settings"
}
