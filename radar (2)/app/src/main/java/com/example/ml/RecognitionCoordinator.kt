package com.example.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.alert.RadarAlertManager
import com.example.data.PersonWithPhotos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RadarStatus {
    object Ready : RadarStatus()
    object Scanning : RadarStatus()
    data class DangerRecognized(
        val personId: Long,
        val personName: String,
        val confidence: Float,
        val photoCount: Int
    ) : RadarStatus()
    data class CameraError(val message: String) : RadarStatus()
}

data class TrackedFaceUi(
    val boundingBox: Rect,
    val isRecognized: Boolean,
    val personName: String?,
    val confidence: Float
)

class RecognitionCoordinator(
    private val recognitionEngine: FaceRecognitionEngine,
    private val alertManager: RadarAlertManager
) {
    companion object {
        private const val TAG = "RecognitionCoordinator"
        const val DEFAULT_THRESHOLD = 0.72f
        const val REPEAT_ALERT_INTERVAL_MS = 30_000L // 30 seconds repetition
        const val GRACE_PERIOD_MS = 3_500L          // 3.5s grace period for tracking loss
        const val MULTI_FRAME_MIN_HITS = 2          // Require 2 matches within window
        const val MULTI_FRAME_WINDOW_MS = 1_500L
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _status = MutableStateFlow<RadarStatus>(RadarStatus.Ready)
    val status: StateFlow<RadarStatus> = _status.asStateFlow()

    private val _trackedFaces = MutableStateFlow<List<TrackedFaceUi>>(emptyList())
    val trackedFaces: StateFlow<List<TrackedFaceUi>> = _trackedFaces.asStateFlow()

    private val _confidenceThreshold = MutableStateFlow(DEFAULT_THRESHOLD)
    val confidenceThreshold: StateFlow<Float> = _confidenceThreshold.asStateFlow()

    private var registeredProfiles: List<PersonWithPhotos> = emptyList()

    // Tracking state for active recognized target
    private var activeTargetPersonId: Long? = null
    private var activeTargetName: String? = null
    private var activeTargetLastSeenMs: Long = 0L
    private var activeTargetLastAlertMs: Long = 0L

    // Frame confirmation tracking: map of personId -> list of timestamps
    private val candidateHitTimestamps = mutableMapOf<Long, MutableList<Long>>()

    private var isMonitoringActive = false

    fun updateRegisteredProfiles(profiles: List<PersonWithPhotos>) {
        registeredProfiles = profiles.filter { it.isReadyForRecognition }
        Log.i(TAG, "Updated registered profiles: ${registeredProfiles.size} ready for recognition")
    }

    fun setThreshold(threshold: Float) {
        _confidenceThreshold.value = threshold.coerceIn(0.55f, 0.90f)
    }

    fun startMonitoring() {
        isMonitoringActive = true
        resetTracking()
        _status.value = RadarStatus.Scanning
    }

    fun stopMonitoring() {
        isMonitoringActive = false
        resetTracking()
        alertManager.stopContinuousAlert()
        _status.value = RadarStatus.Ready
        _trackedFaces.value = emptyList()
    }

    private fun resetTracking() {
        activeTargetPersonId = null
        activeTargetName = null
        activeTargetLastSeenMs = 0L
        activeTargetLastAlertMs = 0L
        candidateHitTimestamps.clear()
        alertManager.stopContinuousAlert()
    }

    /**
     * Called from camera analyzer for each frame.
     */
    suspend fun processFrame(
        frameBitmap: Bitmap,
        detectedFaces: List<DetectedFaceInfo>
    ) {
        if (!isMonitoringActive) return

        val now = System.currentTimeMillis()
        val currentThreshold = _confidenceThreshold.value
        val faceUiList = mutableListOf<TrackedFaceUi>()

        var highestMatchPerson: PersonWithPhotos? = null
        var highestConfidence = 0f
        var highestMatchBox: Rect? = null

        // If no profiles registered, we still show faces as scanning but cannot alert
        if (registeredProfiles.isNotEmpty()) {
            for (face in detectedFaces) {
                val embedding = recognitionEngine.extractEmbedding(frameBitmap, face.boundingBox)
                if (embedding != null) {
                    var bestPersonForThisFace: PersonWithPhotos? = null
                    var bestScoreForThisFace = 0f

                    for (person in registeredProfiles) {
                        for (photo in person.photos) {
                            val refEmb = photo.getEmbeddingArray()
                            if (refEmb.isNotEmpty()) {
                                val sim = FaceRecognitionEngine.cosineSimilarity(embedding, refEmb)
                                if (sim > bestScoreForThisFace) {
                                    bestScoreForThisFace = sim
                                    bestPersonForThisFace = person
                                }
                            }
                        }
                    }

                    val isMatch = bestScoreForThisFace >= currentThreshold && bestPersonForThisFace != null

                    faceUiList.add(
                        TrackedFaceUi(
                            boundingBox = face.boundingBox,
                            isRecognized = isMatch,
                            personName = if (isMatch) bestPersonForThisFace?.person?.name else null,
                            confidence = bestScoreForThisFace
                        )
                    )

                    if (isMatch && bestScoreForThisFace > highestConfidence) {
                        highestConfidence = bestScoreForThisFace
                        highestMatchPerson = bestPersonForThisFace
                        highestMatchBox = face.boundingBox
                    }
                } else {
                    faceUiList.add(
                        TrackedFaceUi(
                            boundingBox = face.boundingBox,
                            isRecognized = false,
                            personName = null,
                            confidence = 0f
                        )
                    )
                }
            }
        } else {
            for (face in detectedFaces) {
                faceUiList.add(
                    TrackedFaceUi(
                        boundingBox = face.boundingBox,
                        isRecognized = false,
                        personName = null,
                        confidence = 0f
                    )
                )
            }
        }

        _trackedFaces.value = faceUiList

        // Handle alert and confirmation logic
        if (highestMatchPerson != null) {
            val personId = highestMatchPerson.person.id
            val personName = highestMatchPerson.person.name

            // Record hit for multi-frame confirmation
            val hits = candidateHitTimestamps.getOrPut(personId) { mutableListOf() }
            hits.add(now)
            // Prune hits older than window
            hits.removeAll { now - it > MULTI_FRAME_WINDOW_MS }

            if (hits.size >= MULTI_FRAME_MIN_HITS) {
                // Confirmed detection: keep continuous loud alarm active
                activeTargetPersonId = personId
                activeTargetName = personName
                activeTargetLastSeenMs = now

                // Start or maintain continuous loud alarm & voice loop with profile ID
                alertManager.startContinuousAlert(personId, personName)

                _status.value = RadarStatus.DangerRecognized(
                    personId = personId,
                    personName = personName,
                    confidence = highestConfidence,
                    photoCount = highestMatchPerson.photos.size
                )
            }
        } else {
            // No recognized registered person in this frame
            if (activeTargetPersonId != null) {
                // Check if grace period has expired (avoids interruptions from temporary tracking loss)
                if (now - activeTargetLastSeenMs > GRACE_PERIOD_MS) {
                    Log.i(TAG, "Target person $activeTargetName disappeared beyond grace period.")
                    activeTargetPersonId = null
                    activeTargetName = null
                    candidateHitTimestamps.clear()
                    alertManager.stopContinuousAlert()
                    _status.value = RadarStatus.Scanning
                }
            } else {
                if (_status.value !is RadarStatus.Scanning) {
                    _status.value = RadarStatus.Scanning
                }
            }
        }
    }
}
