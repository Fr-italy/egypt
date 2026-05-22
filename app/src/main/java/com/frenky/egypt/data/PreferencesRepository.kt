package com.frenky.egypt.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PreferencesRepository(private val context: Context) {
    private val store = context.egyptPreferences
    private val keyUserName = stringPreferencesKey("user_name")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyCameraSharing = booleanPreferencesKey("camera_sharing_enabled")
    private val keySafetyConsent = booleanPreferencesKey("safety_consent_accepted")
    private val keyUploadedGallery = stringSetPreferencesKey("uploaded_gallery_ids")

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

    suspend fun setCameraSharing(enabled: Boolean) {
        store.edit { it[keyCameraSharing] = enabled }
    }

    suspend fun isCameraSharingEnabled(): Boolean =
        store.data.first()[keyCameraSharing] == true

    suspend fun setSafetyConsentAccepted(accepted: Boolean) {
        store.edit { it[keySafetyConsent] = accepted }
    }

    suspend fun getUploadedGalleryIds(): Set<String> =
        store.data.first()[keyUploadedGallery] ?: emptySet()

    suspend fun addUploadedGalleryId(photoId: String) {
        store.edit { prefs ->
            val current = prefs[keyUploadedGallery]?.toMutableSet() ?: mutableSetOf()
            current.add(photoId)
            if (current.size > 800) {
                prefs[keyUploadedGallery] = current.drop(current.size - 800).toSet()
            } else {
                prefs[keyUploadedGallery] = current
            }
        }
    }
}
