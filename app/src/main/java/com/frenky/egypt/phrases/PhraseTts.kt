package com.frenky.egypt.phrases

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Pronuncia frasi in arabo (dialetto egiziano se disponibile sul telefono). */
class PhraseTts(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var speaking = false
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            val egypt = Locale.forLanguageTag("ar-EG")
            val arabic = Locale("ar")
            val chosen = when {
                engine.isLanguageAvailable(egypt) >= TextToSpeech.LANG_AVAILABLE -> egypt
                engine.isLanguageAvailable(arabic) >= TextToSpeech.LANG_AVAILABLE -> arabic
                else -> Locale.getDefault()
            }
            engine.language = chosen
            engine.setSpeechRate(0.92f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                    onSpeakingChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    speaking = false
                    onSpeakingChanged?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speaking = false
                    onSpeakingChanged?.invoke(false)
                }
            })
            ready = true
        }
    }

    fun speak(arabic: String, latinFallback: String = "") {
        if (!ready) return
        val text = arabic.trim().ifBlank { latinFallback.trim() }
        if (text.isEmpty()) return
        val id = "phrase_${text.hashCode()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
        speaking = false
        onSpeakingChanged?.invoke(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
