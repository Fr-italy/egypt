package com.frenky.egypt.camera

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import java.io.File

class RemoteAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var lastToken: String = ""

    fun playBase64(audioBase64: String, token: String = audioBase64) {
        if (audioBase64.isBlank() || token == lastToken) return
        lastToken = token
        mediaPlayer?.release()
        mediaPlayer = null
        val file = File(context.cacheDir, "egypt_listen_${System.currentTimeMillis()}.m4a")
        runCatching {
            file.writeBytes(Base64.decode(audioBase64, Base64.DEFAULT))
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    if (mediaPlayer === it) mediaPlayer = null
                    file.delete()
                }
            }
        }.onFailure {
            file.delete()
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        lastToken = ""
    }
}
