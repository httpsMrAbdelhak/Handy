package com.handymobile.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Records 16 kHz mono PCM16 from the microphone into memory (max [MAX_SECONDS]). */
class Recorder {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 60
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false
    private val buffer = ShortArray(SAMPLE_RATE * MAX_SECONDS)

    @Volatile
    private var length = 0

    /** Throws if the microphone cannot be opened. Callbacks run on the reader thread. */
    @SuppressLint("MissingPermission")
    fun start(onLimitReached: () -> Unit, onError: (Throwable) -> Unit) {
        check(record == null) { "already recording" }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuf > 0) { "unsupported audio parameters" }
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE * 2),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            error("microphone not initialized")
        }
        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            rec.release()
            throw e
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            error("microphone is busy")
        }
        length = 0
        running = true
        record = rec
        thread = Thread({
            val chunk = ShortArray(4096)
            while (running) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n < 0) {
                    running = false
                    onError(IllegalStateException("AudioRecord.read error $n"))
                    break
                }
                val room = buffer.size - length
                val take = minOf(n, room)
                System.arraycopy(chunk, 0, buffer, length, take)
                length += take
                if (take < n || length >= buffer.size) {
                    running = false
                    onLimitReached()
                    break
                }
            }
        }, "recorder").also { it.start() }
    }

    /** Stops recording and returns the samples as floats in [-1, 1]. */
    fun stop(): FloatArray {
        shutdown()
        val n = length
        return FloatArray(n) { buffer[it] / 32768f }
    }

    fun cancel() {
        shutdown()
    }

    private fun shutdown() {
        running = false
        thread?.join(2000)
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
    }
}
