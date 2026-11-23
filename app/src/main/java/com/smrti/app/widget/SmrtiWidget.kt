package com.smrti.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.smrti.app.MainActivity
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.AppScope
import com.smrti.app.data.Prefs
import com.smrti.app.data.Quote
import kotlinx.coroutines.launch

/** Home screen widget: the latest reminder's quote; the refresh button shows another one. */
class SmrtiWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
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

        /** Redraws every placed widget (e.g. after a new reminder was sent). */
        fun refresh(context: Context) {
            val app = context.applicationContext
            AppScope.io.launch { render(app, pickNew = false) }
        }

        private suspend fun render(context: Context, pickNew: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SmrtiWidget::class.java))
            if (ids.isEmpty()) return
            val dao = AppDatabase.get(context).quotes()
            var quote: Quote? = if (pickNew) null else Prefs.lastQuoteId(context).takeIf { it > 0 }?.let { dao.byId(it) }
            if (quote == null) quote = dao.random()
            if (quote != null) Prefs.setLastQuoteId(context, quote.id)
            val views = build(context, quote)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun build(context: Context, quote: Quote?): RemoteViews {
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
