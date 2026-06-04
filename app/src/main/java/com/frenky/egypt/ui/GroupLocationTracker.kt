package com.frenky.egypt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.frenky.egypt.data.EgyptApi
import kotlinx.coroutines.delay

/** Posizioni del gruppo (tutti vedono tutti, escluso sé stessi sulla mappa). */
@Composable
fun rememberGroupLocations(
    userId: String,
    userName: String,
): List<EgyptApi.UserLocation> {
    var locations by remember { mutableStateOf(emptyList<EgyptApi.UserLocation>()) }

    LaunchedEffect(userId, userName) {
        while (true) {
            EgyptApi.getGroupLocations(userId, userName)
                .onSuccess { r ->
                    if (r.ok) {
                        locations = r.locations.filter { it.user_id != userId }
                    }
                }
            delay(15_000)
        }
    }

    return locations
}
