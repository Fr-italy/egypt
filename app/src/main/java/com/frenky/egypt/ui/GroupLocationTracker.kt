package com.frenky.egypt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.frenky.egypt.data.ChatModerator
import com.frenky.egypt.data.EgyptApi

/** Per Frenk: aggiorna le posizioni del gruppo ogni 15 s. */
@Composable
fun rememberGroupLocations(
    userId: String,
    userName: String,
    enabled: Boolean = true,
): List<EgyptApi.UserLocation> {
    var locations by remember { mutableStateOf(emptyList<EgyptApi.UserLocation>()) }
    val isModerator = remember(userName) { ChatModerator.isModerator(userName) }

    LaunchedEffect(userId, userName, enabled, isModerator) {
        if (!enabled || !isModerator) {
            locations = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            EgyptApi.getGroupLocations(userName)
                .onSuccess { r ->
                    if (r.ok) {
                        locations = r.locations.filter { it.user_id != userId }
                    }
                }
            kotlinx.coroutines.delay(15_000)
        }
    }

    return locations
}
