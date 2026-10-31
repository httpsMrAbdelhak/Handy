package com.handymobile.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handymobile.app.CompareResult
import com.handymobile.app.EngineState
import com.handymobile.app.R
import com.handymobile.app.UiState
import com.handymobile.app.model.WhisperModel
import com.handymobile.app.settings.DictationMode
import kotlinx.coroutines.delay

class Actions(
    val onDownload: (WhisperModel) -> Unit,
    val onCancelDownload: () -> Unit,
    val onSelectModel: (WhisperModel) -> Unit,
    val onRetryLoad: () -> Unit,
    val onRedownload: () -> Unit,
    val onMode: (DictationMode) -> Unit,
    val onFast: (Boolean) -> Unit,
    val onCorrections: (Boolean) -> Unit,
    val onDictionary: (String) -> Unit,
    val onCompare: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissError: () -> Unit,
)

@Composable
fun MainScreen(state: UiState, actions: Actions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_dictation, R.string.tab_settings, R.string.tab_compare)
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { ErrorCard(it.message, it.detail, it.offerSettings, actions) }
            when (tab) {
                0 -> DictationTab(state, actions)
                1 -> SettingsTab(state, actions)
                else -> CompareTab(state, actions)
            }
        }
    }
}

@Composable
private fun Busy(text: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
    Text(text)
}

@Composable
private fun DictationTab(state: UiState, actions: Actions) {
    val model = state.settings.model
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.model_title), style = MaterialTheme.typography.titleSmall)
            val dl = state.download
            when {
                dl != null -> DownloadProgress(dl.model, dl.doneBytes, actions)
                state.engine == EngineState.Checking || state.engine == EngineState.Loading ->
                    Busy(stringResource(R.string.model_loading))
                state.engine == EngineState.NoModel -> {
                    Text(stringResource(R.string.model_missing, stringResource(model.label), model.sizeMb))
                    Button(onClick = { actions.onDownload(model) }) { Text(stringResource(R.string.model_download)) }
                }
                state.engine == EngineState.Ready ->
                    Text(stringResource(R.string.model_ready, stringResource(model.label)))
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onRetryLoad) { Text(stringResource(R.string.model_retry)) }
                    OutlinedButton(onClick = actions.onRedownload) { Text(stringResource(R.string.model_redownload)) }
                }
            }
        }
    }

    Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleSmall)
    val idle = !state.recording && !state.transcribing && !state.compare.running
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DictationMode.entries.forEach { m ->
            FilterChip(
                selected = state.settings.mode == m,
                onClick = { actions.onMode(m) },
                enabled = idle,
                label = { Text(stringResource(m.label)) },
            )
        }
    }

    val ready = state.engine == EngineState.Ready && !state.compare.running
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = actions.onStart,
            enabled = ready && !state.recording && !state.transcribing,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.btn_start)) }
        OutlinedButton(
            onClick = actions.onStop,
            enabled = state.recording,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.btn_stop)) }
    }

    when {
        state.recording -> RecordingIndicator()
        state.transcribing -> Busy(stringResource(R.string.transcribing))
    }

    Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleSmall)
    ResultCard(state.text)
    state.stats?.let {
        Text(
            stringResource(
                R.string.stats_line,
                stringResource(it.model.label),
                it.transcribeMs / 1000f,
                it.audioMs / 1000f,
                it.audioMs.toFloat() / it.transcribeMs.coerceAtLeast(1),
                it.ramMb,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ResultCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        SelectionContainer {
            Text(
                text = text.ifEmpty { stringResource(R.string.result_hint) },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (text.isEmpty()) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadProgress(model: WhisperModel, done: Long, actions: Actions) {
    val pct = (done * 100 / model.sizeBytes).toInt().coerceIn(0, 100)
    Text(stringResource(R.string.model_downloading, stringResource(model.label), pct))
    LinearProgressIndicator(progress = { done.toFloat() / model.sizeBytes }, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = actions.onCancelDownload) { Text(stringResource(R.string.model_cancel)) }
}

@Composable
private fun SettingsTab(state: UiState, actions: Actions) {
    val s = state.settings
    Text(stringResource(R.string.settings_models), style = MaterialTheme.typography.titleSmall)
    val canSelect = !state.recording && !state.transcribing && !state.compare.running &&
        state.engine != EngineState.Loading
    WhisperModel.entries.forEach { m ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = s.model == m,
                    onClick = { actions.onSelectModel(m) },
                    enabled = canSelect,
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(m.label))
                    Text(
                        stringResource(R.string.model_size, m.sizeMb),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val dl = state.download
                when {
                    dl?.model == m -> Text("${(dl.doneBytes * 100 / m.sizeBytes).toInt().coerceIn(0, 100)} %")
                    m in state.installed -> Text(stringResource(R.string.model_installed))
                    else -> Button(onClick = { actions.onDownload(m) }, enabled = dl == null) {
                        Text(stringResource(R.string.model_download_short))
                    }
                }
            }
            if (state.download?.model == m) {
                TextButton(onClick = actions.onCancelDownload) { Text(stringResource(R.string.model_cancel)) }
            }
        }
    }

    SwitchRow(R.string.settings_fast, R.string.settings_fast_help, s.fast, actions.onFast)

    Text(stringResource(R.string.settings_dictionary), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.settings_dictionary_help), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = s.dictionary,
        onValueChange = actions.onDictionary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        placeholder = { Text(stringResource(R.string.settings_dictionary_example)) },
    )
    SwitchRow(R.string.settings_corrections, R.string.settings_corrections_help, s.corrections, actions.onCorrections)

    if (state.systemInfo.isNotBlank()) {
        Text(stringResource(R.string.settings_engine), style = MaterialTheme.typography.titleSmall)
        Text(state.systemInfo, style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.settings_load_time, state.loadMs / 1000f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SwitchRow(title: Int, help: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title))
            Text(stringResource(help), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CompareTab(state: UiState, actions: Actions) {
    val c = state.compare
    Text(stringResource(R.string.compare_help), style = MaterialTheme.typography.bodyMedium)
    val enabled = state.hasAudio && !c.running && !state.recording && !state.transcribing &&
        state.installed.isNotEmpty() && state.engine != EngineState.Loading
    Button(onClick = actions.onCompare, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.compare_run))
    }
    if (!state.hasAudio) Text(stringResource(R.string.compare_no_audio), style = MaterialTheme.typography.bodySmall)
    c.current?.let { Busy(stringResource(R.string.compare_running, stringResource(it.label))) }
    c.results.forEach { CompareCard(it, c.audioMs) }
}

@Composable
private fun CompareCard(r: CompareResult, audioMs: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(r.model.label), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    R.string.compare_stats,
                    r.transcribeMs / 1000f,
                    audioMs.toFloat() / r.transcribeMs.coerceAtLeast(1),
                    r.loadMs / 1000f,
                    r.ramMb,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            SelectionContainer { Text(r.text.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun ErrorCard(message: Int, detail: String?, offerSettings: Boolean, actions: Actions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.onErrorContainer)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Row {
                if (offerSettings) {
                    TextButton(onClick = actions.onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                }
                TextButton(onClick = actions.onDismissError) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

@Composable
private fun RecordingIndicator() {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }
    val alpha by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "recAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .alpha(alpha)
                .background(Color.Red, CircleShape),
        )
        Spacer(Modifier.size(10.dp))
        Text(stringResource(R.string.recording_now, "%d:%02d".format(seconds / 60, seconds % 60)))
    }
}
