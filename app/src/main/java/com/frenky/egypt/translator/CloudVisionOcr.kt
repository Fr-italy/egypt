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

    suspend fun recognizeText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MAPS_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Chiave Google mancante nell'app. Contatta chi ha compilato l'APK."),
            )
        }
        runCatching {
            val jpeg = bitmapToJpegBase64(bitmap)
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
                .url("https://vision.googleapis.com/v1/images:annotate?key=$apiKey")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = parseError(body) ?: "Vision API HTTP ${response.code}"
                    error(
                        if (response.code == 403 || msg.contains("API key", ignoreCase = true)) {
                            "Abilita Cloud Vision API nella console Google (stesso progetto della mappa). $msg"
                        } else {
                            msg
                        },
                    )
                }
                parseText(body)
            }
        }
    }

    private fun bitmapToJpegBase64(bitmap: Bitmap): String {
        var bmp = bitmap
        val maxSide = 1280
        if (bmp.width > maxSide || bmp.height > maxSide) {
            val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt(),
                (bmp.height * scale).toInt(),
                true,
            )
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
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
