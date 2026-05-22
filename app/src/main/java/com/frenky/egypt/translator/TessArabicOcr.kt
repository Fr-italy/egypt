package com.frenky.egypt.translator

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** OCR arabo offline (fallback se Vision API non è abilitata). */
object TessArabicOcr {
    private const val BUNDLE_VERSION = 3

    @Volatile
    private var ready = false

    @Synchronized
    fun ensureReady(context: Context) {
        if (ready) return
        val base = File(context.filesDir, "tesseract")
        val tessdataDir = File(base, "tessdata")
        val marker = File(tessdataDir, ".bundle_v$BUNDLE_VERSION")
        if (!marker.exists()) {
            tessdataDir.mkdirs()
            tessdataDir.listFiles()?.forEach { it.delete() }
            context.assets.list("tessdata")?.orEmpty()?.forEach { name ->
                context.assets.open("tessdata/$name").use { input ->
                    File(tessdataDir, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
            marker.writeText("ok")
        }
        check(File(tessdataDir, "ara.cube.params").exists()) {
            "File OCR arabo mancante nell'app."
        }
        ready = true
    }

    fun recognize(bitmap: Bitmap, context: Context): String {
        ensureReady(context)
        val dataPath = File(context.filesDir, "tesseract").absolutePath + File.separator
        val api = TessBaseAPI()
        return try {
            check(api.init(dataPath, "ara", TessBaseAPI.OEM_TESSERACT_ONLY)) {
                "OCR offline non avviato."
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(bitmap)
            api.utF8Text.orEmpty().trim()
        } finally {
            api.end()
        }
    }
}
