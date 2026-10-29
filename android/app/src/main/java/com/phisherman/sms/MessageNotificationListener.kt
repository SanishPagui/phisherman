package com.phisherman.sms

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.phisherman.voice.CallWatcher

/**
 * Checks the text of new notifications from every app: RCS chats in Google / Samsung Messages,
 * WhatsApp, Instagram, Telegram, Gmail, and any other app a scam can arrive through. Text is
 * scored on the device and never stored; system, ongoing and progress notifications are skipped.
 */
class MessageNotificationListener : NotificationListenerService() {
    private val labels = HashMap<String, String>()

    override fun onListenerConnected() {
        Log.i(TAG, "connected")
    }

    /** Samsung and others unbind listeners of sleeping apps; ask to be bound again right away. */
    override fun onListenerDisconnected() {
        Log.w(TAG, "disconnected, requesting rebind")
        requestRebind(ComponentName(this, MessageNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in SYSTEM_PACKAGES) return
        if (!com.phisherman.data.Prefs.enabled(this, com.phisherman.data.Prefs.Feature.NOTIFICATIONS)) return
        if (pkg == "com.android.shell" && !isDebuggable()) return  // adb test posts, debug builds only
        val n = sbn.notification ?: return
        if (n.category == Notification.CATEGORY_CALL) { voipCall(sbn); return }
        if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        if (n.category in SKIP_CATEGORIES) return
        if (n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return
        val msg = try { latestMessage(n.extras) } catch (e: Exception) { Log.w(TAG, "could not read", e); null } ?: return
        val (sender, body) = msg
        if (body.trim().length < 3) return
        val app = MESSAGING_APPS[pkg] ?: label(pkg)
        // a person's message: a chat bubble (MessagingStyle), an SMS app, or an email; anything
        // else is the app's own notification and is judged only on hard evidence
        val fromPerson = hasMessages(n.extras) || pkg in SMS_APPS ||
            n.category == Notification.CATEGORY_MESSAGE || n.category == Notification.CATEGORY_EMAIL
        Log.d(TAG, "checking $app (${body.length} chars, ${if (fromPerson) "message" else "app notice"})")
        SmsHandler.handle(this, sender, body, null, app, recordSafe = fromPerson, appNotice = !fromPerson)
    }

    /**
     * Internet calls (WhatsApp, Telegram, ...) have no phone-network call state; their ongoing
     * call notification is one of the two signals CallWatcher uses (the other is the audio mode).
     */
    private val callKeys = HashMap<String, String>()

    private fun voipCall(sbn: StatusBarNotification) {
        val app = VOIP_APPS[sbn.packageName] ?: return
        if (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return
        callKeys[sbn.key] = app
        CallWatcher.onVoipNotification(this, app)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (callKeys.remove(sbn.key) != null) CallWatcher.onVoipNotification(this, callKeys.values.firstOrNull())
    }

    private fun hasMessages(x: Bundle): Boolean = try {
        val msgs = if (Build.VERSION.SDK_INT >= 33) x.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
        else @Suppress("DEPRECATION") x.getParcelableArray(Notification.EXTRA_MESSAGES)
        !msgs.isNullOrEmpty()
    } catch (e: Exception) { false }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /** Newest message in a MessagingStyle notification, else title + (big) text. */
    private fun latestMessage(x: Bundle): Pair<String, String>? {
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val msgs = if (Build.VERSION.SDK_INT >= 33) {
            x.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION") x.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        val last = msgs?.lastOrNull() as? Bundle
        if (last != null) {
            val text = last.getCharSequence("text")?.toString() ?: return null
            val sender = last.getCharSequence("sender")?.toString()
                ?: (if (Build.VERSION.SDK_INT >= 28) last.getParcelable("sender_person", android.app.Person::class.java)?.name?.toString() else null)
                ?: title
            return sender to text
        }
        val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            ?: return null
        return title to text
    }

    private fun isDebuggable() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        private const val TAG = "MsgListener"

        /** Friendly names for the main chat apps; any other app is labelled by its own name. */
        val MESSAGING_APPS = mapOf(
            "com.google.android.apps.messaging" to "Messages",
            "com.samsung.android.messaging" to "Samsung Messages",
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "com.instagram.android" to "Instagram",
            "com.instagram.barcelona" to "Threads",
            "com.facebook.orca" to "Messenger",
            "com.google.android.gm" to "Gmail",
            "com.microsoft.office.outlook" to "Outlook",
            "com.snapchat.android" to "Snapchat",
            "com.discord" to "Discord",
            "com.linkedin.android" to "LinkedIn",
            "com.truecaller" to "Truecaller",
        )
        /** Apps whose internet calls the call guard follows. */
        val VOIP_APPS = mapOf(
            "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "org.telegram.messenger" to "Telegram",
            "org.thoughtcrime.securesms" to "Signal", "com.facebook.orca" to "Messenger", "com.instagram.android" to "Instagram",
            "com.google.android.apps.tachyon" to "Google Meet", "com.skype.raider" to "Skype", "com.truecaller" to "Truecaller",
        )
        private val SMS_APPS = setOf("com.google.android.apps.messaging", "com.samsung.android.messaging")
        private val SYSTEM_PACKAGES = setOf("android", "com.android.systemui", "com.samsung.android.app.smartcapture")
        private val SKIP_CATEGORIES = setOf(
            Notification.CATEGORY_PROGRESS, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM, Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_LOCATION_SHARING,
        )

        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, MessageNotificationListener::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            return enabled.split(':').any { it == me }
        }
    }
}
