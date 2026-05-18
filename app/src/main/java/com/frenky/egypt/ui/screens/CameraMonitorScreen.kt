package com.frenky.egypt.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frenky.egypt.camera.RemoteAudioPlayer
import com.frenky.egypt.data.EgyptApi
import kotlinx.coroutines.delay

@Composable
fun CameraMonitorScreen(
    modifier: Modifier = Modifier,
    moderatorName: String,
) {
    var feeds by remember { mutableStateOf<List<EgyptApi.CameraFeed>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<EgyptApi.CameraFeed?>(null) }

    LaunchedEffect(moderatorName, selected) {
        while (true) {
            if (selected == null) {
                loading = feeds.isEmpty()
            }
            EgyptApi.getCameraFeeds(moderatorName)
                .onSuccess { r ->
                    if (r.ok) {
                        feeds = r.camera_feeds
                        status = null
                        selected?.let { sel ->
                            r.camera_feeds.find { it.user_id == sel.user_id }?.let { selected = it }
                        }
                    } else {
                        status = r.error
                    }
                }
                .onFailure { status = it.message }
            loading = false
            delay(if (selected == null) 12_000 else 3_000)
        }
    }

    val person = selected
    if (person != null) {
        LiveCameraView(
            modifier = modifier,
            moderatorName = moderatorName,
            feed = person,
            onBack = { selected = null },
        )
    } else {
        CameraNameList(
            modifier = modifier,
            feeds = feeds,
            loading = loading,
            status = status,
            onSelect = { selected = it },
        )
    }
}

@Composable
private fun CameraNameList(
    modifier: Modifier,
    feeds: List<EgyptApi.CameraFeed>,
    loading: Boolean,
    status: String?,
    onSelect: (EgyptApi.CameraFeed) -> Unit,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("CAM — gruppo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tocca un nome per aprire la fotocamera",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        status?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (loading && feeds.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (feeds.isEmpty()) {
            Text(
                "Nessun telefono attivo. I membri devono aver installato l'app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(feeds, key = { it.user_id }) { feed ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(feed) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    feed.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    when {
                                        feed.has_frame && feed.has_audio -> "Video + audio live"
                                        feed.has_frame -> "Video live"
                                        feed.has_audio -> "Audio live"
                                        else -> "In attesa segnale…"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = "Apri fotocamera",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveCameraView(
    modifier: Modifier,
    moderatorName: String,
    feed: EgyptApi.CameraFeed,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val audioPlayer = remember { RemoteAudioPlayer(context) }
    var frame by remember(feed.user_id) { mutableStateOf<EgyptApi.CameraFrame?>(null) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var audioOn by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }

    LaunchedEffect(frame?.audio_base64, audioOn) {
        if (audioOn) {
            frame?.audio_base64?.let { audioPlayer.playBase64(it) }
        }
    }

    LaunchedEffect(feed.user_id, moderatorName) {
        while (true) {
            EgyptApi.getCameraImage(moderatorName, feed.user_id)
                .onSuccess { r ->
                    if (r.ok && r.camera_frame != null) {
                        frame = r.camera_frame
                        status = null
                    } else {
                        status = r.error ?: "Nessuna immagine"
                    }
                    loading = false
                }
                .onFailure {
                    status = it.message
                    loading = false
                }
            delay(3_000)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(feed.name, fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                append(frame?.updated_at?.ifBlank { feed.updated_at } ?: feed.updated_at)
                                if (frame?.has_audio == true) append(" · audio")
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { audioOn = !audioOn }) {
                        Text(if (audioOn) "🔊" else "🔇")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading && frame == null -> CircularProgressIndicator()
                frame?.image_base64?.isNotBlank() == true -> {
                    val bytes = remember(frame!!.image_base64) {
                        Base64.decode(frame!!.image_base64, Base64.DEFAULT)
                    }
                    val bitmap = remember(bytes) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = feed.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                else -> Text(
                    status ?: "In attesa fotocamera…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
