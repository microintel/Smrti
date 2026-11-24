package com.smrti.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What the website's version.json says about the newest published build. */
data class UpdateInfo(val version: String, val date: String, val sha256: String, val notes: List<String>)

/**
 * In-app updates. The GitHub workflow publishes docs/downloads/version.json and Smrti-latest.apk on the
 * main branch; the app reads those two files straight from GitHub, so publishing a new tag is all it takes.
 */
object Updater {
    private const val BASE = "https://raw.githubusercontent.com/microintel/Smrti/main/docs/downloads/"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val percent: Int) : State
        data class Ready(val info: UpdateInfo) : State
        data object Failed : State
    }

    /** Shared by the banner on Home and the row in Settings. */
    var state by mutableStateOf<State>(State.Idle)
        private set

    /** Whether the person tapped "Later" for this version (until a newer one shows up). */
    var dismissedVersion by mutableStateOf<String?>(null)
        private set
    fun dismiss(v: String) { dismissedVersion = v }

    /** Checks GitHub at most every 6 hours unless [force]. Never throws. */
    suspend fun check(ctx: Context, force: Boolean = false) {
        val busy = state is State.Checking || state is State.Downloading || state is State.Ready
        if (busy) return
        val sp = ctx.getSharedPreferences("smrti", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong("update_checked_at", 0L) < CHECK_EVERY_MS && state !is State.Failed) return
        state = State.Checking
        state = try {
            val info = withContext(Dispatchers.IO) { fetchInfo() }
            sp.edit().putLong("update_checked_at", now).apply()
            if (isNewer(info.version, currentVersion(ctx))) State.Available(info) else State.UpToDate
        } catch (_: Throwable) {
            State.Failed
        }
    }

    /** Downloads the APK, checks its fingerprint, and opens Android's installer. */
    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        state = State.Downloading(info, 0)
        try {
            val file = withContext(Dispatchers.IO) { download(ctx, info) }
            state = State.Ready(info)
            install(ctx, file)
        } catch (_: Throwable) {
            state = State.Failed
        }
    }

    /** Opens the installer again for an APK that is already downloaded. */
    fun installAgain(ctx: Context) {
        val f = apkFile(ctx)
        if (f.exists()) try { install(ctx, f) } catch (_: Throwable) { state = State.Failed }
    }

    /** After returning from the installer: if this is the new version already, clean up. */
    fun onResume(ctx: Context) {
        val s = state
        if (s is State.Ready && !isNewer(s.info.version, currentVersion(ctx))) {
            apkFile(ctx).delete()
            state = State.UpToDate
        }
    }

    fun currentVersion(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null } ?: "0"

    // ---- internals --------------------------------------------------------------------------------

    private fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "Smrti-update.apk")

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }

    private fun fetchInfo(): UpdateInfo {
        val c = open(BASE + "version.json?t=" + System.currentTimeMillis())
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return parseInfo(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }

    private fun download(ctx: Context, info: UpdateInfo): File {
        val out = apkFile(ctx)
        out.delete()
        val c = open(BASE + "Smrti-latest.apk?t=" + System.currentTimeMillis())
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; state = State.Downloading(info, pct) }
                        }
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            // an empty sha256 in version.json (older publishes) means "don't verify"
            if (info.sha256.isNotBlank() && !hex.equals(info.sha256, ignoreCase = true)) {
                out.delete(); error("checksum mismatch")
            }
            return out
        } finally { c.disconnect() }
    }

    private fun install(ctx: Context, file: File) {
        // Android needs a one-time "install unknown apps" permission for Smrti
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun parseInfo(json: String): UpdateInfo {
    val o = JSONObject(json)
    val notes = o.optJSONArray("notes")
    return UpdateInfo(
        version = o.getString("version").trim().removePrefix("v"),
        date = o.optString("date"),
        sha256 = o.optString("sha256"),
        notes = (0 until (notes?.length() ?: 0)).map { notes!!.getString(it) }
    )
}

/** True when [remote] is a higher dotted version than [local] ("1.10" > "1.9", "1.2.1" > "1.2"). */
internal fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val r = parts(remote); val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
