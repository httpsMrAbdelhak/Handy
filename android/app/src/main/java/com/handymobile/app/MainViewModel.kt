package com.handymobile.app

import android.app.ActivityManager
import android.app.Application
import android.os.Debug
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handymobile.app.audio.AudioTrim
import com.handymobile.app.audio.Recorder
import com.handymobile.app.model.ModelException
import com.handymobile.app.model.ModelManager
import com.handymobile.app.model.WhisperModel
import com.handymobile.app.settings.DictationMode
import com.handymobile.app.settings.Prefs
import com.handymobile.app.settings.Settings
import com.handymobile.app.text.Dictionary
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

enum class EngineState { Checking, NoModel, Loading, Ready, LoadFailed }

data class Download(val model: WhisperModel, val doneBytes: Long)

data class UiError(
    @StringRes val message: Int,
    val detail: String? = null,
    val offerSettings: Boolean = false,
)

data class Stats(val model: WhisperModel, val audioMs: Long, val transcribeMs: Long, val ramMb: Int)

data class CompareResult(
    val model: WhisperModel,
    val loadMs: Long,
    val transcribeMs: Long,
    val ramMb: Int,
    val text: String,
)

data class CompareState(
    val running: Boolean = false,
    val current: WhisperModel? = null,
    val results: List<CompareResult> = emptyList(),
    val audioMs: Long = 0,
)

data class UiState(
    val settings: Settings = Settings(),
    val installed: Set<WhisperModel> = emptySet(),
    val engine: EngineState = EngineState.Checking,
    val systemInfo: String = "",
    val loadMs: Long = 0,
    val download: Download? = null,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    val text: String = "",
    val stats: Stats? = null,
    val hasAudio: Boolean = false,
    val compare: CompareState = CompareState(),
    val error: UiError? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        const val MIN_FREE_RAM = 250L * 1024 * 1024
        const val MIN_SAMPLES = Recorder.SAMPLE_RATE / 2
    }

    private val models = ModelManager(app)
    private val prefs = Prefs(app)
    private val engine = WhisperEngine()
    private val recorder = Recorder()
    private val libDir = app.applicationInfo.nativeLibraryDir
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()
    // The Snapdragon 845 has 4 big cores; more threads only add contention with the little ones.
    private val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    private val _state = MutableStateFlow(UiState(settings = prefs.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var downloadJob: Job? = null
    private var lastAudio: FloatArray? = null

    init {
        viewModelScope.launch {
            mutate { copy(installed = models.installed()) }
            loadActive()
        }
    }

    private fun mutate(block: UiState.() -> UiState) = _state.update(block)
    private fun pssMb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024
    private fun now() = SystemClock.elapsedRealtime()

    private fun isBusy(): Boolean {
        val s = _state.value
        return s.recording || s.transcribing || s.compare.running || s.engine == EngineState.Loading
    }

    private fun updateSettings(block: Settings.() -> Settings) {
        val s = _state.value.settings.block()
        prefs.save(s)
        mutate { copy(settings = s) }
    }

    fun setMode(m: DictationMode) = updateSettings { copy(mode = m) }
    fun setFast(v: Boolean) = updateSettings { copy(fast = v) }
    fun setCorrections(v: Boolean) = updateSettings { copy(corrections = v) }
    fun setDictionary(t: String) = updateSettings { copy(dictionary = t) }
    fun dismissError() = mutate { copy(error = null) }

    fun selectModel(m: WhisperModel) {
        if (isBusy() || _state.value.settings.model == m) return
        updateSettings { copy(model = m) }
        viewModelScope.launch { loadActive() }
    }

    private suspend fun loadActive() {
        val m = _state.value.settings.model
        if (!models.isInstalled(m)) {
            withContext(engineDispatcher) { engine.close() }
            mutate { copy(engine = EngineState.NoModel) }
            return
        }
        mutate { copy(engine = EngineState.Loading, error = null) }
        val am = getApplication<Application>().getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        if (info.availMem < MIN_FREE_RAM) {
            withContext(engineDispatcher) { engine.close() }
            mutate { copy(engine = EngineState.LoadFailed, error = UiError(R.string.err_low_memory)) }
            return
        }
        try {
            val t0 = now()
            val sys = withContext(engineDispatcher) {
                engine.load(models.file(m).path, libDir)
                engine.systemInfo()
            }
            mutate { copy(engine = EngineState.Ready, systemInfo = sys, loadMs = now() - t0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mutate { copy(engine = EngineState.LoadFailed, error = UiError(R.string.err_load, e.message)) }
        }
    }

    fun retryLoad() {
        viewModelScope.launch { loadActive() }
    }

    fun redownload() {
        val m = _state.value.settings.model
        models.delete(m)
        mutate { copy(installed = models.installed(), engine = EngineState.NoModel, error = null) }
        downloadModel(m)
    }

    fun downloadModel(m: WhisperModel) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            mutate { copy(download = Download(m, 0), error = null) }
            try {
                models.download(m) { done -> mutate { copy(download = Download(m, done)) } }
                mutate { copy(download = null, installed = models.installed()) }
                if (_state.value.settings.model == m && !isBusy()) loadActive()
            } catch (e: CancellationException) {
                mutate { copy(download = null) }
                throw e
            } catch (e: ModelException) {
                val res = when (e.kind) {
                    ModelException.Kind.NO_SPACE -> R.string.err_no_space
                    ModelException.Kind.NETWORK -> R.string.err_network
                    ModelException.Kind.HTTP -> R.string.err_http
                    ModelException.Kind.CORRUPT -> R.string.err_corrupt
                }
                mutate { copy(download = null, error = UiError(res, e.detail)) }
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
        if (s.engine != EngineState.Ready || isBusy()) return
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
        val raw = recorder.stop()
        if (raw.size < MIN_SAMPLES) {
            mutate { copy(error = UiError(R.string.err_too_short)) }
            return
        }
        val audio = AudioTrim.trim(raw)
        if (audio == null) {
            mutate { copy(error = UiError(R.string.err_no_speech)) }
            return
        }
        lastAudio = audio
        val settings = _state.value.settings
        val parsed = Dictionary.parse(settings.dictionary)
        val prompt = Dictionary.buildPrompt(settings.mode, parsed)
        mutate { copy(transcribing = true, hasAudio = true, error = null) }
        viewModelScope.launch {
            try {
                val t0 = now()
                val rawText = withContext(engineDispatcher) {
                    engine.transcribe(audio, settings.mode.code, prompt, settings.fast, threads)
                }
                val ms = now() - t0
                val text = if (settings.corrections) Dictionary.apply(rawText, parsed) else rawText
                val stats = Stats(settings.model, audio.size * 1000L / Recorder.SAMPLE_RATE, ms, pssMb())
                mutate {
                    copy(
                        transcribing = false,
                        text = text,
                        stats = stats,
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

    /** Runs every installed model on the last recording with the current settings. */
    fun compare() {
        val audio = lastAudio ?: return
        if (isBusy()) return
        val settings = _state.value.settings
        val parsed = Dictionary.parse(settings.dictionary)
        val prompt = Dictionary.buildPrompt(settings.mode, parsed)
        val audioMs = audio.size * 1000L / Recorder.SAMPLE_RATE
        mutate { copy(compare = CompareState(running = true, audioMs = audioMs), error = null) }
        viewModelScope.launch {
            val results = mutableListOf<CompareResult>()
            try {
                for (m in WhisperModel.entries.filter { models.isInstalled(it) }) {
                    mutate { copy(compare = compare.copy(current = m)) }
                    val r = withContext(engineDispatcher) {
                        val t0 = now()
                        engine.load(models.file(m).path, libDir)
                        val loadMs = now() - t0
                        val t1 = now()
                        val raw = engine.transcribe(audio, settings.mode.code, prompt, settings.fast, threads)
                        val ms = now() - t1
                        val text = if (settings.corrections) Dictionary.apply(raw, parsed) else raw
                        CompareResult(m, loadMs, ms, pssMb(), text)
                    }
                    results += r
                    mutate { copy(compare = compare.copy(results = results.toList())) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                mutate { copy(error = UiError(R.string.err_transcribe, e.message)) }
            }
            mutate { copy(compare = compare.copy(running = false, current = null)) }
            loadActive()
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
