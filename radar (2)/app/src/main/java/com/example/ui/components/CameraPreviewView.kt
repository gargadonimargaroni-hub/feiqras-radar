package com.example.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camera.RadarCameraManager
import com.example.ml.TrackedFaceUi
import com.example.ui.theme.RadarPitchBlack

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    cameraManager: RadarCameraManager,
    isMonitoring: Boolean,
    trackedFaces: List<TrackedFaceUi>,
    isDangerAlert: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(lifecycleOwner, isMonitoring) {
        if (isMonitoring) {
            cameraManager.bindCamera(lifecycleOwner, previewView)
        } else {
            cameraManager.unbind()
        }
        onDispose {
            cameraManager.unbind()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RadarPitchBlack)
    ) {
        if (isMonitoring) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        RadarOverlay(
            modifier = Modifier.fillMaxSize(),
            isScanning = isMonitoring,
            trackedFaces = trackedFaces,
            isDangerAlert = isDangerAlert
        )
    }
}
