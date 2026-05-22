package com.frenky.egypt.translator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object BitmapScaler {
    private const val MAX_SIDE = 1280

    fun decodeSampledFile(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
            ?: error("Impossibile leggere la foto")
    }

    fun scaleDown(bitmap: Bitmap): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= MAX_SIDE) return bitmap
        val scale = MAX_SIDE.toFloat() / max
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / sample > maxSide * 2 || h / sample > maxSide * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
