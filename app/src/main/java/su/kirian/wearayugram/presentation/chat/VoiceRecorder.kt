package su.kirian.wearayugram.presentation.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var startTime = 0L
    private var outputFile: File? = null

    fun start(): File {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
        outputFile = file
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioSamplingRate(48000)
            setAudioEncodingBitRate(32000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        startTime = System.currentTimeMillis()
        return file
    }

    // Returns duration in seconds, releases recorder
    fun stop(): Int {
        val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        return duration
    }

    fun cancel() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    val isRecording: Boolean get() = recorder != null
}
