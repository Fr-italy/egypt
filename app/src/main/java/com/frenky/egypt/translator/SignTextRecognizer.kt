package com.frenky.egypt.translator

import android.content.Context
import android.graphics.Bitmap

object SignTextRecognizer {
    suspend fun recognize(bitmap: Bitmap, context: Context): Result<String> {
        val vision = CloudVisionOcr.recognizeText(bitmap)
        if (vision.isSuccess) return vision

        val err = vision.exceptionOrNull()
        val useOffline = err != null && (
            err.message?.contains("blocked", ignoreCase = true) == true ||
                err.message?.contains("403", ignoreCase = true) == true ||
                err.message?.contains("Vision API", ignoreCase = true) == true ||
                err.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            )

        if (!useOffline) return vision

        return runCatching {
            TessArabicOcr.recognize(bitmap, context.applicationContext)
        }
    }
}
