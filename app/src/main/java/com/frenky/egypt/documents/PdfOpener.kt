package com.frenky.egypt.documents

import android.app.Activity
import android.content.ClipData
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
            item.file.isNotBlank() -> {
                val file = prepareAssetPdf(context, item.file)
                withContext(Dispatchers.Main) {
                    openWithSystemPdfViewer(context, file, item.title)
                }
            }
            item.url.isNotBlank() -> openUrl(context, item.url, item.title)
            else -> Toast.makeText(context, "Documento non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun openUrl(context: Context, url: String, title: String) = withContext(Dispatchers.IO) {
        runCatching {
            val name = url.substringAfterLast('/').ifBlank { "document.pdf" }
            val file = pdfFileFor(context, "remote_$name")
            if (!file.exists() || file.length() == 0L) {
                val request = Request.Builder().url(url).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Risposta vuota")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
            }
            withContext(Dispatchers.Main) {
                openWithSystemPdfViewer(context, file, title)
            }
        }.onFailure {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download fallito: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun prepareAssetPdf(context: Context, assetRelativePath: String): File =
        withContext(Dispatchers.IO) {
            val file = pdfFileFor(context, assetRelativePath.replace('/', '_'))
            if (!file.exists() || file.length() == 0L) {
                context.assets.open("documents/$assetRelativePath").use { input ->
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
            file
        }

    private fun pdfFileFor(context: Context, name: String): File {
        val safeName = if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
        val dir = context.getExternalFilesDir("pdfs") ?: File(context.cacheDir, "pdfs")
        dir.mkdirs()
        return File(dir, safeName)
    }

    private fun openWithSystemPdfViewer(context: Context, file: File, title: String) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "File PDF non trovato", Toast.LENGTH_LONG).show()
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "pdf", uri)
        }

        val handlers = context.packageManager.queryIntentActivities(
            viewIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )

        if (handlers.isEmpty()) {
            val generic = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "pdf", uri)
            }
            if (generic.resolveActivity(context.packageManager) == null) {
                Toast.makeText(
                    context,
                    "Installa un lettore PDF (Adobe, Google PDF Viewer, Samsung Notes…)",
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            launchChooser(context, generic, title, uri)
            return
        }

        handlers.forEach { info ->
            context.grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        launchChooser(context, viewIntent, title, uri)
    }

    private fun launchChooser(context: Context, intent: Intent, title: String, uri: Uri) {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "pdf", uri)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "Impossibile aprire il PDF: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
