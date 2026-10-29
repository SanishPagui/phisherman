package com.phisherman

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phisherman.data.AppDatabase
import com.phisherman.data.Prefs
import com.phisherman.sms.ChatScreenReader
import com.phisherman.sms.MessageNotificationListener
import com.phisherman.ui.ActivityTab
import com.phisherman.ui.HomeTab
import com.phisherman.ui.ResultsContent
import com.phisherman.ui.SettingsTab
import com.phisherman.ui.loadResults
import com.phisherman.ui.theme.PhishermanTheme
import com.phisherman.ui.theme.tr
import com.phisherman.voice.CallGuardActivity
import com.phisherman.voice.CallWatcher

/** What is switched on; drives the protection list on the home screen. */
data class Protection(
    val links: Boolean = false,
    val sms: Boolean = false,
    val notifications: Boolean = false,
    val openChats: Boolean = false,
    val calls: Boolean = false,
    val battery: Boolean = true,
) {
    val onCount get() = listOf(links, sms, notifications, openChats, calls).count { it }
}

class MainActivity : ComponentActivity() {
    private var protection by mutableStateOf(Protection())

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val smsPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val recent = AppDatabase.get(this).decisions().recent(300)
        val results = loadResults(this)
        setContent {
            PhishermanTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val decisions by recent.collectAsState(initial = emptyList())
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { BottomBar(tab) { tab = it } },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        when (tab) {
                            0 -> HomeTab(
                                protection = protection,
                                actions = HomeActions(
                                    links = ::requestLinkHandler,
                                    sms = ::requestSmsProtection,
                                    notifications = ::openListenerSettings,
                                    openChats = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                    calls = { startActivity(Intent(this@MainActivity, CallGuardActivity::class.java)) },
                                    battery = ::requestBatteryExemption,
                                ),
                                decisions = decisions,
                                onOpenHistory = { tab = 1 },
                            )
                            1 -> ActivityTab(decisions)
                            2 -> ResultsContent(results)
                            else -> SettingsTab()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        protection = Protection(
            links = isLinkHandler(),
            sms = hasSmsProtection(),
            notifications = MessageNotificationListener.isEnabled(this),
            openChats = ChatScreenReader.isEnabled(this),
            calls = Prefs.autoCallGuard(this) && CallWatcher.canWatch(this),
            battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
        )
    }

    @SuppressLint("BatteryLife")  // a security app must keep checking messages in the background
    private fun requestBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openListenerSettings() {
        val component = ComponentName(this, MessageNotificationListener::class.java)
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else null
        try {
            startActivity(detail ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasSmsProtection(): Boolean =
        granted(Manifest.permission.RECEIVE_SMS) && (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))

    private fun requestSmsProtection() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        smsPermissions.launch(perms.toTypedArray())
    }

    private fun isLinkHandler(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) return rm.isRoleHeld(RoleManager.ROLE_BROWSER)
        }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        return packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    private fun requestLinkHandler() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                return
            }
        }
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }
}

class HomeActions(
    val links: () -> Unit, val sms: () -> Unit, val notifications: () -> Unit,
    val openChats: () -> Unit, val calls: () -> Unit, val battery: () -> Unit,
)

/** Floating pill navigation; the selected tab expands to show its label. */
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.Rounded.Home to tr("Home", "होम"),
        Icons.Rounded.History to tr("Activity", "गतिविधि"),
        Icons.Rounded.BarChart to tr("Results", "परिणाम"),
        Icons.Rounded.Settings to tr("Settings", "सेटिंग्स"),
    )
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(shape).background(cs.surface).border(1.dp, cs.outlineVariant, shape).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (icon, label) ->
                val on = selected == i
                val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, label = "navBg")
                val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, label = "navFg")
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(bg).clickable { onSelect(i) }
                        .animateContentSize().padding(horizontal = if (on) 16.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, label, tint = fg, modifier = Modifier.size(22.dp))
                    if (on) {
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}
