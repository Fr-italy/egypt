package com.frenky.egypt.translator

import android.graphics.Bitmap
import android.util.Base64
import com.frenky.egypt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object CloudVisionOcr {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun apiKey(): String {
        val vision = BuildConfig.VISION_API_KEY.trim()
        if (vision.isNotBlank()) return vision
        return BuildConfig.MAPS_API_KEY.trim()
    }

    suspend fun recognizeText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey()
        if (key.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Chiave Google mancante nell'APK. Aggiungi MAPS_API_KEY o VISION_API_KEY in local.properties e ricompila.",
                ),
            )
        }
        runCatching {
            val scaled = BitmapScaler.scaleDown(bitmap)
            val jpeg = bitmapToJpegBase64(scaled)
            val json = """
                {
                  "requests": [{
                    "image": {"content": "$jpeg"},
                    "features": [{"type": "TEXT_DETECTION", "maxResults": 1}],
                    "imageContext": {"languageHints": ["ar", "en"]}
                  }]
                }
            """.trimIndent()
            val request = Request.Builder()
                .url("https://vision.googleapis.com/v1/images:annotate?key=$key")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = parseError(body) ?: "Errore HTTP ${response.code}"
                    if (response.code == 403 || msg.contains("blocked", ignoreCase = true)) {
                        error(visionBlockedHelp())
                    } else {
                        error(msg)
                    }
                }
                parseText(body)
            }
        }
    }

    private fun visionBlockedHelp(): String =
        "La chiave API nell'app non può chiamare Cloud Vision (bloccata).\n\n" +
            "Google Cloud Console → Credenziali → apri la chiave usata per Egypt:\n" +
            "• Restrizioni API: aggiungi «Cloud Vision API» (oltre a Maps), oppure «Non limitare la chiave»\n" +
            "• Deve essere lo stesso progetto dove hai abilitato Vision API\n" +
            "• Fatturazione attiva sul progetto\n\n" +
            "Poi ricompila l'APK. In alternativa crea una seconda chiave solo per Vision, " +
            "mettila in local.properties come VISION_API_KEY=... e ricompila."

    private fun bitmapToJpegBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseText(json: String): String {
        val root = JSONObject(json)
        val responses = root.optJSONArray("responses") ?: return ""
        if (responses.length() == 0) return ""
        val first = responses.getJSONObject(0)
        first.optJSONObject("error")?.let { err ->
            error(err.optString("message", "Errore Vision API"))
        }
        val full = first.optJSONObject("fullTextAnnotation")?.optString("text").orEmpty()
        if (full.isNotBlank()) return full.trim()
        val annotations = first.optJSONArray("textAnnotations") ?: return ""
        if (annotations.length() == 0) return ""
        return annotations.getJSONObject(0).optString("description", "").trim()
    }

    private fun parseError(json: String): String? = runCatching {
        JSONObject(json).optJSONObject("error")?.optString("message")
    }.getOrNull()
}
