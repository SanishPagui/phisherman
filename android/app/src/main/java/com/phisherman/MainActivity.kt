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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.phisherman.data.AppDatabase
import com.phisherman.data.Decision
import com.phisherman.data.DecisionLog
import com.phisherman.data.Prefs
import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.link.Forwarder
import com.phisherman.link.LinkAnalyser
import com.phisherman.sms.ChatScreenReader
import com.phisherman.sms.MessageNotificationListener
import com.phisherman.sms.SmsAnalyser
import com.phisherman.ui.MetricsActivity
import com.phisherman.ui.WarningActivity
import com.phisherman.ui.theme.Brand
import com.phisherman.ui.theme.PhishermanTheme
import com.phisherman.voice.CallGuardActivity
import com.phisherman.voice.CallWatcher
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
        val recent = AppDatabase.get(this).decisions().recent(10)
        setContent {
            PhishermanTheme {
                val decisions by recent.collectAsState(initial = emptyList())
                HomeScreen(
                    protection = protection,
                    actions = HomeActions(
                        links = { on -> toggle(Prefs.Feature.LINKS, on, isLinkHandler(), ::requestLinkHandler) },
                        sms = { on -> toggle(Prefs.Feature.SMS, on, hasSmsProtection(), ::requestSmsProtection) },
                        notifications = { on -> toggle(Prefs.Feature.NOTIFICATIONS, on, MessageNotificationListener.isEnabled(this), ::openListenerSettings) },
                        openChats = { on -> toggle(Prefs.Feature.OPEN_CHATS, on, ChatScreenReader.isEnabled(this)) {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
                        calls = ::toggleCalls,
                        battery = ::requestBatteryExemption,
                    ),
                    decisions = decisions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /**
     * A protection is on when Android allows it AND the user's switch is on. Switching off only
     * pauses it inside Phisherman (permissions stay); switching on asks for the permission if needed.
     */
    private fun toggle(f: Prefs.Feature, on: Boolean, granted: Boolean, grant: () -> Unit) {
        Prefs.setEnabled(this, f, on)
        if (on && !granted) grant()
        refresh()
    }

    private fun toggleCalls(on: Boolean) {
        when {
            !on -> { Prefs.setAutoCallGuard(this, false); CallWatcher.stop(this) }
            CallWatcher.canWatch(this) -> { Prefs.setAutoCallGuard(this, true); CallWatcher.start(this) }
            else -> startActivity(Intent(this, CallGuardActivity::class.java))  // asks for call permissions there
        }
        refresh()
    }

    private fun refresh() {
        fun on(f: Prefs.Feature, granted: Boolean) = granted && Prefs.enabled(this, f)
        protection = Protection(
            links = on(Prefs.Feature.LINKS, isLinkHandler()),
            sms = on(Prefs.Feature.SMS, hasSmsProtection()),
            notifications = on(Prefs.Feature.NOTIFICATIONS, MessageNotificationListener.isEnabled(this)),
            openChats = on(Prefs.Feature.OPEN_CHATS, ChatScreenReader.isEnabled(this)),
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
    val links: (Boolean) -> Unit, val sms: (Boolean) -> Unit, val notifications: (Boolean) -> Unit,
    val openChats: (Boolean) -> Unit, val calls: (Boolean) -> Unit, val battery: () -> Unit,
)

@Composable
private fun HomeScreen(protection: Protection, actions: HomeActions, decisions: List<Decision>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(protection)

        // PS4 10.2: a warning in the last 15 minutes gets a reminder, in case the user went ahead anyway
        decisions.firstOrNull { it.level != "SAFE" && System.currentTimeMillis() - it.ts < 15 * 60_000 }?.let { d ->
            val mins = ((System.currentTimeMillis() - d.ts) / 60_000).coerceAtLeast(1)
            val what = if (d.channel == "VOICE") "During a ${d.subject.takeIf { it != "call" } ?: "call"}" else "${channelLabel(d.channel)}: ${d.subject}"
            Notice(Brand.Danger, "You were warned $mins min ago",
                "$what. If you entered details, shared an OTP or paid, call your bank right away.")
        }
        LinkAnalyser.loadError?.let { Notice(Brand.Danger, "Detection engine failed to load", it) }
        if ((protection.sms || protection.notifications) && !protection.battery) {
            Notice(Brand.Warn, "Phone may pause Phisherman", "Allow it to run in the background so new messages are always checked.",
                action = "Allow" to actions.battery)
        }

        SectionTitle("Protection")
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column {
                ProtectionRow("🔗", "Links", "Checked before they open", protection.links, actions.links)
                RowDivider()
                ProtectionRow("✉️", "SMS", "Checked as they arrive", protection.sms, actions.sms)
                RowDivider()
                ProtectionRow("🔔", "App notifications", "WhatsApp, RCS, Instagram, Gmail…", protection.notifications, actions.notifications)
                RowDivider()
                ProtectionRow("💬", "Open chats & emails", "Messages on screen in chat and mail apps", protection.openChats, actions.openChats)
                RowDivider()
                ProtectionRow("📞", "Calls", "Phone & WhatsApp calls, via Live Caption", protection.calls, actions.calls)
            }
        }

        SectionTitle("Check something")
        QuickCheck()

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("📊", "Results", "Accuracy & speed", Modifier.weight(1f)) {
                ctx.startActivity(Intent(ctx, MetricsActivity::class.java))
            }
            ActionTile("🛡️", "Call guard", "Demo & settings", Modifier.weight(1f)) {
                ctx.startActivity(Intent(ctx, CallGuardActivity::class.java))
            }
        }

        SectionTitle("Recent checks")
        RecentChecks(decisions)

        SettingsSection()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(p: Protection) {
    val all = p.onCount == 5
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(if (all) Brand.Safe else Brand.Navy), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_shield), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(Color.White))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Phisherman", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (all) "Fully protected · everything runs on this phone" else "${p.onCount} of 5 protections on",
                color = if (all) Brand.Safe else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.surfaceVariant)

@Composable
private fun ProtectionRow(icon: String, title: String, subtitle: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!on) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Brand.Safe))
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun Notice(color: Color, title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(body, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
            }
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) } }
        }
    }
}

@Composable
private fun ActionTile(icon: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        }
    }
}

/** Paste a link or a whole message; the same engines as everything else. */
@Composable
private fun QuickCheck() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<String, Verdict?>?>(null) }
    var checking by remember { mutableStateOf(false) }
    // looks inside the file, so an app renamed to .pdf / .jpg is still caught
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ctx.startActivity(Intent(ctx, com.phisherman.ui.FileCheckActivity::class.java)
            .setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(input, { input = it; result = null }, Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a link or a message") }, minLines = 1, maxLines = 5, shape = RoundedCornerShape(14.dp))
            Button(
                enabled = input.isNotBlank() && !checking, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                onClick = {
                    checking = true
                    scope.launch {
                        val text = input.trim()
                        // one token with a dot = a link; anything else is checked as a message
                        val isLink = text.none { it.isWhitespace() } && '.' in text
                        val v = if (isLink) LinkAnalyser.analyse(ctx, text) else SmsAnalyser.analyse(ctx, null, text)
                        checking = false
                        when {
                            v == null -> {}
                            v.level == Level.SAFE -> DecisionLog.recordAsync(ctx, v)
                            isLink -> ctx.startActivity(WarningActivity.intent(ctx, text, v))
                            else -> {
                                DecisionLog.recordAsync(ctx, v)
                                ctx.startActivity(WarningActivity.smsIntent(ctx, text, v))
                            }
                        }
                        result = (if (isLink) text else "") to v
                    }
                },
            ) { Text(if (checking) "Checking…" else "Check") }
            androidx.compose.material3.OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Check a file (hidden apps / APKs)") }
            result?.let { (url, v) ->
                when {
                    v == null -> Text("That is not a web link (http or https).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    v.level == Level.SAFE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip("Looks safe", Brand.Safe)
                        Spacer(Modifier.width(8.dp))
                        Text("risk ${v.score}/100 · ${v.latencyMs} ms", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (url.isNotEmpty()) TextButton(onClick = { Forwarder.openInRealBrowser(ctx, url) }) { Text("Open") }
                    }
                    else -> Chip("${levelLabel(v.level.name)} · risk ${v.score}/100", levelColor(v.level.name))
                }
            }
        }
    }
}

@Composable
private fun RecentChecks(decisions: List<Decision>) {
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (decisions.isEmpty()) {
                Text("Nothing checked yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            decisions.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(levelColor(d.level)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.subject, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text("${channelLabel(d.channel)} · ${fmt.format(Date(d.ts))}${d.userAction?.let { " · ${actionLabel(it)}" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(levelLabel(d.level), color = levelColor(d.level), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val browsers = remember { Forwarder.browsers(ctx) }
    var browser by remember { mutableStateOf(Forwarder.targetBrowser(ctx)) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Hide settings" else "Settings") }
    if (!open) return
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Safe links open in", fontWeight = FontWeight.SemiBold)
            if (browsers.isEmpty()) Text("No other browser installed. Install Chrome so safe links can open.")
            browsers.forEach { b ->
                Row(Modifier.fillMaxWidth().clickable { browser = b.packageName; Prefs.setChosenBrowser(ctx, b.packageName) },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = browser == b.packageName, onClick = { browser = b.packageName; Prefs.setChosenBrowser(ctx, b.packageName) })
                    Text(b.label)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { scope.launch { AppDatabase.get(ctx).decisions().clear() } }) { Text("Clear check history") }
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}. Everything is checked on this phone; nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun levelColor(level: String) = when (level) { "DANGEROUS" -> Brand.Danger; "SUSPICIOUS" -> Brand.Warn; else -> Brand.Safe }
private fun levelLabel(level: String) = when (level) { "DANGEROUS" -> "Dangerous"; "SUSPICIOUS" -> "Suspicious"; else -> "Safe" }
private fun channelLabel(c: String) = when (c) { "LINK" -> "Link"; "SMS" -> "Message"; "VOICE" -> "Call"; "FILE" -> "File"; else -> c }
private fun actionLabel(a: String) = when (a) { "BACK" -> "went back"; "OPEN_ANYWAY" -> "opened anyway"; "FORWARDED" -> "opened"; else -> a.lowercase() }
