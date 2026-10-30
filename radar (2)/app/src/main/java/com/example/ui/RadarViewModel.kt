package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alert.RadarAlertManager
import com.example.camera.CameraFacingMode
import com.example.camera.RadarCameraManager
import com.example.data.PersonEntity
import com.example.data.PersonNicknameEntity
import com.example.data.PersonPhotoEntity
import com.example.data.PersonWithPhotos
import com.example.data.PhotoEnrollmentResult
import com.example.data.ProfileManager
import com.example.data.RadarDatabase
import com.example.ml.FaceDetectionEngine
import com.example.ml.FaceRecognitionEngine
import com.example.ml.GeneratedCommentary
import com.example.ml.HumorCommentaryEngine
import com.example.ml.HumorLevel
import com.example.ml.HumorSettings
import com.example.ml.RadarStatus
import com.example.ml.RecognitionCoordinator
import com.example.ml.TrackedFaceUi
import com.example.service.RadarMonitoringService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RadarDatabase.getInstance(application)
    val detectionEngine = FaceDetectionEngine()
    val recognitionEngine = FaceRecognitionEngine(application)
    val humorEngine = HumorCommentaryEngine(application, database)
    val alertManager = RadarAlertManager(application, humorEngine)
    val recognitionCoordinator = RecognitionCoordinator(recognitionEngine, alertManager)
    val cameraManager = RadarCameraManager(application, detectionEngine, recognitionCoordinator)
    val profileManager = ProfileManager(application, database, detectionEngine, recognitionEngine)

    val registeredProfiles: StateFlow<List<PersonWithPhotos>> =
        profileManager.allPersonsWithPhotos.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val radarStatus: StateFlow<RadarStatus> = recognitionCoordinator.status
    val trackedFaces: StateFlow<List<TrackedFaceUi>> = recognitionCoordinator.trackedFaces
    val isMuted: StateFlow<Boolean> = alertManager.isMuted
    val isAlarmActive: StateFlow<Boolean> = alertManager.isAlarmActive
    val ttsErrorMessage: StateFlow<String?> = alertManager.ttsErrorMessage
    val currentCommentary: StateFlow<GeneratedCommentary?> = alertManager.currentCommentary
    val humorSettings: StateFlow<HumorSettings> = humorEngine.settings
    val confidenceThreshold: StateFlow<Float> = recognitionCoordinator.confidenceThreshold
    val facingMode: StateFlow<CameraFacingMode> = cameraManager.facingMode
    val isSimultaneousSupported: StateFlow<Boolean> = cameraManager.isSimultaneousSupported

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _selectedPerson = MutableStateFlow<PersonWithPhotos?>(null)
    val selectedPerson: StateFlow<PersonWithPhotos?> = _selectedPerson.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        alertManager.humorEngine = humorEngine

        viewModelScope.launch {
            registeredProfiles.collect { profiles ->
                recognitionCoordinator.updateRegisteredProfiles(profiles)
                val current = _selectedPerson.value
                if (current != null) {
                    _selectedPerson.value = profiles.find { it.person.id == current.person.id }
                }
            }
        }
    }

    fun startMonitoring() {
        val readyCount = registeredProfiles.value.count { it.isReadyForRecognition }
        _isMonitoring.value = true
        recognitionCoordinator.startMonitoring()
        RadarMonitoringService.start(getApplication())
        if (readyCount == 0) {
            _userMessage.value = "Monitoring started. Tip: Add photos to profiles so targets can be recognized."
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        recognitionCoordinator.stopMonitoring()
        RadarMonitoringService.stop(getApplication())
    }

    fun toggleMute() {
        alertManager.toggleMute()
    }

    fun silenceAlarm() {
        alertManager.stopContinuousAlert()
    }

    fun clearTtsError() {
        alertManager.clearTtsError()
    }

    fun setHumorEnabled(enabled: Boolean) {
        humorEngine.setEnabled(enabled)
    }

    fun setHumorLevel(level: HumorLevel) {
        humorEngine.setHumorLevel(level)
    }

    fun setHumorInterval(seconds: Int) {
        humorEngine.setIntervalSeconds(seconds)
    }

    fun clearAllNicknames() {
        viewModelScope.launch {
            humorEngine.clearAllNicknames()
            _userMessage.value = "All saved nicknames cleared. Fresh ones will be generated."
        }
    }

    fun getNicknamesForPerson(personId: Long): Flow<List<PersonNicknameEntity>> {
        return profileManager.getNicknamesForPerson(personId)
    }

    fun regenerateNicknamesForPerson(personId: Long, personName: String) {
        viewModelScope.launch {
            humorEngine.regenerateNicknamesForPerson(personId, personName)
            _userMessage.value = "Fresh nicknames generated for $personName."
        }
    }

    fun addCustomNickname(personId: Long, nickname: String) {
        if (nickname.isBlank()) return
        viewModelScope.launch {
            humorEngine.addCustomNickname(personId, nickname)
            _userMessage.value = "Nickname \"$nickname\" added."
        }
    }

    fun deleteNickname(nickname: PersonNicknameEntity) {
        viewModelScope.launch {
            profileManager.deleteNickname(nickname)
        }
    }

    fun switchCamera() {
        cameraManager.switchCamera()
    }

    fun setThreshold(threshold: Float) {
        recognitionCoordinator.setThreshold(threshold)
    }

    fun selectPerson(personWithPhotos: PersonWithPhotos?) {
        _selectedPerson.value = personWithPhotos
    }

    fun createPerson(name: String, notes: String = "", onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = profileManager.createPerson(name, notes)
            // Pre-seed funny nicknames for this new person
            humorEngine.generateInitialPoolForPerson(id, name, humorEngine.settings.value.humorLevel)
            onCreated(id)
        }
    }

    fun updatePerson(person: PersonEntity) {
        viewModelScope.launch {
            profileManager.updatePerson(person)
        }
    }

    fun deletePerson(personWithPhotos: PersonWithPhotos) {
        viewModelScope.launch {
            profileManager.deletePerson(personWithPhotos)
            if (_selectedPerson.value?.person?.id == personWithPhotos.person.id) {
                _selectedPerson.value = null
            }
        }
    }

    fun enrollPhotoFromUri(personId: Long, uri: Uri, onResult: (PhotoEnrollmentResult) -> Unit) {
        viewModelScope.launch {
            val result = profileManager.enrollPhotoFromUri(personId, uri)
            onResult(result)
        }
    }

    fun enrollBitmap(personId: Long, bitmap: Bitmap, onResult: (PhotoEnrollmentResult) -> Unit) {
        viewModelScope.launch {
            val result = profileManager.enrollBitmap(personId, bitmap)
            onResult(result)
        }
    }

    fun deletePhoto(photo: PersonPhotoEntity) {
        viewModelScope.launch {
            profileManager.deletePhoto(photo)
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        cameraManager.release()
        alertManager.release()
        detectionEngine.close()
        recognitionEngine.close()
    }
}
