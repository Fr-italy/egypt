package com.frenky.egypt.translator

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class SignTranslation(
    val arabic: String,
    val italian: String,
    val english: String = "",
)

class ArabicOcrTranslator(private val appContext: Context) {
    private val arToIt: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ARABIC)
            .setTargetLanguage(TranslateLanguage.ITALIAN)
            .build(),
    )
    private val arToEn: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ARABIC)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    )

    private var modelsReady = false
    private var tessReady = false
    private var tessDataPath: String? = null

    suspend fun ensureModelsDownloaded(onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("Preparo lettore arabo…")
            prepareTesseract()
            onProgress("Scarico modelli traduzione (prima volta, ~30 MB)…")
            val conditions = DownloadConditions.Builder().build()
            Tasks.await(arToIt.downloadModelIfNeeded(conditions), 120, TimeUnit.SECONDS)
            Tasks.await(arToEn.downloadModelIfNeeded(conditions), 120, TimeUnit.SECONDS)
            modelsReady = true
            onProgress("")
        }
    }

    suspend fun scanAndTranslate(bitmap: Bitmap): Result<SignTranslation> = withContext(Dispatchers.Default) {
        if (!tessReady || !modelsReady) {
            return@withContext Result.failure(IllegalStateException("Modelli non pronti"))
        }
        runCatching {
            val raw = recognizeArabic(bitmap).trim()
            if (raw.isBlank()) {
                error("Nessun testo rilevato. Avvicinati, luce buona, testo dritto.")
            }
            val arabic = extractArabicOrAll(raw)
            if (arabic.isBlank()) {
                error("Testo trovato ma senza arabo. Inquadra scritte in arabo.")
            }
            val italian = Tasks.await(arToIt.translate(arabic)).trim()
            val english = runCatching {
                Tasks.await(arToEn.translate(arabic)).trim()
            }.getOrDefault("")
            SignTranslation(arabic = arabic, italian = italian, english = english)
        }
    }

    private fun prepareTesseract() {
        if (tessReady) return
        val base = File(appContext.filesDir, "tesseract")
        val tessdataDir = File(base, "tessdata")
        tessdataDir.mkdirs()
        val trained = File(tessdataDir, "ara.traineddata")
        // tess-two 3.x richiede traineddata 3.04 (~6 MB), non tessdata_fast
        if (!trained.exists() || trained.length() < 5_000_000L) {
            trained.delete()
            appContext.assets.open("tessdata/ara.traineddata").use { input ->
                trained.outputStream().use { output -> input.copyTo(output) }
            }
        }
        tessDataPath = base.absolutePath
        tessReady = true
    }

    private fun recognizeArabic(bitmap: Bitmap): String {
        val path = tessDataPath ?: error("Tesseract non inizializzato")
        val api = TessBaseAPI()
        return try {
            // Arabo senza file .cube: solo motore Tesseract classico (vedi tess-two #239)
            check(
                api.init(path, "ara", TessBaseAPI.OEM_TESSERACT_ONLY),
            ) { "Init OCR arabo fallito. Reinstalla l'app o cancella dati Egypt." }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(bitmap)
            api.utF8Text.orEmpty()
        } finally {
            api.end()
        }
    }

    fun close() {
        arToIt.close()
        arToEn.close()
    }

    companion object {
        private val ARABIC_REGEX = Regex("[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]+")

        fun extractArabicOrAll(text: String): String {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val arabicLines = lines.filter { line -> ARABIC_REGEX.containsMatchIn(line) }
            return (if (arabicLines.isNotEmpty()) arabicLines else lines).joinToString("\n")
        }
    }
}
