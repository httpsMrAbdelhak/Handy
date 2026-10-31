package com.handymobile.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StatusScreen()
                }
            }
        }
    }
}

@Composable
private fun StatusScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.phase_label),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.android_label, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.device_label, Build.MANUFACTURER, Build.MODEL),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
