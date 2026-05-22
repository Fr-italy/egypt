package com.frenky.egypt.translator

import android.graphics.Bitmap

/** Solo Google Cloud Vision (no Tesseract nativo = niente crash). */
object SignTextRecognizer {
    suspend fun recognize(bitmap: Bitmap): Result<String> = CloudVisionOcr.recognizeText(bitmap)
}
