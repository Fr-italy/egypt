package com.frenky.egypt.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class PreferencesRepository(private val context: Context) {
    private val store = context.egyptPreferences
    private val keyUserName = stringPreferencesKey("user_name")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keySafetyConsent = booleanPreferencesKey("safety_consent_accepted")
    private val keySeenMessageIds = stringSetPreferencesKey("seen_message_ids")

    val userName: Flow<String?> = store.data.map { it[keyUserName] }
    val userId: Flow<String?> = store.data.map { it[keyUserId] }

    suspend fun saveUser(name: String): String {
        val id = UUID.randomUUID().toString()
        store.edit {
            it[keyUserName] = name.trim()
            it[keyUserId] = id
        }
        return id
    }

    suspend fun setSafetyConsentAccepted(accepted: Boolean) {
        store.edit { it[keySafetyConsent] = accepted }
    }

    suspend fun getSeenMessageIds(): Set<String> =
        store.data.first()[keySeenMessageIds] ?: emptySet()

    suspend fun addSeenMessageIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        store.edit { prefs ->
            val current = prefs[keySeenMessageIds]?.toMutableSet() ?: mutableSetOf()
            current.addAll(ids.filter { it.isNotBlank() })
            if (current.size > 800) {
                prefs[keySeenMessageIds] = current.drop(current.size - 800).toSet()
            } else {
                prefs[keySeenMessageIds] = current
            }
        }
    }
}
