package com.smrti.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/** A scope that outlives screens, so an import that is running isn't cut off when the user leaves the page. */
object AppScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/**
 * "My images" use the original photos in place (no copy, so no extra storage and no copying time).
 * If Android won't let the app keep access to a photo, that one is copied into app storage instead,
 * exactly like earlier versions did.
 */
object ImageStore {
    // Notification pictures never need more than roughly a phone's width.
    private const val MAX_EDGE = 720

    /**
     * Adds the picked photos. Runs in [AppScope], so leaving the screen mid-import can't leave a half-finished
     * file behind. [onDone] is called on the IO thread with how many were added.
     */
    fun importAsync(context: Context, uris: List<Uri>, onDone: (Int) -> Unit = {}) {
        val app = context.applicationContext
        AppScope.io.launch { onDone(import(app, uris)) }
    }

    suspend fun import(context: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var added = 0
        for (uri in uris) if (import(context, uri) != null) added++
        added
    }

    suspend fun import(context: Context, uri: Uri): QuoteImage? = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).images()
        try {
            val kept = try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (_: Exception) {
                false
            }
            if (kept) {
                val path = uri.toString()
                if (dao.countPath(path) > 0) return@withContext null // already added
                val image = QuoteImage(path = path)
                image.copy(id = dao.insert(image))
            } else {
                copyIntoApp(context, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Fallback for photos the app can't keep a link to: a scaled copy in app storage. */
    private suspend fun copyIntoApp(context: Context, uri: Uri): QuoteImage? {
        val path = copyFile(context, uri) ?: return null
        return try {
            val image = QuoteImage(path = path)
            image.copy(id = AppDatabase.get(context).images().insert(image))
        } catch (e: Exception) {
            File(path).delete() // never leave a file that has no row
            null
        }
    }

    private fun copyFile(context: Context, uri: Uri): String? {
        val bitmap = decodeScaled(context, uri, 1080) ?: return null
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            file.absolutePath
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** Keeps a picked photo for a quote: a long-term link to the original, or a scaled copy if Android refuses. Returns the path to store. */
    suspend fun keep(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uri.toString()
        } catch (_: Exception) {
            copyFile(context, uri)
        }
    }

    /** Takes the picture out of the app. Photos the app only linked to are never touched. */
    suspend fun remove(context: Context, image: QuoteImage) = withContext(Dispatchers.IO) {
        AppDatabase.get(context).images().delete(image)
        deleteFileIfCopy(image)
    }

    /** Removes only the list entry, so the delete can still be undone. Call [finishRemove] when it can't. */
    suspend fun removeEntry(context: Context, image: QuoteImage) = withContext(Dispatchers.IO) {
        AppDatabase.get(context).images().delete(image)
    }

    suspend fun finishRemove(image: QuoteImage) = withContext(Dispatchers.IO) { deleteFileIfCopy(image) }

    suspend fun restore(context: Context, image: QuoteImage) = withContext(Dispatchers.IO) {
        AppDatabase.get(context).images().insert(image)
    }

    private fun deleteFileIfCopy(image: QuoteImage) {
        if (!image.isContent) File(image.path).delete()
    }

    /**
     * Housekeeping on app start:
     *  - drops entries whose picture is gone (deleted file, or a phone restored from a backup where the
     *    old photo links don't exist any more),
     *  - deletes copied files that no entry points to (left by an interrupted import).
     */
    suspend fun cleanUp(context: Context) = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.get(context).images()
            val all = dao.all()
            val granted = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
            for (image in all) {
                val alive = if (image.isContent) image.path in granted else File(image.path).exists()
                if (!alive) dao.deleteById(image.id)
            }
            // pictures chosen for single quotes: forget the ones that are gone, keep the copied files that are still used
            val quoteDao = AppDatabase.get(context).quotes()
            val withPicture = quoteDao.withImage()
            for (q in withPicture) {
                val p = q.imagePath ?: continue
                val alive = if (p.startsWith("content://")) p in granted else File(p).exists()
                if (!alive) quoteDao.setImage(q.id, null)
            }
            val used = (all.map { it.path } + withPicture.mapNotNull { it.imagePath })
                .filter { !it.startsWith("content://") }.map { File(it).absolutePath }.toSet()
            File(context.filesDir, "images").listFiles()?.forEach { f ->
                if (f.absolutePath !in used) f.delete()
            }
        } catch (_: Throwable) {
            // housekeeping must never crash the app
        }
    }

    /** Decodes a copied file, scaled down so it never costs more memory than the notification needs. */
    fun load(path: String): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Throwable) {
        null
    }

    /** Decodes a gallery / picked image (content:// uri), scaled down so large camera photos don't blow up the notification. */
    fun loadUri(context: Context, uri: Uri): Bitmap? = decodeScaled(context, uri, MAX_EDGE)

    private fun decodeScaled(context: Context, uri: Uri, maxEdge: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxEdge) {
                val scale = maxEdge.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    } catch (_: Throwable) {
        null
    }
}
