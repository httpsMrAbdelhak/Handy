package com.handymobile.app.whisper

/** JNI bridge to libhandywhisper.so (whisper.cpp, CPU only). */
internal object WhisperLib {
    init {
        System.loadLibrary("handywhisper")
    }

    external fun nativeInit(modelPath: String): Long
    external fun nativeFree(ptr: Long)
    external fun nativeTranscribe(ptr: Long, threads: Int, language: String, samples: FloatArray): ByteArray?
}

/** Not thread-safe: call from a single thread (the ViewModel uses a dedicated one). */
class WhisperEngine {
    private var ctx = 0L

    fun load(modelPath: String) {
        close()
        ctx = WhisperLib.nativeInit(modelPath)
        check(ctx != 0L) { "whisper_init failed (corrupt model or not enough memory)" }
    }

    fun transcribe(samples: FloatArray, language: String, threads: Int): String {
        check(ctx != 0L) { "model not loaded" }
        val bytes = WhisperLib.nativeTranscribe(ctx, threads, language, samples)
            ?: error("whisper_full failed")
        return String(bytes, Charsets.UTF_8).trim()
    }

    fun close() {
        if (ctx != 0L) {
            WhisperLib.nativeFree(ctx)
            ctx = 0L
        }
    }
}
