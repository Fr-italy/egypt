package com.frenky.egypt.translator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SignCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val executor: Executor = ContextCompat.getMainExecutor(context)
    private var imageCapture: ImageCapture? = null

    suspend fun bind(previewView: PreviewView) {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener({ cont.resume(future.get()) }, executor)
            }
        }
        provider.unbindAll()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
    }

    fun unbind() {
        runCatching {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
        imageCapture = null
    }

    suspend fun captureBitmap(): Bitmap = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resumeWithException(IllegalStateException("Fotocamera non pronta"))
            return@suspendCancellableCoroutine
        }
        val photoFile = File(context.cacheDir, "sign_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            output,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    photoFile.delete()
                    if (bitmap != null) {
                        cont.resume(bitmap)
                    } else {
                        cont.resumeWithException(IllegalStateException("Impossibile leggere la foto"))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    photoFile.delete()
                    cont.resumeWithException(exception)
                }
            },
        )
    }
}
