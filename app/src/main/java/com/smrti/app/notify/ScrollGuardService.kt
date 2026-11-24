package com.smrti.app.notify

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.smrti.app.MainActivity
import com.smrti.app.R
import com.smrti.app.data.GuardStats
import com.smrti.app.data.Prefs
import com.smrti.app.data.formatInterval

/** An app Scroll Guard can watch. */
data class GuardApp(val pkg: String, val label: String)

/**
 * Scroll Guard: starts a countdown when a chosen app (X, Instagram, YouTube) comes to the front,
 * resets it when the app is left or the screen turns off, and sends a notification with a
 * "Close" button when the time is nearly (or fully) used up.
 * It uses Android's "Usage access", which only tells which app is in front, never what is on the screen.
 */
class ScrollGuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var front: String? = null          // app currently in front (any app)
    private var lastEventTime = 0L
    private var sessionPkg: String? = null      // watched app being counted
    private var sessionStart = 0L
    private var warned = false
    private var reached = false
    private var lastTick = 0L
    private var pendingMs = 0L
    private var popup: View? = null

    private val tick = object : Runnable {
        override fun run() {
            try { poll() } catch (_: Throwable) { /* never crash the app over a missed tick */ }
            handler.postDelayed(this, 1000)
        }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = endSession()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ongoing = NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_smrti)
            .setContentTitle(getString(R.string.guard_running))
            .setContentText(getString(R.string.guard_running_sub))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        try {
            ServiceCompat.startForeground(
                this, RUNNING_ID, ongoing,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (_: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        // look back a few hours once, to learn which app is in front right now
        lastEventTime = System.currentTimeMillis() - 6 * 60 * 60 * 1000L
        lastTick = SystemClock.elapsedRealtime()
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(screenOff) }
        endSession()
        flushUsage()
        super.onDestroy()
    }

    private fun poll() {
        if (!Prefs.guardEnabled(this) || !hasUsageAccess(this)) { endSession(); stopSelf(); return }
        readFrontApp()
        val nowTick = SystemClock.elapsedRealtime()
        // a delayed tick (phone asleep) must not count as time spent in the app
        val delta = (nowTick - lastTick).coerceIn(0L, 3000L)
        lastTick = nowTick
        val pkg = front
        val watching = pkg != null && pkg in Prefs.guardApps(this) && !Notifier.isDeviceLocked(this)
        if (!watching) { endSession(); return }
        if (pkg != sessionPkg) startSession(pkg!!)
        pendingMs += delta
        if (pendingMs >= 3000L) flushUsage()

        val elapsed = SystemClock.elapsedRealtime() - sessionStart
        val limitMs = Prefs.guardLimitMinutes(this) * 60_000L
        val warnMin = Prefs.guardWarnMinutes(this)
        if (!reached && elapsed >= limitMs) {
            reached = true
            notifyGuard(pkg!!, limitReached = true)
        } else if (!warned && !reached && warnMin > 0 && limitMs - warnMin * 60_000L > 0 &&
            elapsed >= limitMs - warnMin * 60_000L
        ) {
            warned = true
            notifyGuard(pkg!!, limitReached = false)
        }
    }

    /** Reads the app-switch events since the last look; the latest "came to front" one is the app in front. */
    private fun readFrontApp() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastEventTime, now) ?: return
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) front = e.packageName
        }
        lastEventTime = now
    }

    private fun startSession(pkg: String) {
        endSession()
        sessionPkg = pkg
        sessionStart = SystemClock.elapsedRealtime()
    }

    /** The app was left (or the screen went off): reset the countdown and clear the notification. */
    private fun endSession() {
        flushUsage()
        dismissPopup()
        if (sessionPkg != null) NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        sessionPkg = null
        warned = false
        reached = false
    }

    /** Saves the time counted so far to today's total. */
    private fun flushUsage() {
        val pkg = sessionPkg
        if (pkg != null && pendingMs > 0) GuardStats.addUsage(this, pkg, pendingMs)
        pendingMs = 0L
    }

    private fun dismissPopup() {
        popup?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        popup = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** A pop-up card over the app: the message, today's time, and Close / Keep going buttons. */
    private fun showPopup(pkg: String, label: String, message: String) {
        if (!canShowPopup(this) || popup != null) return
        flushUsage()
        val used = GuardStats.usageMs(this, pkg)
        val usedText = if (used < 60_000L) getString(R.string.guard_sec_short, (used / 1000).toInt())
            else formatInterval(this, (used / 60_000L).toInt())

        val gold = Color.parseColor("#FAB62F")
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            background = GradientDrawable().apply { setColor(Color.parseColor("#1B1B1E")); cornerRadius = dp(24).toFloat() }
        }
        fun text(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            this.text = t; textSize = sp; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        card.addView(text(getString(R.string.guard_notif_up_title, label), 20f, Color.parseColor("#F2F0EA"), true))
        card.addView(text(message, 16f, Color.parseColor("#F2F0EA")).apply { setPadding(0, dp(12), 0, 0) })
        card.addView(text(getString(R.string.guard_popup_used, label, usedText), 14f, Color.parseColor("#A7A7AE")).apply { setPadding(0, dp(12), 0, dp(20)) })
        card.addView(Button(this).apply {
            text = getString(R.string.guard_close_action, label)
            setTextColor(Color.parseColor("#041237"))
            background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(14).toFloat() }
            isAllCaps = false
            setOnClickListener { dismissPopup(); goHome() }
        })
        card.addView(Button(this).apply {
            text = getString(R.string.guard_popup_keep)
            setTextColor(gold)
            background = null
            isAllCaps = false
            setOnClickListener { dismissPopup() }
        })

        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { dismissPopup(); return true }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT
        )
        try {
            getSystemService(WindowManager::class.java).addView(root, lp)
            popup = root
        } catch (_: Throwable) {
            // not allowed right now: the notification still shows
        }
    }

    private fun goHome() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) { }
    }

    private fun notifyGuard(pkg: String, limitReached: Boolean) {
        val label = APPS.firstOrNull { it.pkg == pkg }?.label ?: pkg
        GuardStats.addWarning(this, pkg)
        val title: String
        val text: String
        if (limitReached) {
            title = getString(R.string.guard_notif_up_title, label)
            text = Prefs.guardMessage(this).ifBlank { getString(R.string.guard_default_message) }
        } else {
            title = getString(R.string.guard_notif_near_title, label)
            text = getString(R.string.guard_notif_near_text, Prefs.guardWarnMinutes(this))
        }
        if (limitReached) showPopup(pkg, label, text)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // "Close" sends the phone to the home screen, which takes the app off the screen
        val home = PendingIntent.getActivity(
            this, 1,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_smrti)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_smrti, getString(R.string.guard_close_action, label), home)
            .build()
        try {
            if (Notifier.canNotify(this)) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // notifications switched off: nothing to show
        }
    }

    companion object {
        private const val CHANNEL_ALERT = "smrti_scroll_guard"
        private const val CHANNEL_RUNNING = "smrti_scroll_guard_running"
        const val NOTIFICATION_ID = 1002
        private const val RUNNING_ID = 1003

        val APPS = listOf(
            GuardApp("com.twitter.android", "X"),
            GuardApp("com.instagram.android", "Instagram"),
            GuardApp("com.google.android.youtube", "YouTube")
        )

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, context.getString(R.string.guard_title), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.guard_channel_desc)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.guard_running), NotificationManager.IMPORTANCE_MIN).apply {
                    description = context.getString(R.string.guard_running_sub)
                }
            )
        }

        /** True when "Display over other apps" is allowed, so the pop-up can appear on top of the app. */
        fun canShowPopup(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** True when the person has allowed "Usage access" for Smrti in the phone's settings. */
        fun hasUsageAccess(context: Context): Boolean = try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) { false }

        /** Starts the watcher when Scroll Guard is on and allowed, stops it otherwise. Safe to call any time. */
        fun sync(context: Context) {
            val app = context.applicationContext
            try {
                val intent = Intent(app, ScrollGuardService::class.java)
                if (Prefs.guardEnabled(app) && hasUsageAccess(app)) ContextCompat.startForegroundService(app, intent)
                else app.stopService(intent)
            } catch (_: Throwable) {
                // Android may refuse to start it from the background; it starts next time the app opens
            }
        }
    }
}
