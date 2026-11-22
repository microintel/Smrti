package com.smrti.app

import android.app.Application
import com.smrti.app.data.QuoteImporter
import com.smrti.app.notify.Notifier
import com.smrti.app.notify.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmrtiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        Scheduler.ensureScheduled(this)
        CoroutineScope(Dispatchers.IO).launch { QuoteImporter.seedIfNeeded(this@SmrtiApp) }
    }
}
