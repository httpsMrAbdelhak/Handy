package com.handymobile.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handymobile.app.ModelState
import com.handymobile.app.R
import com.handymobile.app.UiState
import com.handymobile.app.model.ModelSpec
import kotlinx.coroutines.delay

class Actions(
    val onDownload: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onRedownload: () -> Unit,
    val onLanguage: (String) -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissError: () -> Unit,
)

@Composable
fun MainScreen(state: UiState, actions: Actions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        ModelCard(state, actions)
        state.error?.let { ErrorCard(it.message, it.detail, it.offerSettings, actions) }

        val ready = state.model == ModelState.Ready
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("fr" to R.string.lang_fr, "ar" to R.string.lang_ar, "en" to R.string.lang_en)
                .forEach { (code, label) ->
                    FilterChip(
                        selected = state.language == code,
                        onClick = { actions.onLanguage(code) },
                        enabled = !state.recording && !state.transcribing,
                        label = { Text(stringResource(label)) },
                    )
                }
        }

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
            state.transcribing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.transcribing))
            }
        }

        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleSmall)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            SelectionContainer {
                Text(
                    text = state.text.ifEmpty { stringResource(R.string.result_hint) },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.text.isEmpty()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModelCard(state: UiState, actions: Actions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.model_title), style = MaterialTheme.typography.titleSmall)
            when (val m = state.model) {
                ModelState.Checking, ModelState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.model_loading))
                }
                ModelState.Missing -> {
                    Text(stringResource(R.string.model_missing))
                    Button(onClick = actions.onDownload) { Text(stringResource(R.string.model_download)) }
                }
                is ModelState.Downloading -> {
                    val pct = (m.doneBytes * 100 / ModelSpec.SIZE_BYTES).toInt().coerceIn(0, 100)
                    Text(stringResource(R.string.model_downloading, pct))
                    LinearProgressIndicator(
                        progress = { m.doneBytes.toFloat() / ModelSpec.SIZE_BYTES },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = actions.onCancelDownload) { Text(stringResource(R.string.model_cancel)) }
                }
                ModelState.Ready -> Text(stringResource(R.string.model_ready))
                ModelState.LoadFailed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onRetryLoad) { Text(stringResource(R.string.model_retry)) }
                    OutlinedButton(onClick = actions.onRedownload) { Text(stringResource(R.string.model_redownload)) }
                }
            }
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
