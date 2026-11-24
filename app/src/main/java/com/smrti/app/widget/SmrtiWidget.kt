package com.smrti.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.smrti.app.MainActivity
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.AppScope
import com.smrti.app.data.ImagePool
import com.smrti.app.data.Prefs
import com.smrti.app.data.Quote
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Home screen widget: the quote of the latest reminder, with its picture behind the words.
 * It changes by itself whenever a reminder is sent; the refresh button shows another one.
 */
class SmrtiWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        AppScope.io.launch {
            try { render(app, pickNew = false) } finally { pending.finish() }
        }
    }

    // resized: redraw so the picture is cut to the new shape
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        val app = context.applicationContext
        AppScope.io.launch {
            try { render(app, pickNew = false) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val pending = goAsync()
            val app = context.applicationContext
            AppScope.io.launch {
                try { render(app, pickNew = true) } finally { pending.finish() }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_NEXT = "com.smrti.app.widget.NEXT"

        /** Redraws every placed widget in the background. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            AppScope.io.launch { render(app, pickNew = false) }
        }

        /** Shows another quote on every placed widget (what the refresh button does). */
        fun showAnother(context: Context) {
            val app = context.applicationContext
            AppScope.io.launch { render(app, pickNew = true) }
        }

        /** True when at least one widget is on the home screen. */
        fun isPlaced(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SmrtiWidget::class.java)).isNotEmpty()

        /** Asks Android to add the widget to the home screen (the person confirms). False if this phone can't do that. */
        fun requestPin(context: Context): Boolean {
            val m = AppWidgetManager.getInstance(context)
            if (!m.isRequestPinAppWidgetSupported) return false
            return m.requestPinAppWidget(ComponentName(context, SmrtiWidget::class.java), null, null)
        }

        /** Redraws every placed widget and returns when done (used right after a reminder is sent). */
        suspend fun refreshNow(context: Context) = render(context.applicationContext, pickNew = false)

        private suspend fun render(context: Context, pickNew: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SmrtiWidget::class.java))
            if (ids.isEmpty()) return
            val dao = AppDatabase.get(context).quotes()
            var quote: Quote? = if (pickNew) null else Prefs.lastQuoteId(context).takeIf { it > 0 }?.let { dao.byId(it) }
            if (quote == null) quote = dao.randomActive() ?: dao.random()
            val changed = quote != null && (pickNew || quote.id != Prefs.lastQuoteId(context))
            if (quote != null) Prefs.setLastQuoteId(context, quote.id)

            // which picture: the one remembered for this quote (the reminder's), else the quote's own, else a random one
            var source: String? = if (changed) null else Prefs.lastWidgetImage(context)
            if (quote != null && source == null) {
                source = if (!showPictures(context)) "" else try {
                    quote.imagePath ?: ImagePool.randomSource(context) ?: ""
                } catch (_: Throwable) { "" }
                Prefs.setLastWidgetImage(context, source)
            }
            val picture: Bitmap? = if (quote != null && !source.isNullOrEmpty() && showPictures(context)) {
                try { ImagePool.load(context, source)?.bitmap } catch (_: Throwable) { null }
            } else null

            for (id in ids) {
                val (wDp, hDp) = sizeDp(manager, id)
                val cut = picture?.let { try { cover(context, it, wDp, hDp) } catch (_: Throwable) { null } }
                manager.updateAppWidget(id, build(context, quote, cut))
            }
        }

        private fun showPictures(context: Context) = Prefs.useImages(context) && Prefs.widgetImages(context)

        private fun sizeDp(manager: AppWidgetManager, id: Int): Pair<Int, Int> {
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(110)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110).coerceAtLeast(80)
            return w to h
        }

        /** Cuts the picture to the widget's shape and keeps it small enough for the system to carry. */
        private fun cover(context: Context, src: Bitmap, wDp: Int, hDp: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            var w = wDp * density
            var h = hDp * density
            // stay well inside the size limit for widget pictures (about 150k pixels)
            val k = sqrt(150_000f / max(1f, w * h))
            if (k < 1f) { w *= k; h *= k }
            val ow = w.toInt().coerceAtLeast(2)
            val oh = h.toInt().coerceAtLeast(2)

            // centre-crop the source to the widget's shape
            val target = ow.toFloat() / oh
            val srcRatio = src.width.toFloat() / src.height
            val cropW: Int; val cropH: Int
            if (srcRatio > target) { cropH = src.height; cropW = (src.height * target).toInt() }
            else { cropW = src.width; cropH = (src.width / target).toInt() }
            val left = (src.width - cropW) / 2
            val top = (src.height - cropH) / 2

            val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // before Android 12 the layout can't round the corners itself, so round the picture
            if (Build.VERSION.SDK_INT < 31) {
                val r = 24f * density * ow / (wDp * density).coerceAtLeast(1f)
                canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, ow.toFloat(), oh.toFloat()), r, r, Path.Direction.CW) })
            }
            canvas.drawBitmap(
                src, android.graphics.Rect(left, top, left + cropW, top + cropH),
                RectF(0f, 0f, ow.toFloat(), oh.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG)
            )
            return out
        }

        private fun build(context: Context, quote: Quote?, picture: Bitmap?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quote)
            if (quote == null) {
                views.setTextViewText(R.id.w_category, context.getString(R.string.app_name))
                views.setTextViewText(R.id.w_text, context.getString(R.string.widget_empty))
                views.setViewVisibility(R.id.w_author, View.GONE)
            } else {
                views.setTextViewText(R.id.w_category, quote.category)
                views.setTextViewText(R.id.w_text, quote.text)
                views.setTextViewText(R.id.w_author, "— ${quote.author}")
                views.setViewVisibility(R.id.w_author, if (quote.author.isBlank()) View.GONE else View.VISIBLE)
            }
            if (picture != null) {
                views.setImageViewBitmap(R.id.w_image, picture)
                views.setViewVisibility(R.id.w_image, View.VISIBLE)
                views.setViewVisibility(R.id.w_scrim, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.w_image, View.GONE)
                views.setViewVisibility(R.id.w_scrim, View.GONE)
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val next = PendingIntent.getBroadcast(
                context, 1, Intent(context, SmrtiWidget::class.java).setAction(ACTION_NEXT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            views.setOnClickPendingIntent(R.id.w_next, next)
            return views
        }
    }
}
