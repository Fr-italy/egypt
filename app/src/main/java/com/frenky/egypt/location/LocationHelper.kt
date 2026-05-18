package com.frenky.egypt.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class GpsPosition(val latitude: Double, val longitude: Double)

class LocationHelper(private val context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun locationUpdates(): Flow<GpsPosition> = callbackFlow {
        if (!hasPermission()) {
            close(IllegalStateException("Permesso posizione non concesso"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    trySend(GpsPosition(it.latitude, it.longitude))
                }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        fused.lastLocation.addOnSuccessListener { loc ->
            loc?.let { trySend(GpsPosition(it.latitude, it.longitude)) }
        }
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}
