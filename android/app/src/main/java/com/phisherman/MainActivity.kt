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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.phisherman.sms.MessageNotificationListener
import com.phisherman.ui.AppResults
import com.phisherman.ui.HistoryTab
import com.phisherman.ui.HomeTab
import com.phisherman.ui.ResultsTab
import com.phisherman.ui.SettingsTab
import com.phisherman.ui.Shield
import com.phisherman.ui.ShieldIcons
import com.phisherman.ui.theme.PhishermanTheme
import com.phisherman.ui.theme.tr
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var protectionOn by mutableStateOf(false)
    private var smsOn by mutableStateOf(false)
    private var chatOn by mutableStateOf(false)
    private var batteryOk by mutableStateOf(true)
    private var overlayOn by mutableStateOf(false)

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        protectionOn = isLinkHandler()
    }
    private val smsPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsOn = hasSmsProtection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = AppDatabase.get(this).decisions()
        val recent = db.recent(500)
        val results = AppResults.load(this)
        setContent {
            var hindi by remember { mutableStateOf(Prefs.hindi(this)) }
            var tab by remember { mutableIntStateOf(0) }
            val decisions by recent.collectAsState(initial = emptyList())
            val voice = remember { AppResults.voice(this) }
            val scope = rememberCoroutineScope()
            val clear: () -> Unit = { scope.launch { db.clear() } }
            fun setHindi(on: Boolean) { hindi = on; Prefs.setHindi(this, on) }

            PhishermanTheme(hindi = hindi) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { BottomBar(tab) { tab = it } },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        when (tab) {
                            0 -> HomeTab(
                                shields = shields(),
                                flagged = decisions.count { it.level != "SAFE" },
                                checks = decisions.size,
                                batteryOk = batteryOk,
                                onFixBattery = ::requestBatteryExemption,
                                onToggleLanguage = { setHindi(!hindi) },
                            )
                            1 -> HistoryTab(decisions, onClear = clear)
                            2 -> ResultsTab(results, voice)
                            else -> SettingsTab(hindi, ::setHindi, onClearHistory = clear)
                        }
                    }
                }
            }
        }
    }

    /** The protections, in the order a scam reaches the user: link, SMS, chat / email, pop-up. */
    @Composable
    private fun shields() = listOf(
        Shield(ShieldIcons.link, tr("Link protection", "लिंक सुरक्षा"),
            tr("Links you tap in any app are checked before they open.", "किसी भी ऐप में टैप किए लिंक खुलने से पहले जाँचे जाते हैं।"),
            tr("Set Phisherman as your browser so risky links are stopped first.", "Phisherman को ब्राउज़र बनाएँ ताकि ख़तरनाक लिंक पहले रुकें।"),
            protectionOn, tr("Turn on", "चालू करें"), ::requestLinkHandler),
        Shield(ShieldIcons.sms, tr("SMS protection", "SMS सुरक्षा"),
            tr("New SMS are checked the moment they arrive.", "नए SMS आते ही जाँचे जाते हैं।"),
            tr("Allow Phisherman to see incoming SMS.", "आने वाले SMS देखने की अनुमति दें।"),
            smsOn, tr("Turn on", "चालू करें"), ::requestSmsProtection),
        Shield(ShieldIcons.chat, tr("Chats & email", "चैट और ईमेल"),
            tr("WhatsApp, RCS, Instagram, Gmail and more are checked.", "WhatsApp, RCS, Instagram, Gmail आदि जाँचे जाते हैं।"),
            tr("Allow notification access to check chats and emails.", "चैट और ईमेल जाँचने के लिए नोटिफ़िकेशन एक्सेस दें।"),
            chatOn, tr("Turn on", "चालू करें"), ::openListenerSettings),
        Shield(ShieldIcons.popup, tr("Full-screen alerts", "पूर्ण-स्क्रीन चेतावनी"),
            tr("A scam message instantly covers the screen in red.", "स्कैम संदेश तुरंत पूरी स्क्रीन पर लाल चेतावनी दिखाता है।"),
            tr("Allow \"Display over other apps\" for full-screen warnings.", "पूर्ण-स्क्रीन चेतावनी के लिए \"दूसरे ऐप्स के ऊपर दिखाएँ\" की अनुमति दें।"),
            overlayOn, tr("Allow", "अनुमति"), ::requestOverlay),
    )

    override fun onResume() {
        super.onResume()
        protectionOn = isLinkHandler()
        smsOn = hasSmsProtection()
        chatOn = MessageNotificationListener.isEnabled(this)
        batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        overlayOn = Settings.canDrawOverlays(this)
    }

    /** "Display over other apps" lets a scam-message warning take over the screen by itself. */
    private fun requestOverlay() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
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

/** Floating pill navigation; the selected tab expands to show its label. */
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.Rounded.Home to tr("Home", "होम"),
        Icons.Rounded.History to tr("History", "इतिहास"),
        Icons.Rounded.BarChart to tr("Results", "परिणाम"),
        Icons.Rounded.Settings to tr("Settings", "सेटिंग्स"),
    )
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (icon, label) ->
                val on = selected == i
                val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, label = "navBg")
                val fg by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "navFg")
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
