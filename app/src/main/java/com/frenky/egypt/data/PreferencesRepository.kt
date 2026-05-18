package com.frenky.egypt.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PreferencesRepository(private val context: Context) {
    private val store = context.egyptPreferences
    private val keyUserName = stringPreferencesKey("user_name")
    private val keyUserId = stringPreferencesKey("user_id")

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
}
