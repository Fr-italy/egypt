package com.frenky.egypt.documents

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object PdfOpener {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun open(context: Context, item: DocumentItem) {
        when {
            item.file.isNotBlank() -> openAsset(context, item.file, item.title)
            item.url.isNotBlank() -> openUrl(context, item.url, item.title)
            else -> Toast.makeText(context, "Documento non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun openAsset(context: Context, assetRelativePath: String, title: String) {
        runCatching {
            PdfViewerActivity.openAsset(context, assetRelativePath, title)
        }.onFailure {
            Toast.makeText(context, "Errore apertura: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun openUrl(context: Context, url: String, title: String) = withContext(Dispatchers.IO) {
        runCatching {
            val name = url.substringAfterLast('/').ifBlank { "document.pdf" }
            val cacheFile = cacheFileFor(context, "remote_$name")
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                val request = Request.Builder().url(url).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Risposta vuota")
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
            }
            withContext(Dispatchers.Main) {
                PdfViewerActivity.openFile(context, cacheFile, title)
            }
        }.onFailure {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download fallito: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openExternalOptional(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val handlers = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        if (handlers.isEmpty()) return
        handlers.forEach { resolve ->
            context.grantUriPermission(
                resolve.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val chooser = Intent.createChooser(intent, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun cacheFileFor(context: Context, name: String) =
        File(context.cacheDir, "pdfs/$name")
}
