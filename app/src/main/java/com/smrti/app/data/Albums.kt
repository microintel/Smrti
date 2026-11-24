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

    private fun selection(albumIds: Set<String>) =
        "${MediaStore.Images.Media.BUCKET_ID} IN (${albumIds.joinToString(",") { "?" }})"

    /** How many photos the chosen albums hold right now (cheap: counts, doesn't load them). */
    fun countImages(context: Context, albumIds: Set<String>): Int {
        if (albumIds.isEmpty()) return 0
        return try {
            context.contentResolver.query(
                collection, arrayOf(MediaStore.Images.Media._ID),
                selection(albumIds), albumIds.toTypedArray(), null
            )?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /** The photo at [index] (oldest first) of the chosen albums, read without loading the others. */
    fun imageUriAt(context: Context, albumIds: Set<String>, index: Int): Uri? {
        if (albumIds.isEmpty() || index < 0) return null
        return try {
            context.contentResolver.query(
                collection, arrayOf(MediaStore.Images.Media._ID),
                selection(albumIds), albumIds.toTypedArray(),
                "${MediaStore.Images.Media.DATE_ADDED} ASC, ${MediaStore.Images.Media._ID} ASC"
            )?.use { c ->
                if (c.moveToPosition(index)) ContentUris.withAppendedId(collection, c.getLong(0)) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** How much of the photo library the user let Smrti see (Android 14+ lets people share only some photos). */
enum class PhotoAccess { FULL, PARTIAL, NONE }

object PhotoPermission {
    fun state(context: Context): PhotoAccess {
        fun granted(p: String) =
            androidx.core.content.ContextCompat.checkSelfPermission(context, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return when {
            android.os.Build.VERSION.SDK_INT >= 34 -> when {
                granted(android.Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.FULL
                granted(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.PARTIAL
                else -> PhotoAccess.NONE
            }
            android.os.Build.VERSION.SDK_INT >= 33 ->
                if (granted(android.Manifest.permission.READ_MEDIA_IMAGES)) PhotoAccess.FULL else PhotoAccess.NONE
            else ->
                if (granted(android.Manifest.permission.READ_EXTERNAL_STORAGE)) PhotoAccess.FULL else PhotoAccess.NONE
        }
    }
}

/** Chooses the picture for the next notification from "my images" plus the chosen albums. */
object ImagePool {
    class Pick(val source: String, val bitmap: android.graphics.Bitmap)

    /** The picture for one specific source (a quote's own picture); null if it can't be loaded any more. */
    fun load(context: Context, source: String): Pick? {
        val bitmap = if (source.startsWith("content://")) ImageStore.loadUri(context, Uri.parse(source))
        else ImageStore.load(source)
        return bitmap?.let { Pick(source, it) }
    }

    /** A random picture's path/uri for showing in the app. Reads no bitmap and leaves the "series" position alone. */
    suspend fun randomSource(context: Context): String? {
        val dao = AppDatabase.get(context).images()
        val albumIds = Prefs.albumIds(context)
        val importedCount = dao.count()
        val total = importedCount + AlbumStore.countImages(context, albumIds)
        if (total == 0) return null
        val index = Random.nextInt(total)
        return if (index < importedCount) dao.at(index)?.path
        else AlbumStore.imageUriAt(context, albumIds, index - importedCount)?.toString()
    }

    /**
     * Picks one source without reading every image: only the counts and the single chosen row are queried
     * (index 0 until imported = "my images", the rest = album photos).
     */
    suspend fun next(context: Context): Pick? {
        val dao = AppDatabase.get(context).images()
        val albumIds = Prefs.albumIds(context)
        val importedCount = dao.count()
        val albumCount = AlbumStore.countImages(context, albumIds)
        val total = importedCount + albumCount
        if (total == 0) return null

        val series = Prefs.imageOrder(context) == Prefs.ORDER_SERIES
        val start = if (series) Prefs.seriesIndex(context).coerceAtLeast(0) % total else Random.nextInt(total)
        // a file may have been deleted: try the next few instead of sending no picture
        for (i in 0 until minOf(total, 5)) {
            val index = (start + i) % total
            val source: String? =
                if (index < importedCount) dao.at(index)?.path
                else AlbumStore.imageUriAt(context, albumIds, index - importedCount)?.toString()
            if (source == null) continue
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
