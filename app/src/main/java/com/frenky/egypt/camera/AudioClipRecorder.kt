package com.frenky.egypt.camera

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object AudioClipRecorder {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun recordClip(
        context: Context,
        outputFile: File,
        durationMs: Long = 4_500L,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext false
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(22_050)
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
            delay(durationMs)
            recorder.stop()
            recorder.release()
            outputFile.exists() && outputFile.length() > 100
        }.getOrElse {
            runCatching { recorder.release() }
            false
        }
    }
}
