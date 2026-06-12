package com.example.cachefileloader

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*

/**
 * AudioRecorderManager
 *
 * Wraps AudioRecord to capture raw PCM from the microphone and
 * emit a normalised amplitude (0.0 – 1.0) on a background coroutine.
 * The amplitude callback is NOT guaranteed on the main thread —
 * callers should dispatch to UI thread themselves.
 */
class AudioRecorderManager {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_AMPLITUDE = 32767f   // max value for 16-bit PCM
    }

    /**
     * Start recording from the mic.
     * [onAmplitude] receives a Float in [0, 1] roughly every 40 ms.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(onAmplitude: (Float) -> Unit) {
        if (audioRecord != null) return

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        ).also { ar ->
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                audioRecord = null
                return
            }
            ar.startRecording()
        }

        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize)
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val rms = calculateRMS(buffer, read)
                    val normalised = (rms / MAX_AMPLITUDE).coerceIn(0f, 1f)
                    // Apply slight amplification so quiet voices still show movement
                    val boosted = (normalised * 4f).coerceIn(0f, 1f)
                    onAmplitude(boosted)
                }
                delay(40)
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }

    fun release() {
        stopRecording()
        scope.cancel()
    }

    private fun calculateRMS(buffer: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) {
            sum += buffer[i] * buffer[i].toDouble()
        }
        return sqrt(sum / count)
    }

    private fun sqrt(value: Double): Float = kotlin.math.sqrt(value).toFloat()
}
