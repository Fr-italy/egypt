package com.frenky.egypt.documents

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.frenky.egypt.ui.theme.EgyptTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val asset = intent.getStringExtra(EXTRA_ASSET)
        val filePath = intent.getStringExtra(EXTRA_FILE)

        setContent {
            EgyptTheme {
                PdfViewerScreen(
                    title = title.ifBlank { "Documento" },
                    loadPages = { loadBitmaps(asset, filePath) },
                )
            }
        }
    }

    private fun loadBitmaps(asset: String?, filePath: String?): List<Bitmap> {
        val file = when {
            !filePath.isNullOrBlank() -> File(filePath)
            !asset.isNullOrBlank() -> {
                val out = File(cacheDir, "pdfs/view_${asset.replace('/', '_')}")
                if (!out.exists() || out.length() == 0L) {
                    out.parentFile?.mkdirs()
                    assets.open("documents/$asset").use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                out
            }
            else -> return emptyList()
        }
        if (!file.exists()) return emptyList()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        return buildList {
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    add(bitmap)
                }
            }
            renderer.close()
            pfd.close()
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ASSET = "asset"
        private const val EXTRA_FILE = "file"

        fun openAsset(context: Context, assetRelativePath: String, title: String) {
            context.startActivity(intent(context, title, assetRelativePath, null))
        }

        fun openFile(context: Context, file: File, title: String) {
            context.startActivity(intent(context, title, null, file.absolutePath))
        }

        private fun intent(context: Context, title: String, asset: String?, file: String?): Intent =
            Intent(context, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ASSET, asset)
                putExtra(EXTRA_FILE, file)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewerScreen(title: String, loadPages: () -> List<Bitmap>) {
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { loadPages() }
                .onSuccess { pages = it }
                .onFailure { error = it.message }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { padding ->
        when {
            error != null -> Text("Errore: $error", Modifier.padding(padding).padding(16.dp))
            pages == null -> CircularProgressIndicator(
                Modifier.fillMaxSize().padding(padding),
            )
            pages!!.isEmpty() -> Text("PDF vuoto", Modifier.padding(padding).padding(16.dp))
            else -> LazyColumn(Modifier.padding(padding)) {
                itemsIndexed(pages!!) { index, bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Pagina ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}
