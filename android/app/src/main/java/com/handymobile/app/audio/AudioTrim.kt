package com.handymobile.app.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Cheap energy-based silence trimming: shorter input = less work and fewer hallucinations. */
object AudioTrim {
    private const val FRAME = 320 // 20 ms at 16 kHz
    private const val MIN_SPEECH_FRAMES = 5
    private const val MARGIN_FRAMES = 15 // 300 ms kept around speech

    /** Returns the trimmed samples, or null when no speech-like energy was found. */
    fun trim(samples: FloatArray): FloatArray? {
        val frames = samples.size / FRAME
        if (frames < MIN_SPEECH_FRAMES) return null
        val rms = FloatArray(frames) { f ->
            var sum = 0f
            for (i in f * FRAME until (f + 1) * FRAME) sum += samples[i] * samples[i]
            sqrt(sum / FRAME)
        }
        val floor = rms.sortedArray()[frames / 5] // ~20th percentile = background noise
        val threshold = max(0.006f, floor * 2.5f)
        var first = -1
        var last = -1
        var count = 0
        for (f in 0 until frames) {
            if (rms[f] > threshold) {
                if (first < 0) first = f
                last = f
                count++
            }
        }
        if (count < MIN_SPEECH_FRAMES) return null
        val from = max(0, first - MARGIN_FRAMES) * FRAME
        val to = min(frames, last + 1 + MARGIN_FRAMES) * FRAME
        return samples.copyOfRange(from, to)
    }
}
