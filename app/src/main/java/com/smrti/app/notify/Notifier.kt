package com.smrti.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smrti.app.MainActivity
import com.smrti.app.R
import com.smrti.app.data.AppDatabase
import com.smrti.app.data.ImagePool
import com.smrti.app.data.Prefs
import com.smrti.app.widget.SmrtiWidget

object Notifier {
    // v2: Android never lets an app raise an existing channel's importance, so the pop-up
    // (heads-up) channel needs a new id. The old low-importance one is removed.
    private const val CHANNEL_ID = "smrti_reminders_v3"
    private const val OLD_CHANNEL_ID = "smrti_reminders"
    // v2 used the system default sound; a channel's sound can't be changed after it exists, so it is replaced
    private const val OLD_CHANNEL_ID_V2 = "smrti_reminders_v2"
    const val NOTIFICATION_ID = 1001

    // Extras carried by the tap intent so the app can show the exact reminder that was tapped.
    const val EXTRA_QUOTE_ID = "smrti.quote_id"
    const val EXTRA_TEXT = "smrti.text"
    const val EXTRA_AUTHOR = "smrti.author"
    const val EXTRA_CATEGORY = "smrti.category"
    const val EXTRA_IMAGE_PATH = "smrti.image_path"

    const val ACTION_SAVE = "com.smrti.app.action.SAVE"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID_V2)
        val channel = NotificationChannel(
            CHANNEL_ID, "Smrti reminders", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Hourly quotes, motivation and dialogues"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC // readable on the lock screen
            setShowBadge(true)
            // Smrti's own sound instead of the phone's default notification sound
            setSound(
                android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.smrti_notification}"),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)
    }

    /** True while the screen is off or the lock screen is showing. */
    fun isDeviceLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        val power = context.getSystemService(android.os.PowerManager::class.java)
        return keyguard?.isKeyguardLocked == true || power?.isInteractive == false
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Picks the next quote (and a random user image, if any) and posts it. Returns false if nothing was sent. */
    suspend fun sendNext(context: Context): Boolean {
        if (!canNotify(context)) return false
        val db = AppDatabase.get(context)
        val quote = db.quotes().next(Prefs.disabledCategories(context).toList()) ?: return false
        // the picture chosen for this quote wins; without one (or if it is gone) a random picture is used
        val pick = if (Prefs.useImages(context)) try {
            quote.imagePath?.let { ImagePool.load(context, it) } ?: ImagePool.next(context)
        } catch (_: Throwable) { null } else null

        // First try with the picture; if Android rejects it (bitmap too big etc.), post it without.
        try {
            post(context, quote, pick, withPicture = true)
        } catch (_: Throwable) {
            try {
                post(context, quote, pick, withPicture = false)
            } catch (_: Throwable) {
                return false
            }
        }
        db.quotes().markShown(quote.id, System.currentTimeMillis())
        Prefs.setLastQuoteId(context, quote.id)
        try { SmrtiWidget.refresh(context) } catch (_: Throwable) { }
        return true
    }

    private fun post(context: Context, quote: com.smrti.app.data.Quote, pick: ImagePool.Pick?, withPicture: Boolean) {
        val picture = if (withPicture) pick?.bitmap else null
        val body = if (quote.author.isNotBlank()) "${quote.text}\n— ${quote.author}" else quote.text
        val title = "Smrti"

        // Tapping opens the app and shows this exact reminder (quote + picture).
        val tapIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_QUOTE_ID, quote.id)
            .putExtra(EXTRA_TEXT, quote.text)
            .putExtra(EXTRA_AUTHOR, quote.author)
            .putExtra(EXTRA_CATEGORY, quote.category)
            .putExtra(EXTRA_IMAGE_PATH, pick?.source)
        val tap = PendingIntent.getActivity(
            context, (quote.id % Int.MAX_VALUE).toInt(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Fallback icon (used if the banner is off): square thumbnail of the picture, else the Smrti logo.
        val largeIcon = picture?.let { squareThumbnail(it, 256) }
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.smrti_logo)

        // Buttons under the notification: save as favourite
        fun actionIntent(action: String) = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, NotificationActionReceiver::class.java).setAction(action)
                .putExtra(EXTRA_QUOTE_ID, quote.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .addAction(R.drawable.ic_stat_smrti, context.getString(R.string.notif_save), actionIntent(ACTION_SAVE))
            .setSmallIcon(R.drawable.ic_stat_smrti)
            .setColor(0xFFFAB62F.toInt())
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tap)
            .setAutoCancel(true)
            // pop up as a banner on the home screen / over other apps, and show on the lock screen
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS) // sound comes from the channel
            .setOnlyAlertOnce(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        val nativeCollapsedPicture = Build.VERSION.SDK_INT >= 31
        if (picture != null && Prefs.largeImage(context) && !nativeCollapsedPicture) {
            // Android 11 and older: custom collapsed view, wide picture banner with the quote on top.
            // (Android 12+ restricts custom collapsed views to a thin strip, so it uses the native mode below.)
            val collapsed = RemoteViews(context.packageName, R.layout.notification_collapsed).apply {
                setImageViewBitmap(R.id.n_image, banner(picture, 800, 150))
                setTextViewText(R.id.n_text, quote.text)
                setTextViewText(R.id.n_author, if (quote.author.isNotBlank()) "— ${quote.author}" else "")
                setViewVisibility(R.id.n_author, if (quote.author.isNotBlank()) View.VISIBLE else View.GONE)
            }
            builder.setCustomContentView(collapsed)
        }

        if (picture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)
                    .also { if (nativeCollapsedPicture && Prefs.largeImage(context)) it.showBigPictureWhenCollapsed(true) }
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }


    /** Center-crops (biased slightly upward) to a wide banner. */
    private fun banner(source: Bitmap, width: Int, height: Int): Bitmap {
        val target = width.toFloat() / height
        val aspect = source.width.toFloat() / source.height
        val cropW = (if (aspect > target) (source.height * target).toInt() else source.width).coerceIn(1, source.width)
        val cropH = (if (aspect > target) source.height else (source.width / target).toInt()).coerceIn(1, source.height)
        val x = (source.width - cropW) / 2
        val y = ((source.height - cropH) * 0.4f).toInt()
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(source, x, y, cropW, cropH), width, height, true)
    }

    private fun squareThumbnail(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        return Bitmap.createScaledBitmap(square, size, size, true)
    }
}
