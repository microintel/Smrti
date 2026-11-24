package com.smrti.app.data

import android.content.Context
import java.time.LocalDate

/** Scroll Guard's numbers for today (time in each app, warnings sent). Everything starts from 0 on a new day. */
object GuardStats {
    private fun sp(c: Context) = c.getSharedPreferences("smrti_guard_stats", Context.MODE_PRIVATE)

    /** Clears the numbers when the date has changed since they were last written. */
    @Synchronized
    private fun rollDay(c: Context) {
        val today = LocalDate.now().toString()
        val p = sp(c)
        if (p.getString("day", null) != today) p.edit().clear().putString("day", today).apply()
    }

    @Synchronized
    fun usageMs(c: Context, pkg: String): Long { rollDay(c); return sp(c).getLong("ms_$pkg", 0L) }

    @Synchronized
    fun warnings(c: Context, pkg: String): Int { rollDay(c); return sp(c).getInt("warn_$pkg", 0) }

    @Synchronized
    fun addUsage(c: Context, pkg: String, ms: Long) {
        if (ms <= 0) return
        rollDay(c)
        sp(c).edit().putLong("ms_$pkg", sp(c).getLong("ms_$pkg", 0L) + ms).apply()
    }

    @Synchronized
    fun addWarning(c: Context, pkg: String) {
        rollDay(c)
        sp(c).edit().putInt("warn_$pkg", sp(c).getInt("warn_$pkg", 0) + 1).apply()
    }
}
