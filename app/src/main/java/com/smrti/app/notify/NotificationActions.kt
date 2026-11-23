package com.smrti.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.AppScope
import kotlinx.coroutines.launch

/** Handles the Save button under a reminder notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val quoteId = intent.getLongExtra(Notifier.EXTRA_QUOTE_ID, 0L)
        val pending = goAsync()
        AppScope.io.launch {
            try {
                when (action) {
                    Notifier.ACTION_SAVE -> {
                        if (quoteId > 0) AppDatabase.get(app).quotes().setFavorite(quoteId, true)
                        toast(app, R.string.notif_saved)
                    }
                }
                NotificationManagerCompat.from(app).cancel(Notifier.NOTIFICATION_ID)
            } catch (_: Throwable) {
                // a button must never crash the app
            } finally {
                pending.finish()
            }
        }
    }

    private fun toast(context: Context, res: Int) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()
        }
    }
}
