package com.smrti.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shows a WHOLE picture, whatever its shape (tall, wide, small, big). Nothing is cropped:
 * the picture is fitted inside the box, and the spare space is filled with a soft, darkened copy of it.
 */
object PictureFit {

    /** Draws [src] fitted inside a [w] x [h] area at the top-left of [canvas]. */
    fun draw(canvas: Canvas, src: Bitmap, w: Int, h: Int) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val box = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val sameShape = abs(src.width.toFloat() / src.height - w.toFloat() / h) < 0.04f
        if (!sameShape) {
            // backdrop: shrink to a few pixels and stretch back, which is a cheap blur; then darken it a little
            val tiny = Bitmap.createScaledBitmap(src, 16, max(1, (16f * h / w).roundToInt()), true)
            canvas.drawBitmap(tiny, null, box, paint)
            canvas.drawColor(0x73000000)
            if (tiny !== src) tiny.recycle()
        }
        val scale = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val fw = src.width * scale
        val fh = src.height * scale
        val left = (w - fw) / 2f
        val top = (h - fh) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + fw, top + fh), paint)
    }

    /** A new [w] x [h] bitmap with [src] fitted inside it. */
    fun fit(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(out), src, w, h)
        return out
    }
}
