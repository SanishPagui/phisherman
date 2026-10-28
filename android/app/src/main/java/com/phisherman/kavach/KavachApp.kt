package com.phisherman.kavach

import android.app.Application
import com.phisherman.kavach.engine.Engine
import com.phisherman.kavach.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KavachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        // Load models and assets off the main thread so first launch stays snappy.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            Engine.init(applicationContext)
        }
    }
}
