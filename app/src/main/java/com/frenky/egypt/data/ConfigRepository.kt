package com.frenky.egypt.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigRepository(context: Context) {
    private val store = context.egyptPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val keyCached = stringPreferencesKey("egypt_config_json")

    val config: Flow<EgyptConfig> = store.data.map { prefs ->
        val raw = prefs[keyCached]
        if (raw.isNullOrBlank()) {
            EgyptConfig.defaults()
        } else {
            runCatching { json.decodeFromString<EgyptConfig>(raw) }
                .getOrElse { EgyptConfig.defaults() }
        }
    }

    suspend fun save(config: EgyptConfig) {
        store.edit { it[keyCached] = json.encodeToString(config) }
    }

    suspend fun refreshFromServer(): Result<EgyptConfig> = withContext(Dispatchers.IO) {
        val serverResult = EgyptApi.fetchConfig().mapCatching { response ->
            if (!response.ok || response.config == null) {
                error(response.error ?: "Config non disponibile")
            }
            var cfg = response.config!!
            if (!cfg.rate_source.contains("auto", ignoreCase = true)) {
                cfg = applyDirectRateIfNewer(cfg)
            }
            save(cfg)
            cfg
        }
        if (serverResult.isSuccess) return@withContext serverResult

        runCatching {
            val live = ExchangeRateClient.fetchEgpToEur()
                ?: error(serverResult.exceptionOrNull()?.message ?: "Tasso non disponibile")
            val cfg = EgyptConfig.defaults().copy(
                egp_to_eur = live.rate,
                rate_source = "${live.source} (app)",
                rate_updated_at = todayDate(),
            )
            save(cfg)
            cfg
        }
    }

    private fun applyDirectRateIfNewer(cfg: EgyptConfig): EgyptConfig {
        val live = ExchangeRateClient.fetchEgpToEur() ?: return cfg
        return cfg.copy(
            egp_to_eur = live.rate,
            rate_source = "${live.source} (app)",
            rate_updated_at = todayDate(),
        )
    }

    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
