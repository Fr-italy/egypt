package com.frenky.egypt.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fallback diretto dall'app se il server non ha ancora aggiornato il tasso. */
object ExchangeRateClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class LiveRate(val rate: Double, val source: String)

    fun fetchEgpToEur(): LiveRate? {
        fetchFromErApi()?.let { return it }
        return fetchFromCurrencyCdn()
    }

    private fun fetchFromErApi(): LiveRate? = runCatching {
        val body = get("https://open.er-api.com/v6/latest/EGP") ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        if (root["result"]?.jsonPrimitive?.content != "success") return null
        val eur = root["rates"]?.jsonObject?.get("EUR")?.jsonPrimitive?.content?.toDoubleOrNull()
        eur?.takeIf { it in 0.001..0.1 }?.let { LiveRate(it, "ExchangeRate-API") }
    }.getOrNull()

    private fun fetchFromCurrencyCdn(): LiveRate? = runCatching {
        val body = get(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/egp.json",
        ) ?: return null
        val egp = json.parseToJsonElement(body).jsonObject["egp"]?.jsonObject ?: return null
        val eur = egp["eur"]?.jsonPrimitive?.content?.toDoubleOrNull()
        eur?.takeIf { it in 0.001..0.1 }?.let { LiveRate(it, "currency-api") }
    }.getOrNull()

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}
