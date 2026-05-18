package com.frenky.egypt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.egyptPreferences: DataStore<Preferences> by preferencesDataStore(name = "egypt_prefs")
