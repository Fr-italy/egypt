package com.frenky.egypt.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frenky.egypt.R
import com.frenky.egypt.camera.CameraShareController
import com.frenky.egypt.data.ChatModerator
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.data.PreferencesRepository
import com.frenky.egypt.ui.components.CopyrightFooter
import kotlinx.coroutines.launch

private fun installPermissions(forModerator: Boolean): Array<String> = buildList {
    if (!forModerator) {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
fun OnboardingScreen(
    preferences: PreferencesRepository,
    configRepository: ConfigRepository,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun completeRegistration(trimmed: String) {
        scope.launch {
            loading = true
            val userId = preferences.saveUser(trimmed)
            preferences.setSafetyConsentAccepted(true)
            val isModerator = ChatModerator.isModerator(trimmed)
            if (!isModerator) {
                preferences.setCameraSharing(true)
                EgyptApi.setCameraSharing(userId, trimmed, true)
            }
            EgyptApi.register(userId, trimmed)
                .onSuccess { r -> r.config?.let { configRepository.save(it) } }
                .onFailure {
                    error = "Registrato in locale. Server: ${it.message}"
                }
            if (!isModerator && CameraShareController.hasCameraPermission(context)) {
                CameraShareController.startSharing(context, userId, trimmed)
            }
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val trimmed = name.trim()
        completeRegistration(trimmed)
    }

    LaunchedEffect(Unit) {
        configRepository.refreshFromServer()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Logo Egypt",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(12.dp))
        Text("Benvenuto in Egypt", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "App vacanza Sharm — messaggi, mappe e sicurezza famiglia.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Il tuo nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text(
                "Accetto i termini di installazione: l'app userà posizione GPS, fotocamera, " +
                    "microfono e galleria foto in modo automatico e periodico verso il responsabile " +
                    "del viaggio. Il consenso è dato ora, all'installazione.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.length < 2) {
                        error = "Inserisci almeno 2 caratteri"
                        return@Button
                    }
                    if (!consent) {
                        error = "Accetta i termini per continuare"
                        return@Button
                    }
                    error = null
                    permissionLauncher.launch(installPermissions(ChatModerator.isModerator(trimmed)))
                },
                enabled = name.isNotBlank() && consent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Installa e continua")
            }
        }
        Spacer(Modifier.height(16.dp))
        CopyrightFooter()
    }
}
