package com.example.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.ml.FaceDetectionEngine
import com.example.ml.RecognitionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class CameraFacingMode {
    FRONT,
    BACK
}

class RadarCameraManager(
    private val context: Context,
    private val faceDetectionEngine: FaceDetectionEngine,
    private val recognitionCoordinator: RecognitionCoordinator
) {
    companion object {
        private const val TAG = "RadarCameraManager"
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val isProcessingFrame = AtomicBoolean(false)

    private var cameraProvider: ProcessCameraProvider? = null

    private val _facingMode = MutableStateFlow(CameraFacingMode.BACK)
    val facingMode: StateFlow<CameraFacingMode> = _facingMode.asStateFlow()

    private val _hasFrontCamera = MutableStateFlow(false)
    val hasFrontCamera: StateFlow<Boolean> = _hasFrontCamera.asStateFlow()

    private val _hasBackCamera = MutableStateFlow(false)
    val hasBackCamera: StateFlow<Boolean> = _hasBackCamera.asStateFlow()

    private val _isSimultaneousSupported = MutableStateFlow(false)
    val isSimultaneousSupported: StateFlow<Boolean> = _isSimultaneousSupported.asStateFlow()

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreviewView: PreviewView? = null

    init {
        checkCameraHardware()
    }

    private fun checkCameraHardware() {
        val pm = context.packageManager
        _hasBackCamera.value = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        _hasFrontCamera.value = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            _isSimultaneousSupported.value = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)
        } else {
            _isSimultaneousSupported.value = false
        }
    }

    fun switchCamera() {
        if (_facingMode.value == CameraFacingMode.BACK) {
            if (_hasFrontCamera.value) {
                _facingMode.value = CameraFacingMode.FRONT
            }
        } else {
            if (_hasBackCamera.value) {
                _facingMode.value = CameraFacingMode.BACK
            }
        }
        val owner = currentLifecycleOwner
        val previewView = currentPreviewView
        if (owner != null && previewView != null) {
            bindCamera(owner, previewView)
        }
    }

    fun setFacingMode(mode: CameraFacingMode) {
        if (_facingMode.value != mode) {
            _facingMode.value = mode
            val owner = currentLifecycleOwner
            val previewView = currentPreviewView
            if (owner != null && previewView != null) {
                bindCamera(owner, previewView)
            }
        }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        currentLifecycleOwner = lifecycleOwner
        currentPreviewView = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val targetSelector = if (_facingMode.value == CameraFacingMode.FRONT && _hasFrontCamera.value) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                if (!provider.hasCamera(targetSelector)) {
                    Log.w(TAG, "Target camera selector not available on this device")
                    return@addListener
                }

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    targetSelector,
                    preview,
                    imageAnalysis
                )
                Log.i(TAG, "Camera bound successfully to $targetSelector")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessingFrame.compareAndSet(false, true)) {
            scope.launch {
                try {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val rawBitmap = imageProxy.toBitmap()

                    val correctedBitmap = if (rotationDegrees != 0) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    } else {
                        rawBitmap
                    }

                    // 1. Detect faces with ML Kit
                    val faces = faceDetectionEngine.detectFaces(correctedBitmap, 0)

                    // 2. Process recognition & alerts
                    recognitionCoordinator.processFrame(correctedBitmap, faces)

                    if (correctedBitmap != rawBitmap) {
                        correctedBitmap.recycle()
                    }
                    rawBitmap.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Error analyzing frame", e)
                } finally {
                    imageProxy.close()
                    isProcessingFrame.set(false)
                }
            }
        } else {
            imageProxy.close()
        }
    }

    fun unbind() {
        try {
            cameraProvider?.unbindAll()
            currentLifecycleOwner = null
            currentPreviewView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        }
    }

    fun release() {
        unbind()
        try {
            cameraExecutor.shutdown()
        } catch (_: Exception) {}
    }
}
