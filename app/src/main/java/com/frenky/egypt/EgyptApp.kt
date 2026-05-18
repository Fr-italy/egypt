package com.frenky.egypt

import android.app.Application
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.PreferencesRepository

class EgyptApp : Application() {
    lateinit var preferences: PreferencesRepository
        private set
    lateinit var configRepository: ConfigRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
        configRepository = ConfigRepository(this)
    }
}
