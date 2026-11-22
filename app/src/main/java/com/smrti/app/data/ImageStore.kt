package com.smrti.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/** Copies user-picked images into app storage (scaled, rotation applied) so they survive the original being moved. */
object ImageStore {
    private const val MAX_EDGE = 1080

    suspend fun import(context: Context, uri: Uri): QuoteImage? = withContext(Dispatchers.IO) {
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            val image = QuoteImage(path = file.absolutePath)
            val id = AppDatabase.get(context).images().insert(image)
            image.copy(id = id)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun remove(context: Context, image: QuoteImage) = withContext(Dispatchers.IO) {
        AppDatabase.get(context).images().delete(image)
        File(image.path).delete()
    }

    fun load(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    /** Decodes a gallery image (content:// uri), scaled down so large camera photos don't blow up the notification. */
    fun loadUri(context: Context, uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    } catch (_: Exception) {
        null
    }
}
