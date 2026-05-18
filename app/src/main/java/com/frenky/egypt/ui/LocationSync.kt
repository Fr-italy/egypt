package com.frenky.egypt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.location.LocationHelper
import kotlinx.coroutines.delay

/** Invia la posizione GPS al server ogni 30 s (tutti gli utenti registrati). */
@Composable
fun LocationSync(userId: String, userName: String) {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }

    LaunchedEffect(userId, userName) {
        while (true) {
            if (locationHelper.hasPermission()) {
                val pos = locationHelper.getCurrentPosition()
                if (pos != null) {
                    EgyptApi.updateLocation(userId, userName, pos.latitude, pos.longitude)
                }
            }
            delay(30_000)
        }
    }
}
