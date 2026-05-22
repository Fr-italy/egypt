package com.frenky.egypt.translator

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class SignTranslation(
    val arabic: String,
    val italian: String,
    val english: String = "",
)

/** OCR via Google Cloud Vision (rete) + traduzione ML Kit (offline dopo download). */
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

    suspend fun ensureModelsDownloaded(onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("Scarico modelli traduzione (~30 MB, prima volta)…")
            val conditions = DownloadConditions.Builder().build()
            Tasks.await(arToIt.downloadModelIfNeeded(conditions), 120, TimeUnit.SECONDS)
            Tasks.await(arToEn.downloadModelIfNeeded(conditions), 120, TimeUnit.SECONDS)
            modelsReady = true
            onProgress("")
        }
    }

    suspend fun scanAndTranslate(bitmap: Bitmap): Result<SignTranslation> = withContext(Dispatchers.IO) {
        if (!modelsReady) {
            return@withContext Result.failure(IllegalStateException("Modelli traduzione non pronti"))
        }
        runCatching {
            val raw = SignTextRecognizer.recognize(bitmap).getOrThrow().trim()
            if (raw.isBlank()) {
                error("Nessun testo rilevato. Avvicinati, luce buona, testo dritto.")
            }
            val arabic = extractArabicOrAll(raw)
            if (arabic.isBlank()) {
                error("Testo trovato ma senza arabo. Inquadra scritte in arabo.")
            }
            val textForTranslate = arabic.take(4_000)
            val italian = Tasks.await(arToIt.translate(textForTranslate)).trim()
            val english = runCatching {
                Tasks.await(arToEn.translate(textForTranslate)).trim()
            }.getOrDefault("")
            SignTranslation(arabic = arabic, italian = italian, english = english)
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
