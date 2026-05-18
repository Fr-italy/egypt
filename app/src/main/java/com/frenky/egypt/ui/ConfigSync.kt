package com.frenky.egypt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.EgyptApi
import kotlinx.coroutines.delay

@Composable
fun ConfigSync(
    configRepository: ConfigRepository,
    userId: String? = null,
    userName: String? = null,
) {
    LaunchedEffect(userId, userName) {
        while (true) {
            configRepository.refreshFromServer()
                .onSuccess { cfg ->
                    if (userId != null && userName != null) {
                        EgyptApi.heartbeat(userId, userName)
                            .onSuccess { r -> r.config?.let { configRepository.save(it) } }
                    }
                }
            delay(60_000)
        }
    }
}
