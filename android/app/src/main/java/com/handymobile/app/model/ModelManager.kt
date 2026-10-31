package com.handymobile.app.model

import android.content.Context
import android.os.StatFs
import androidx.annotation.StringRes
import com.handymobile.app.R
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

/** Multilingual Whisper models (ggml, from the whisper.cpp Hugging Face repo). */
enum class WhisperModel(
    val file: String,
    val sizeBytes: Long,
    val sha256: String,
    @StringRes val label: Int,
) {
    TINY("ggml-tiny.bin", 77_691_713L, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21", R.string.model_tiny),
    BASE_Q5("ggml-base-q5_1.bin", 59_707_625L, "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", R.string.model_base),
    ;

    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
    val sizeMb get() = (sizeBytes / 1_000_000L).toInt()
}

class ModelException(val kind: Kind, val detail: String? = null, cause: Throwable? = null) :
    Exception(detail, cause) {
    enum class Kind { NO_SPACE, NETWORK, HTTP, CORRUPT }
}

class ModelManager(private val context: Context) {
    private val dir get() = File(context.filesDir, "models")
    fun file(m: WhisperModel) = File(dir, m.file)
    private fun part(m: WhisperModel) = File(dir, m.file + ".part")

    fun isInstalled(m: WhisperModel) = file(m).isFile && file(m).length() == m.sizeBytes
    fun installed(): Set<WhisperModel> = WhisperModel.entries.filter { isInstalled(it) }.toSet()

    /** Only used to clear a model that fails to load (corrupt file). */
    fun delete(m: WhisperModel) {
        file(m).delete()
        part(m).delete()
    }

    /** Downloads (resuming a partial file), verifies size and SHA-256, then installs atomically. */
    suspend fun download(m: WhisperModel, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val part = part(m)
        var have = if (part.isFile) part.length() else 0L
        if (have > m.sizeBytes) {
            part.delete()
            have = 0L
        }
        val free = StatFs(dir.path).availableBytes
        if (free < m.sizeBytes - have + 20_000_000L) {
            throw ModelException(ModelException.Kind.NO_SPACE)
        }
        if (have < m.sizeBytes) fetch(m, part, have, onProgress)
        if (part.length() != m.sizeBytes) {
            throw ModelException(ModelException.Kind.NETWORK, "incomplete download")
        }
        if (sha256(part) != m.sha256) {
            part.delete()
            throw ModelException(ModelException.Kind.CORRUPT)
        }
        file(m).delete()
        if (!part.renameTo(file(m))) {
            throw ModelException(ModelException.Kind.NETWORK, "cannot move model into place")
        }
    }

    private suspend fun fetch(m: WhisperModel, part: File, have: Long, onProgress: (Long) -> Unit) {
        val conn = URL(m.url).openConnection() as HttpURLConnection
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
            FileOutputStream(part, append).use { out ->
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
