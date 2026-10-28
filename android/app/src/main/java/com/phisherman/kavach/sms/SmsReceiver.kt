package com.phisherman.kavach.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.phisherman.kavach.data.DecisionLog
import com.phisherman.kavach.engine.Engine
import com.phisherman.kavach.engine.Level
import com.phisherman.kavach.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming SMS, scores it with the shared engine and raises a notification for
 * non-safe verdicts (PS4 Section 8.5). This is an ADDITIONAL warning after arrival — it
 * does not and cannot block the message before the messaging app shows it (honest framing).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        // Join multipart bodies before scoring (edge case 12.2 "Multipart messages").
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (!Engine.initialised) Engine.init(context.applicationContext)
                val verdict = Engine.scoreSms(sender, body)
                val id = DecisionLog.record(context, verdict, sender)
                if (verdict.level != Level.SAFE) {
                    Notifier.showSms(context, sender, verdict, id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
