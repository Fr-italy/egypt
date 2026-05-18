package com.frenky.egypt.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frenky.egypt.R
import com.frenky.egypt.data.ConfigRepository
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.data.PreferencesRepository
import com.frenky.egypt.ui.components.CopyrightFooter
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    preferences: PreferencesRepository,
    configRepository: ConfigRepository,
) {
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        configRepository.refreshFromServer()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Logo Egypt",
            modifier = Modifier.size(140.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(16.dp))
        Text("Benvenuto in Egypt", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Inserisci il tuo nome per messaggi e checklist condivisa.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Il tuo nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
                    loading = true
                    scope.launch {
                        val userId = preferences.saveUser(trimmed)
                        EgyptApi.register(userId, trimmed)
                            .onSuccess { r ->
                                r.config?.let { configRepository.save(it) }
                            }
                            .onFailure {
                                error = "Salvato in locale. Server: ${it.message}"
                            }
                        loading = false
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continua")
            }
        }
        Spacer(Modifier.height(24.dp))
        CopyrightFooter()
    }
}
