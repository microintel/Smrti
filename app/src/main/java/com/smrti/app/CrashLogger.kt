package com.smrti.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught crashes to a small file in app storage (nothing leaves the phone). Settings → About shows
 * the last report with a Share button, so a bug can be sent without a computer or Android Studio.
 */
object CrashLogger {
    private const val FILE = "crash.log"
    private const val MAX_BYTES = 48 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { write(app, thread, error) } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val file = File(context.filesDir, FILE)
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Throwable) { "?" }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "[$stamp] Smrti $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "thread: ${thread.name}\n$trace\n"
        var text = (if (file.exists()) file.readText() else "") + entry
        if (text.length > MAX_BYTES) text = text.takeLast(MAX_BYTES)
        file.writeText(text)
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
