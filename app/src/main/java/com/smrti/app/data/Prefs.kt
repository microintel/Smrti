package com.smrti.app.data

import android.content.Context

object Prefs {
    const val MIN_INTERVAL = 1
    const val MAX_INTERVAL = 7 * 24 * 60 // one week, in minutes

    private fun sp(c: Context) = c.getSharedPreferences("smrti", Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun intervalMinutes(c: Context) = sp(c).getInt("interval_min", 60)
    fun setIntervalMinutes(c: Context, v: Int) =
        sp(c).edit().putInt("interval_min", v.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply()

    fun nextAt(c: Context) = sp(c).getLong("next_at", 0L)
    fun setNextAt(c: Context, v: Long) = sp(c).edit().putLong("next_at", v).apply()

    fun useImages(c: Context) = sp(c).getBoolean("use_images", true)
    fun setUseImages(c: Context, v: Boolean) = sp(c).edit().putBoolean("use_images", v).apply()

    fun largeImage(c: Context) = sp(c).getBoolean("large_image", true)
    fun setLargeImage(c: Context, v: Boolean) = sp(c).edit().putBoolean("large_image", v).apply()

    // Which image comes next: "random" (default) or "series" (one after another, looping)
    const val ORDER_RANDOM = "random"
    const val ORDER_SERIES = "series"
    fun imageOrder(c: Context) = sp(c).getString("image_order", ORDER_RANDOM) ?: ORDER_RANDOM
    fun setImageOrder(c: Context, v: String) = sp(c).edit().putString("image_order", v).apply()
    fun seriesIndex(c: Context) = sp(c).getInt("series_index", 0)
    fun setSeriesIndex(c: Context, v: Int) = sp(c).edit().putInt("series_index", v).apply()

    // Device photo albums (MediaStore bucket ids) used as extra image sources
    fun albumIds(c: Context): Set<String> =
        sp(c).getStringSet("album_ids", emptySet())?.toSet() ?: emptySet()
    fun setAlbumIds(c: Context, v: Set<String>) = sp(c).edit().putStringSet("album_ids", v).apply()

    // Quiet hours, stored as minutes after midnight
    fun quietOn(c: Context) = sp(c).getBoolean("quiet_on", false)
    fun setQuietOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("quiet_on", v).apply()
    fun quietStart(c: Context) = sp(c).getInt("quiet_start", 22 * 60)
    fun setQuietStart(c: Context, v: Int) = sp(c).edit().putInt("quiet_start", v).apply()
    fun quietEnd(c: Context) = sp(c).getInt("quiet_end", 7 * 60)
    fun setQuietEnd(c: Context, v: Int) = sp(c).edit().putInt("quiet_end", v).apply()

    fun disabledCategories(c: Context): Set<String> =
        sp(c).getStringSet("disabled_categories", emptySet())?.toSet() ?: emptySet()
    fun setDisabledCategories(c: Context, v: Set<String>) =
        sp(c).edit().putStringSet("disabled_categories", v).apply()

    // App theme: "system", "light" or "dark" (dark is the original look and stays the default)
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    fun themeMode(c: Context) = sp(c).getString("theme_mode", THEME_DARK) ?: THEME_DARK
    fun setThemeMode(c: Context, v: String) = sp(c).edit().putString("theme_mode", v).apply()

    // The user's own emojis for the add-quote picker (kept in the order they were added)
    private const val EMOJI_SEP = "\u001F"

    // Categories the user created, each with its emoji: "name<US>emoji" entries joined by <RS>
    private const val CATEGORY_SEP = "\u001E"
    fun customCategories(c: Context): List<Pair<String, String>> =
        (sp(c).getString("custom_categories", "") ?: "").split(CATEGORY_SEP).mapNotNull {
            val p = it.split(EMOJI_SEP, limit = 2)
            if (p.size == 2 && p[0].isNotBlank()) p[0] to p[1] else null
        }
    fun setCustomCategories(c: Context, v: List<Pair<String, String>>) =
        sp(c).edit().putString("custom_categories", v.joinToString(CATEGORY_SEP) { "${it.first}$EMOJI_SEP${it.second}" }).apply()
    fun customEmojis(c: Context): List<String> =
        (sp(c).getString("custom_emojis", "") ?: "").split(EMOJI_SEP).filter { it.isNotBlank() }
    fun setCustomEmojis(c: Context, v: List<String>) =
        sp(c).edit().putString("custom_emojis", v.joinToString(EMOJI_SEP)).apply()

    fun isDefaultsV2(c: Context) = sp(c).getBoolean("defaults_v2", false)
    fun setDefaultsV2(c: Context) = sp(c).edit().putBoolean("defaults_v2", true).apply()

    // Starter quotes grew in 1.2: phones that already seeded get the new ones added once (nothing is removed)
    fun isDefaultsV3(c: Context) = sp(c).getBoolean("defaults_v3", false)
    fun setDefaultsV3(c: Context) = sp(c).edit().putBoolean("defaults_v3", true).apply()

    // First-run explanation of permissions. Phones that already used the app (quotes seeded) never see it.
    fun initOnboarding(c: Context) {
        if (!sp(c).contains("onboarded")) sp(c).edit().putBoolean("onboarded", isSeeded(c)).apply()
    }
    fun isOnboarded(c: Context) = sp(c).getBoolean("onboarded", true)
    fun setOnboarded(c: Context) = sp(c).edit().putBoolean("onboarded", true).apply()

    // The quote of the latest reminder, shown on the home screen widget
    fun lastQuoteId(c: Context) = sp(c).getLong("last_quote_id", 0L)
    fun setLastQuoteId(c: Context, v: Long) = sp(c).edit().putLong("last_quote_id", v).apply()

    fun isSeeded(c: Context) = sp(c).getBoolean("seeded", false)
    fun setSeeded(c: Context) = sp(c).edit().putBoolean("seeded", true).apply()
}

fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}

fun formatClock(minutesAfterMidnight: Int): String =
    "%02d:%02d".format(minutesAfterMidnight / 60, minutesAfterMidnight % 60)

/** Same as [formatInterval] but in the phone's language. */
fun formatInterval(context: Context, minutes: Int): String = when {
    minutes < 60 -> context.getString(com.smrti.app.R.string.minutes_short, minutes)
    minutes % 60 == 0 -> context.getString(com.smrti.app.R.string.hours_short, minutes / 60)
    else -> context.getString(com.smrti.app.R.string.hours_minutes_short, minutes / 60, minutes % 60)
}
