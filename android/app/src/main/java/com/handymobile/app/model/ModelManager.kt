package com.handymobile.app.model

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The single model shipped in phase 1: multilingual Whisper Tiny (ggml, from the whisper.cpp repo). */
object ModelSpec {
    const val FILE_NAME = "ggml-tiny.bin"
    const val URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    const val SIZE_BYTES = 77_691_713L
    const val SHA256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"
}

class ModelException(val kind: Kind, val detail: String? = null, cause: Throwable? = null) :
    Exception(detail, cause) {
    enum class Kind { NO_SPACE, NETWORK, HTTP, CORRUPT }
}

class ModelManager(private val context: Context) {
    private val dir get() = File(context.filesDir, "models")
    val modelFile get() = File(dir, ModelSpec.FILE_NAME)
    private val partFile get() = File(dir, ModelSpec.FILE_NAME + ".part")

    fun isInstalled() = modelFile.isFile && modelFile.length() == ModelSpec.SIZE_BYTES

    fun delete() {
        modelFile.delete()
        partFile.delete()
    }

    /** Downloads (resuming a partial file), verifies size and SHA-256, then installs atomically. */
    suspend fun download(onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        var have = if (partFile.isFile) partFile.length() else 0L
        if (have > ModelSpec.SIZE_BYTES) {
            partFile.delete()
            have = 0L
        }
        val free = StatFs(dir.path).availableBytes
        if (free < ModelSpec.SIZE_BYTES - have + 20_000_000L) {
            throw ModelException(ModelException.Kind.NO_SPACE)
        }
        if (have < ModelSpec.SIZE_BYTES) fetch(have, onProgress)
        if (partFile.length() != ModelSpec.SIZE_BYTES) {
            throw ModelException(ModelException.Kind.NETWORK, "incomplete download")
        }
        if (sha256(partFile) != ModelSpec.SHA256) {
            partFile.delete()
            throw ModelException(ModelException.Kind.CORRUPT)
        }
        modelFile.delete()
        if (!partFile.renameTo(modelFile)) {
            throw ModelException(ModelException.Kind.NETWORK, "cannot move model into place")
        }
    }

    private suspend fun fetch(have: Long, onProgress: (Long) -> Unit) {
        val conn = URL(ModelSpec.URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            val code = conn.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_OK -> false
                HttpURLConnection.HTTP_PARTIAL -> true
                else -> throw ModelException(ModelException.Kind.HTTP, code.toString())
            }
            var total = if (append) have else 0L
            val buf = ByteArray(64 * 1024)
            var lastReport = 0L
            FileOutputStream(partFile, append).use { out ->
                conn.inputStream.use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        if (total - lastReport >= 256 * 1024) {
                            lastReport = total
                            onProgress(total)
                        }
                    }
                }
            }
            onProgress(total)
        } catch (e: IOException) {
            val noSpace = e.message?.contains("ENOSPC") == true
            throw ModelException(
                if (noSpace) ModelException.Kind.NO_SPACE else ModelException.Kind.NETWORK,
                e.message, e,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
