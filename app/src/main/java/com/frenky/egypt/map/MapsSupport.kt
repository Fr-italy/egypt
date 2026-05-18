package com.frenky.egypt.map

import android.content.Context
import com.frenky.egypt.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.MapsInitializer

object MapsSupport {
    fun hasApiKey(): Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()

    fun canUseGoogleMaps(context: Context): Boolean {
        if (!hasApiKey()) return false
        val availability = GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(context)
        if (status != ConnectionResult.SUCCESS) return false
        return runCatching {
            @Suppress("DEPRECATION")
            MapsInitializer.initialize(context.applicationContext)
            true
        }.getOrDefault(false)
    }
}
