package com.phisherman.sms

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.phisherman.ui.theme.I18n
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.phisherman.voice.CallGuard
import com.phisherman.voice.CallWatcher

/**
 * Reads the OPEN chat in messaging apps. When a chat is on screen, apps like WhatsApp do not post
 * a notification for new messages, so the notification listener never sees them. This service
 * looks only at the listed chat apps, takes the incoming bubbles (left half of the screen), and
 * sends each new one through the same SMS engine. Nothing is stored.
 */
class ChatScreenReader : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val pending = Runnable { scan() }
    /** chat + text -> most copies of that text seen on screen at once; more copies = a new message */
    private val seen = object : LinkedHashMap<Int, Int>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Int>?) = size > 1000
    }
    private var firstScanOf: String? = null

    private var banner: AlertBanner? = null

    override fun onServiceConnected() {
        Log.i(TAG, "connected")
        banner = AlertBanner(this)
        instance = this
    }

    override fun onDestroy() {
        banner?.hide()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Called for any risky message (open chat, notification or SMS) while this service runs. */
    fun showBanner(body: String, v: com.phisherman.engine.Verdict) { banner?.show(body, v) }

    private val pendingEmail = Runnable { scanEmail() }
    private val emailsDone = object : LinkedHashMap<Int, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>?) = size > 300
    }

    private var lastInstallerAlert = 0L

    /**
     * However an APK was disguised (fake PDF, renamed file, link download), installing it opens
     * Android's package installer. Warn at that moment, before the user taps Install.
     */
    private fun onInstallerOpened() {
        val now = System.currentTimeMillis()
        if (now - lastInstallerAlert < 30_000) return
        // the same installer also asks "uninstall this app?"; only an install prompt is a risk
        val texts = mutableListOf<Triple<Rect, String, String?>>()
        collect(rootInActiveWindow, texts)
        val screen = texts.joinToString(" ") { it.second }.lowercase()
        if ("uninstall" in screen || "install" !in screen) return
        lastInstallerAlert = now
        Log.i(TAG, "package installer opened")
        banner?.warn(I18n.t("⚠ Installing an app from outside the Play Store"),
            I18n.t("Scammers send fake bank, KYC and \"support\" apps as files on WhatsApp, SMS or email."),
            I18n.t("If someone sent you this file, tap Cancel. Banks never ask you to install apps this way."),
            com.phisherman.engine.Level.DANGEROUS, null)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString()
        if (pkg in INSTALLER_PKGS) {
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) onInstallerOpened()
            return
        }
        if (pkg == CAPTION_PKG || (e.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED && CallWatcher.inCall)) {
            if (CallWatcher.inCall) readCaptions()
            return
        }
        if (pkg in EMAIL_APPS) {
            main.removeCallbacks(pendingEmail)
            main.postDelayed(pendingEmail, 1200)  // the body (often a WebView) loads after the header
            return
        }
        if (pkg !in CHAT_APPS) return
        main.removeCallbacks(pending)
        main.postDelayed(pending, 700)  // the chat settles after typing / scrolling
    }

    override fun onInterrupt() {}

    private fun scan() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val app = CHAT_APPS[pkg] ?: return
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val all = mutableListOf<Triple<Rect, String, String?>>()
        collect(root, all)
        // WhatsApp marks message bubbles with an id. Other apps: only a real chat screen counts, one
        // with a message box ("Message…") at the bottom; feeds like Instagram Reels (captions,
        // usernames, "Follow") or comment sheets ("Add a comment…") are never read
        val bubbles = all.filter { it.third?.endsWith(":id/message_text") == true }
        if (pkg.startsWith("com.whatsapp") && bubbles.isEmpty()) return  // not inside a chat
        if (bubbles.isEmpty() && !hasMessageComposer(root)) return
        val texts = (if (bubbles.isNotEmpty()) bubbles else all).map { it.first to it.second }

        val sender = all.firstOrNull { it.third?.endsWith(":id/conversation_contact_name") == true }?.second
            ?: all.firstOrNull { it.first.top < height * 0.13 && it.second.length in 2..40 }?.second ?: "Open chat"
        val incoming = texts.filter { (r, t) ->
            r.top > height * 0.12 && r.bottom < height * 0.92 &&   // not the toolbar or the typing bar
                r.centerX() < width / 2 &&                           // left side = incoming bubble
                t.length >= 6 && !TIME_RE.matches(t) && t.trim().lowercase() !in UI_WORDS
        }.sortedBy { it.first.top }.map { it.second }

        // first look at a chat: only the newest few, so opening an old chat does not alert on history
        val chatKey = "$pkg|$sender"
        val first = firstScanOf != chatKey
        firstScanOf = chatKey
        // a scammer re-sending the same text is still a new message: compare how many copies are visible
        val counts = incoming.groupingBy { it }.eachCount()
        val newest = incoming.takeLast(3).toSet()
        for ((t, n) in counts) {
            val key = (chatKey + "\u0000" + t).hashCode()
            val before = seen[key] ?: 0
            if (n <= before) continue
            seen[key] = n
            if (first && t !in newest) continue  // opening a chat: do not alert on old history
            if (isDebuggable()) Log.d(TAG, "checking open-chat message in $app: \"${t.take(40)}\" (copy $n)")
            // the AI sees the last few incoming messages together, so it can follow the conversation
            val recent = incoming.takeLast(5).joinToString(" || ") { "Them: $it" }
            SmsHandler.handle(this, sender, t, null, app, recordSafe = false, forceNew = before > 0, context = recent)
        }
    }

    /**
     * An OPEN email (Gmail, Outlook, Samsung Email): sender + subject + body, checked once.
     * Only a fully opened email counts: exactly one subject on screen AND a Reply button. The
     * inbox list (one subject per row, no Reply button) is never read.
     */
    private fun scanEmail() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val app = EMAIL_APPS[pkg] ?: return
        val all = mutableListOf<Triple<Rect, String, String?>>()
        collect(root, all)
        val subjects = all.filter { it.third?.lowercase()?.contains("subject") == true }
        if (subjects.size != 1 || !hasReplyButton(root)) return  // inbox list or still loading
        val subjectNode = subjects.single()
        val subject = subjectNode.second.removeSuffix("Inbox").trim()
        val sender = all.firstOrNull { val id = it.third?.lowercase() ?: ""; "sender" in id || id.endsWith("from") }?.second ?: "Email"
        val body = all.filter { it.first.top >= subjectNode.first.bottom && it.second.length >= 3 && it.second !in EMAIL_CHROME }
            .sortedBy { it.first.top }.joinToString("\n") { it.second }.take(3000)
        if (body.length < 40) return  // body not loaded yet; the next event will retry
        val key = (pkg + "\u0000" + sender + "\u0000" + subject).hashCode()
        if (emailsDone.put(key, true) != null) return
        if (isDebuggable()) Log.d(TAG, "checking open email in $app: \"${subject.take(40)}\" (${body.length} chars)")
        SmsHandler.handle(this, sender, "$subject\n$body", null, app, recordSafe = false, context = "Subject: $subject || $body")
    }

    /**
     * During a call, Android gives other apps silence from the microphone, but the system Live
     * Caption feature can transcribe the call. Read its caption box and feed the call guard.
     */
    private fun readCaptions() {
        val box = windows.firstOrNull { it.root?.packageName?.toString() == CAPTION_PKG }?.root ?: return
        val parts = mutableListOf<Triple<Rect, String, String?>>()
        collect(box, parts)
        // only the caller's words: drop the box's own UI ("[Call started. Captions will appear
        // here.]", the language label, ellipses), which once made every call look like a scam
        val text = parts.sortedBy { it.first.top }.map { it.second }
            .filter { it.trim().lowercase() !in CAPTION_UI_LABELS }
            .joinToString(" ")
            .replace(CAPTION_BRACKETS, " ").replace("…", " ")
            .replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return
        if (isDebuggable()) Log.d(TAG, "caption: \"${text.takeLast(50)}\"")
        CallGuard.onCaption(this, text)
    }

    /** A chat screen has a text box for writing a message; feeds and comment sheets do not. */
    private fun hasMessageComposer(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        if (n.isEditable || n.className?.toString()?.endsWith("EditText") == true) {
            val hint = listOfNotNull(n.hintText, n.text, n.contentDescription).joinToString(" ").lowercase()
            if (COMPOSER_HINT.containsMatchIn(hint) && !COMMENT_HINT.containsMatchIn(hint)) return true
        }
        for (i in 0 until n.childCount) if (hasMessageComposer(n.getChild(i))) return true
        return false
    }

    /** An opened email shows a Reply button (text or icon description); the inbox list does not. */
    private fun hasReplyButton(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        val label = (n.contentDescription ?: n.text)?.toString()?.trim()?.lowercase()
        if (label != null && label in REPLY_LABELS) return true
        for (i in 0 until n.childCount) if (hasReplyButton(n.getChild(i))) return true
        return false
    }

    private fun isDebuggable() = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<Triple<Rect, String, String?>>) {
        if (n == null) return
        val text = n.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && !n.isEditable && n.className?.toString()?.endsWith("EditText") != true) {
            val r = Rect()
            n.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) out += Triple(r, text, n.viewIdResourceName)
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    companion object {
        private const val TAG = "ChatScreenReader"
        @Volatile var instance: ChatScreenReader? = null
            private set

        /** Android System Intelligence hosts Live Caption */
        const val CAPTION_PKG = "com.google.android.as"
        /** Live Caption's own messages are in square brackets, e.g. "[Call started. Captions will appear here.]" */
        private val CAPTION_BRACKETS = Regex("\\[[^\\]]*\\]")
        private val CAPTION_UI_LABELS = setOf("english", "hindi", "हिंदी", "live caption", "captions", "caption", "…", "...")
        private val TIME_RE = Regex("""(?i)\d{1,2}:\d{2}\s*(am|pm)?|today|yesterday|online|typing…?|last seen.*""")

        /** Chat apps whose open conversations are checked (same names as the notification listener). */
        val CHAT_APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.messaging" to "Messages",
            "com.samsung.android.messaging" to "Samsung Messages",
            "com.facebook.orca" to "Messenger",
        )

        /** Email apps whose OPEN email is checked. */
        val EMAIL_APPS = mapOf(
            "com.google.android.gm" to "Gmail",
            "com.microsoft.office.outlook" to "Outlook",
            "com.samsung.android.email.provider" to "Samsung Email",
        )

        /** Android's app installer (Google's on most phones, including Samsung). */
        private val INSTALLER_PKGS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.samsung.android.packageinstaller")
        private val REPLY_LABELS = setOf("reply", "reply all", "उत्तर दें", "जवाब दें")
        private val COMPOSER_HINT = Regex("message|text|chat|type|write|rcs|sms|संदेश|मैसेज")
        private val COMMENT_HINT = Regex("comment|reply to|search|caption")
        /** Buttons and labels that are never a message ("Follow", "Like"…). */
        private val UI_WORDS = setOf("follow", "following", "message", "like", "share", "send", "reply", "view profile", "see translation")

        /** Buttons and labels in email screens that are not part of the message. */
        private val EMAIL_CHROME = setOf("Reply", "Reply all", "Forward", "Inbox", "Show quoted text", "Details", "to me", "Translate", "Unsubscribe")

        fun isEnabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.startsWith("${ctx.packageName}/") && it.endsWith("ChatScreenReader") }
        }
    }
}
