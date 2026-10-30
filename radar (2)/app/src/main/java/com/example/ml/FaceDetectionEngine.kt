package com.example.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DetectedFaceInfo(
    val boundingBox: Rect,
    val trackingId: Int? = null
)

class FaceDetectionEngine {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        detector = FaceDetection.getClient(options)
    }

    suspend fun detectFaces(bitmap: Bitmap, rotationDegrees: Int = 0): List<DetectedFaceInfo> =
        withContext(Dispatchers.Default) {
            try {
                val image = InputImage.fromBitmap(bitmap, rotationDegrees)
                val task = detector.process(image)
                val faces: List<Face> = Tasks.await(task)
                faces.map { face ->
                    DetectedFaceInfo(
                        boundingBox = face.boundingBox,
                        trackingId = face.trackingId
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    fun close() {
        try {
            detector.close()
        } catch (_: Exception) {
        }
    }
}
