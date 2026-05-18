package com.frenky.egypt.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Phrase(
    val it: String,
    val en: String,
    val ar: String,
    val arLatin: String,
)

object PhraseRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<Phrase> {
        val raw = context.assets.open("phrases.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(raw)
    }
}
