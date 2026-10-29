package com.phisherman

import android.app.Application
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import com.phisherman.sms.MessageNotificationListener
import com.phisherman.engine.ScamLlm
import com.phisherman.voice.CallGuard
import com.phisherman.voice.CallWatcher
import com.phisherman.sms.SmsAnalyser
import com.phisherman.sms.SmsNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhishermanApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        com.phisherman.ui.theme.AppSettings.load(this)  // light / dark / system and language, for every screen
        SmsNotifier.ensureChannel(this)
        // a listener enabled while the app was being updated can stay unbound; ask for a rebind
        if (MessageNotificationListener.isEnabled(this)) {
            NotificationListenerService.requestRebind(ComponentName(this, MessageNotificationListener::class.java))
        }
        CallWatcher.restoreCaptions(this)  // captions are only ever on during a call
        CallWatcher.start(this)
        // load the models early so the first link tap or SMS is fast
        scope.launch {
            SmsAnalyser.init(this@PhishermanApp)
            CallGuard.model(this@PhishermanApp)
            // the on-device LLM is optional: when its file is bundled and loads, it becomes the
            // second opinion for chats and the call guard
            if (ScamLlm.available(this@PhishermanApp) && ScamLlm.init(this@PhishermanApp)) CallGuard.judge = ScamLlm
        }
    }
}
