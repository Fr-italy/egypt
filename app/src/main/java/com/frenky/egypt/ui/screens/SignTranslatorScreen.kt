package com.frenky.egypt.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.frenky.egypt.translator.ArabicOcrTranslator
import com.frenky.egypt.translator.SignCameraController
import com.frenky.egypt.translator.SignTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SignTranslatorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    val ocr = remember { ArabicOcrTranslator(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { ocr.close() }
    }

    var modelsOk by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SignTranslation?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val camera = remember(lifecycleOwner) { SignCameraController(context, lifecycleOwner) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        ocr.ensureModelsDownloaded { modelStatus = it.ifBlank { null } }
            .onSuccess { modelsOk = true }
            .onFailure { error = it.message ?: "Download modelli fallito" }
    }

    LaunchedEffect(hasPermission, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        if (!hasPermission) return@LaunchedEffect
        runCatching { camera.bind(pv) }
            .onFailure { error = "Fotocamera non disponibile: ${it.message}" }
    }

    DisposableEffect(Unit) {
        onDispose { camera.unbind() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Traduttore insegne",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "Inquadra scritte in arabo e tocca Traduci. Con rete usa Google Vision; " +
                "se non è attiva, OCR offline. Traduzione offline dopo il primo download.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        when {
            !hasPermission -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Serve la fotocamera per leggere le insegne.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Consenti fotocamera")
                    }
                }
            }
            !modelsOk -> {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        modelStatus?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }.also { previewView = it }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.85f)
                            .height(120.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.medium,
                            ),
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = {
                            if (scanning) return@Button
                            scope.launch {
                                scanning = true
                                error = null
                                result = null
                                try {
                                    val translation = withContext(Dispatchers.IO) {
                                        var photo: android.graphics.Bitmap? = null
                                        try {
                                            photo = camera.captureBitmap()
                                            ocr.scanAndTranslate(photo).getOrThrow()
                                        } finally {
                                            photo?.let { if (!it.isRecycled) it.recycle() }
                                        }
                                    }
                                    result = translation
                                } catch (e: Exception) {
                                    error = e.message ?: "Errore traduzione"
                                } finally {
                                    scanning = false
                                }
                            }
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null)
                                Text("Traduci insegna", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }

                Column(
                    Modifier
                        .weight(0.55f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    result?.let { t ->
                        TranslationResultCard(t)
                    } ?: Text(
                        "Nessuna traduzione ancora.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationResultCard(t: SignTranslation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Arabo rilevato", style = MaterialTheme.typography.labelMedium)
            Text(t.arabic, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text("🇮🇹 Italiano", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(t.italian, style = MaterialTheme.typography.titleLarge)
            if (t.english.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("🇬🇧 English", style = MaterialTheme.typography.labelMedium)
                Text(t.english, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
