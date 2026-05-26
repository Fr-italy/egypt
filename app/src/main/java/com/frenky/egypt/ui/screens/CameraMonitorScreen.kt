package com.frenky.egypt.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frenky.egypt.camera.FeedFreshness
import com.frenky.egypt.camera.RemoteAudioPlayer
import com.frenky.egypt.camera.cameraFeedStatus
import com.frenky.egypt.data.EgyptApi
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

private enum class CamSection { Live, Gallery, Archive }

@Composable
fun CameraMonitorScreen(
    modifier: Modifier = Modifier,
    moderatorName: String,
) {
    var section by remember { mutableIntStateOf(0) }
    val sections = CamSection.entries

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section) {
            sections.forEachIndexed { index, s ->
                Tab(
                    selected = section == index,
                    onClick = { section = index },
                    text = {
                        Text(
                            when (s) {
                                CamSection.Live -> "Live"
                                CamSection.Gallery -> "Galleria"
                                CamSection.Archive -> "Archivio"
                            },
                        )
                    },
                )
            }
        }
        when (sections[section]) {
            CamSection.Live -> LiveCameraSection(Modifier.fillMaxSize(), moderatorName)
            CamSection.Gallery -> GallerySection(Modifier.fillMaxSize(), moderatorName)
            CamSection.Archive -> ArchiveSection(Modifier.fillMaxSize(), moderatorName)
        }
    }
}

@Composable
private fun LiveCameraSection(modifier: Modifier, moderatorName: String) {
    var feeds by remember { mutableStateOf<List<EgyptApi.CameraFeed>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<EgyptApi.CameraFeed?>(null) }

    LaunchedEffect(moderatorName, selected) {
        while (true) {
            if (selected == null) loading = feeds.isEmpty()
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
            delay(if (selected == null) 5_000 else 2_000)
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
private fun GallerySection(modifier: Modifier, moderatorName: String) {
    var users by remember { mutableStateOf<List<EgyptApi.GalleryUser>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<EgyptApi.GalleryUser?>(null) }

    LaunchedEffect(moderatorName, selected) {
        while (true) {
            if (selected == null) {
                EgyptApi.getGalleryUsers(moderatorName)
                    .onSuccess { r ->
                        if (r.ok) {
                            users = r.gallery_users
                            status = null
                        } else {
                            status = r.error
                        }
                    }
                    .onFailure { status = it.message }
                loading = false
            }
            delay(if (selected == null) 20_000 else 5_000)
        }
    }

    val person = selected
    if (person != null) {
        GalleryGridView(
            modifier = modifier,
            moderatorName = moderatorName,
            user = person,
            onBack = { selected = null },
        )
    } else {
        Column(modifier.padding(16.dp)) {
            Text("Galleria telefoni", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Foto sincronizzate dal telefono del gruppo (backup su fr-italy). " +
                    "Serve permesso Galleria sul telefono del bimbo; sync ogni ~45 s.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            status?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (loading && users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (users.isEmpty()) {
                Text("Nessuna foto in galleria ancora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users, key = { it.user_id }) { u ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = u },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("${u.photo_count} foto", style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Apri galleria")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryGridView(
    modifier: Modifier,
    moderatorName: String,
    user: EgyptApi.GalleryUser,
    onBack: () -> Unit,
) {
    var items by remember(user.user_id) { mutableStateOf<List<EgyptApi.GalleryItem>>(emptyList()) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var previewBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(user.user_id, moderatorName) {
        while (true) {
            EgyptApi.getGalleryItems(moderatorName, user.user_id)
                .onSuccess { r -> if (r.ok) items = r.gallery_items }
            loading = false
            delay(15_000)
        }
    }

    LaunchedEffect(previewId) {
        val id = previewId ?: return@LaunchedEffect
        EgyptApi.getGalleryImage(moderatorName, user.user_id, id)
            .onSuccess { r ->
                val b64 = r.gallery_image?.image_base64.orEmpty()
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    previewBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
    }

    if (previewId != null && previewBmp != null) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(user.name) },
                    navigationIcon = {
                        IconButton(onClick = { previewId = null; previewBmp = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                )
            },
        ) { padding ->
            Image(
                bitmap = previewBmp!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text("${user.name} — galleria") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                )
            },
        ) { padding ->
            if (loading && items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items.filter { it.has_file }, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { previewId = item.id; previewBmp = null },
                        ) {
                            GalleryThumb(
                                moderatorName = moderatorName,
                                userId = user.user_id,
                                photoId = item.id,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryThumb(moderatorName: String, userId: String, photoId: String) {
    var bmp by remember(photoId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photoId) {
        EgyptApi.getGalleryImage(moderatorName, userId, photoId)
            .onSuccess { r ->
                val b64 = r.gallery_image?.image_base64.orEmpty()
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
    }
    bmp?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun ArchiveSection(modifier: Modifier, moderatorName: String) {
    var feeds by remember { mutableStateOf<List<EgyptApi.CameraFeed>>(emptyList()) }
    var selected by remember { mutableStateOf<EgyptApi.CameraFeed?>(null) }

    LaunchedEffect(moderatorName) {
        while (true) {
            EgyptApi.getCameraFeeds(moderatorName).onSuccess { r ->
                if (r.ok) feeds = r.camera_feeds
            }
            delay(30_000)
        }
    }

    val person = selected
    if (person != null) {
        ArchiveDetailView(modifier, moderatorName, person, onBack = { selected = null })
    } else {
        Column(modifier.padding(16.dp)) {
            Text("Archivio server", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Backup di ogni fotogramma e clip audio inviati (ultimi ~150 per tipo).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(feeds, key = { it.user_id }) { feed ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = feed },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(feed.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.History, contentDescription = "Archivio")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveDetailView(
    modifier: Modifier,
    moderatorName: String,
    feed: EgyptApi.CameraFeed,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val audioPlayer = remember { RemoteAudioPlayer(context) }
    var history by remember(feed.user_id) { mutableStateOf<List<EgyptApi.CameraHistoryItem>>(emptyList()) }
    var viewer by remember { mutableStateOf<EgyptApi.CameraHistoryItem?>(null) }
    var viewerData by remember { mutableStateOf<EgyptApi.CameraHistoryItem?>(null) }

    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }

    LaunchedEffect(feed.user_id) {
        EgyptApi.getCameraHistory(moderatorName, feed.user_id)
            .onSuccess { r -> if (r.ok) history = r.camera_history }
    }

    LaunchedEffect(viewer?.id) {
        val v = viewer ?: return@LaunchedEffect
        EgyptApi.getCameraHistoryItem(moderatorName, feed.user_id, v.id)
            .onSuccess { r -> viewerData = r.camera_history_item }
    }

    LaunchedEffect(viewerData) {
        val item = viewerData ?: return@LaunchedEffect
        if (item.type == "audio" && item.data_base64.isNotBlank()) {
            audioPlayer.playBase64(item.data_base64, item.id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("${feed.name} — archivio") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewer != null) {
                            viewer = null
                            viewerData = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        if (viewer != null && viewerData?.type == "image") {
            val b64 = viewerData?.data_base64.orEmpty()
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(history, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewer = item; viewerData = null },
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (item.type == "image") "📷 Foto" else "🔊 Audio",
                                fontWeight = FontWeight.Medium,
                            )
                            Text(item.updated_at, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
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
        Text("CAM — live", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Live a scatti (~3–4 s, non video continuo). Verde = attivo: " +
                "lascia Egypt in background sul telefono del bimbo e disattiva risparmio batteria per l'app.",
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
                    val hasSignal = feed.has_frame || feed.has_audio
                    val statusInfo = cameraFeedStatus(feed.updated_at, hasSignal)
                    val statusColor = when (statusInfo.freshness) {
                        FeedFreshness.Live -> Color(0xFF4CAF50)
                        FeedFreshness.Recent -> MaterialTheme.colorScheme.primary
                        FeedFreshness.Stale -> MaterialTheme.colorScheme.error
                        FeedFreshness.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
                                    statusInfo.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = "Apri fotocamera",
                                tint = statusColor,
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

    LaunchedEffect(frame?.audio_base64, frame?.updated_at, audioOn) {
        if (audioOn) {
            val audio = frame?.audio_base64 ?: return@LaunchedEffect
            val token = frame?.updated_at ?: audio
            audioPlayer.playBase64(audio, token)
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
            delay(2_000)
        }
    }

    val imageKey = frame?.let { "${it.updated_at}_${it.image_base64.length}" } ?: "none"

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(feed.name, fontWeight = FontWeight.Bold)
                        Text(
                            "LIVE · aggiornamento ~3 s",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
                    val bytes = Base64.decode(frame!!.image_base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bitmap?.let {
                        androidx.compose.runtime.key(imageKey) {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = feed.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
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
