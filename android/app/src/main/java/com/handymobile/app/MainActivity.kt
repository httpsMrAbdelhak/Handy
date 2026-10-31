package com.handymobile.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.handymobile.app.ui.Actions
import com.handymobile.app.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                vm.startRecording()
            } else {
                vm.onMicDenied(!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
            }
        }

    private fun onStartClicked() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = Actions(
            onDownload = vm::downloadModel,
            onCancelDownload = vm::cancelDownload,
            onSelectModel = vm::selectModel,
            onRetryLoad = vm::retryLoad,
            onRedownload = vm::redownload,
            onMode = vm::setMode,
            onFast = vm::setFast,
            onCorrections = vm::setCorrections,
            onDictionary = vm::setDictionary,
            onCompare = vm::compare,
            onStart = ::onStartClicked,
            onStop = vm::stopRecording,
            onOpenSettings = ::openAppSettings,
            onDismissError = vm::dismissError,
        )
        setContent {
            MaterialTheme {
                Surface {
                    val state by vm.state.collectAsState()
                    MainScreen(state, actions)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.onAppBackgrounded()
    }
}
