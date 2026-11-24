package com.smrti.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.smrti.app.data.AppScope
import com.smrti.app.data.ImageStore
import com.smrti.app.data.Prefs
import com.smrti.app.data.QuoteImporter
import com.smrti.app.notify.Notifier
import com.smrti.app.notify.Scheduler
import kotlinx.coroutines.launch

class SmrtiApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        // must run before the quotes are seeded: it tells a first install from an update
        Prefs.initOnboarding(this)
        Notifier.createChannel(this)
        Scheduler.ensureScheduled(this)
        com.smrti.app.notify.ScrollGuardService.sync(this)
        AppScope.io.launch {
            QuoteImporter.seedIfNeeded(this@SmrtiApp)
            ImageStore.cleanUp(this@SmrtiApp)
        }
    }

    /** One shared image loader with a small, bounded cache (thumbnails are decoded at grid size, not full size). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(false)
        .build()
}
