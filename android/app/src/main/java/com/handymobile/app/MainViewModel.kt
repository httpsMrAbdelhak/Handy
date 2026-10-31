package com.handymobile.app

import android.app.ActivityManager
import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handymobile.app.audio.Recorder
import com.handymobile.app.model.ModelException
import com.handymobile.app.model.ModelManager
import com.handymobile.app.whisper.WhisperEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

sealed interface ModelState {
    data object Checking : ModelState
    data object Missing : ModelState
    data class Downloading(val doneBytes: Long) : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    data object LoadFailed : ModelState
}

data class UiError(
    @StringRes val message: Int,
    val detail: String? = null,
    val offerSettings: Boolean = false,
)

data class UiState(
    val model: ModelState = ModelState.Checking,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    val text: String = "",
    val language: String = "fr",
    val error: UiError? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        const val MIN_FREE_RAM = 250L * 1024 * 1024
        const val MIN_SAMPLES = Recorder.SAMPLE_RATE / 2
    }

    private val models = ModelManager(app)
    private val engine = WhisperEngine()
    private val recorder = Recorder()
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()
    private val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            if (models.isInstalled()) loadModel() else mutate { copy(model = ModelState.Missing) }
        }
    }

    private fun mutate(block: UiState.() -> UiState) = _state.update(block)

    fun setLanguage(code: String) = mutate { copy(language = code) }
    fun dismissError() = mutate { copy(error = null) }

    private suspend fun loadModel() {
        mutate { copy(model = ModelState.Loading, error = null) }
        val am = getApplication<Application>().getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        if (info.availMem < MIN_FREE_RAM) {
            mutate { copy(model = ModelState.LoadFailed, error = UiError(R.string.err_low_memory)) }
            return
        }
        try {
            withContext(engineDispatcher) { engine.load(models.modelFile.path) }
            mutate { copy(model = ModelState.Ready) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mutate {
                copy(model = ModelState.LoadFailed, error = UiError(R.string.err_load, e.message))
            }
        }
    }

    fun retryLoad() {
        viewModelScope.launch { if (models.isInstalled()) loadModel() else mutate { copy(model = ModelState.Missing) } }
    }

    fun redownload() {
        models.delete()
        mutate { copy(model = ModelState.Missing, error = null) }
        downloadModel()
    }

    fun downloadModel() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            mutate { copy(model = ModelState.Downloading(0), error = null) }
            try {
                models.download { done -> mutate { copy(model = ModelState.Downloading(done)) } }
                loadModel()
            } catch (e: CancellationException) {
                mutate { copy(model = ModelState.Missing) }
                throw e
            } catch (e: ModelException) {
                val res = when (e.kind) {
                    ModelException.Kind.NO_SPACE -> R.string.err_no_space
                    ModelException.Kind.NETWORK -> R.string.err_network
                    ModelException.Kind.HTTP -> R.string.err_http
                    ModelException.Kind.CORRUPT -> R.string.err_corrupt
                }
                mutate { copy(model = ModelState.Missing, error = UiError(res, e.detail)) }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun onMicDenied(permanent: Boolean) = mutate {
        copy(error = UiError(if (permanent) R.string.err_mic_denied_permanent else R.string.err_mic_denied, offerSettings = permanent))
    }

    fun startRecording() {
        val s = _state.value
        if (s.model != ModelState.Ready || s.recording || s.transcribing) return
        try {
            recorder.start(
                onLimitReached = { viewModelScope.launch { stopRecording() } },
                onError = { e ->
                    viewModelScope.launch {
                        recorder.cancel()
                        mutate { copy(recording = false, error = UiError(R.string.err_mic, e.message)) }
                    }
                },
            )
            mutate { copy(recording = true, error = null) }
        } catch (e: Exception) {
            mutate { copy(error = UiError(R.string.err_mic, e.message)) }
        }
    }

    fun stopRecording() {
        if (!_state.value.recording) return
        mutate { copy(recording = false) }
        val samples = recorder.stop()
        if (samples.size < MIN_SAMPLES) {
            mutate { copy(error = UiError(R.string.err_too_short)) }
            return
        }
        val language = _state.value.language
        mutate { copy(transcribing = true, error = null) }
        viewModelScope.launch {
            try {
                val text = withContext(engineDispatcher) { engine.transcribe(samples, language, threads) }
                mutate {
                    copy(
                        transcribing = false,
                        text = text,
                        error = if (text.isBlank()) UiError(R.string.err_no_speech) else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                mutate { copy(transcribing = false, error = UiError(R.string.err_transcribe, e.message)) }
            }
        }
    }

    /** Android silences the microphone for background apps, so drop the recording. */
    fun onAppBackgrounded() {
        if (_state.value.recording) {
            recorder.cancel()
            mutate { copy(recording = false, error = UiError(R.string.err_background)) }
        }
    }

    override fun onCleared() {
        recorder.cancel()
        engineExecutor.execute { engine.close() }
        engineExecutor.shutdown()
    }
}
