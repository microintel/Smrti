package com.smrti.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlin.random.Random

data class Album(val id: String, val name: String, val count: Int, val cover: Uri)

/** Reads the phone's photo albums (needs the photos permission). */
object AlbumStore {
    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun listAlbums(context: Context): List<Album> = try {
        val albums = LinkedHashMap<String, Album>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val bucket = c.getString(bucketCol) ?: continue
                val existing = albums[bucket]
                albums[bucket] = if (existing == null) {
                    Album(bucket, c.getString(nameCol) ?: "Unnamed", 1, ContentUris.withAppendedId(collection, c.getLong(idCol)))
                } else existing.copy(count = existing.count + 1)
            }
        }
        albums.values.sortedBy { it.name.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }

    /** Images of the chosen albums, oldest first. */
    fun imageUris(context: Context, albumIds: Set<String>): List<Uri> {
        if (albumIds.isEmpty()) return emptyList()
        return try {
            val out = ArrayList<Uri>()
            val marks = albumIds.joinToString(",") { "?" }
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.BUCKET_ID} IN ($marks)",
                albumIds.toTypedArray(),
                "${MediaStore.Images.Media.DATE_ADDED} ASC, ${MediaStore.Images.Media._ID} ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext()) out.add(ContentUris.withAppendedId(collection, c.getLong(idCol)))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Chooses the picture for the next notification from "my images" plus the chosen albums. */
object ImagePool {
    class Pick(val source: String, val bitmap: android.graphics.Bitmap)

    suspend fun next(context: Context): Pick? {
        val imported = AppDatabase.get(context).images().all().map { it.path }
        val albums = AlbumStore.imageUris(context, Prefs.albumIds(context)).map { it.toString() }
        val sources = imported + albums
        if (sources.isEmpty()) return null

        val series = Prefs.imageOrder(context) == Prefs.ORDER_SERIES
        val start = if (series) Prefs.seriesIndex(context).coerceAtLeast(0) else Random.nextInt(sources.size)
        // a file may have been deleted: try the next few instead of sending no picture
        for (i in 0 until minOf(sources.size, 5)) {
            val index = (start + i) % sources.size
            val source = sources[index]
            val bitmap = if (source.startsWith("content://")) ImageStore.loadUri(context, Uri.parse(source))
            else ImageStore.load(source)
            if (bitmap != null) {
                if (series) Prefs.setSeriesIndex(context, index + 1)
                return Pick(source, bitmap)
            }
        }
        return null
    }
}
