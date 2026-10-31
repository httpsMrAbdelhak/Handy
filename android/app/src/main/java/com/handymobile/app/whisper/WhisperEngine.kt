package com.handymobile.app.whisper

/** JNI bridge to libhandywhisper.so (whisper.cpp, CPU only). */
internal object WhisperLib {
    init {
        System.loadLibrary("handywhisper")
    }

    external fun nativeInit(modelPath: String, libDir: String): Long
    external fun nativeFree(ptr: Long)
    external fun nativeSystemInfo(): String
    external fun nativeTranscribe(
        ptr: Long, threads: Int, language: String, prompt: String, fast: Boolean, samples: FloatArray,
    ): ByteArray?
}

/** Not thread-safe: call from a single thread (the ViewModel uses a dedicated one). */
class WhisperEngine {
    private var ctx = 0L

    fun load(modelPath: String, libDir: String) {
        close()
        ctx = WhisperLib.nativeInit(modelPath, libDir)
        check(ctx != 0L) { "whisper_init failed (corrupt model, no CPU backend or not enough memory)" }
    }

    /** CPU features / backend variant picked by ggml; valid once a model is loaded. */
    fun systemInfo(): String = WhisperLib.nativeSystemInfo()

    fun transcribe(samples: FloatArray, language: String, prompt: String?, fast: Boolean, threads: Int): String {
        check(ctx != 0L) { "model not loaded" }
        val bytes = WhisperLib.nativeTranscribe(ctx, threads, language, prompt.orEmpty(), fast, samples)
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
