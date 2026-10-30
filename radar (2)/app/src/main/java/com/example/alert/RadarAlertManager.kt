package com.example.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.ml.GeneratedCommentary
import com.example.ml.HumorCommentaryEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class RadarAlertManager(
    private val context: Context,
    var humorEngine: HumorCommentaryEngine? = null
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Dual ToneGenerators for dynamic audio mixing during speech
    private var toneGeneratorNormal: ToneGenerator? = null
    private var toneGeneratorDucked: ToneGenerator? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive: StateFlow<Boolean> = _isAlarmActive.asStateFlow()

    private val _ttsErrorMessage = MutableStateFlow<String?>(null)
    val ttsErrorMessage: StateFlow<String?> = _ttsErrorMessage.asStateFlow()

    // Live commentary displayed on UI while recognized
    private val _currentCommentary = MutableStateFlow<GeneratedCommentary?>(null)
    val currentCommentary: StateFlow<GeneratedCommentary?> = _currentCommentary.asStateFlow()

    private var beepJob: Job? = null
    private var voiceJob: Job? = null
    private val isCurrentlySpeaking = AtomicBoolean(false)

    private var activePersonId: Long? = null
    private var activePersonName: String = ""

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        private const val TAG = "RadarAlertManager"
        private const val DEFAULT_SPEECH_INTERVAL_MS = 5000L
        private const val BEEP_DURATION_MS = 240
        private const val BEEP_PAUSE_MS = 200
        private const val TONE_VOLUME_NORMAL = 85
        private const val TONE_VOLUME_DUCKED = 35 // Ducked slightly during speech so commentary is crystal clear
    }

    init {
        initAudio()
    }

    private fun initAudio() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            toneGeneratorNormal = ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME_NORMAL)
            toneGeneratorDucked = ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME_DUCKED)
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator init failed", e)
        }
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "TTS init failed", e)
            _ttsErrorMessage.value = "Text-to-Speech initialization failed. Tone alarm active."
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "US English language not supported, trying default locale")
                val defResult = tts?.setLanguage(Locale.getDefault())
                if (defResult == TextToSpeech.LANG_MISSING_DATA || defResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _ttsErrorMessage.value = "English voice not found. Sound alarm is active."
                }
            }

            // Loud, slow, clear pronunciation
            tts?.setSpeechRate(0.78f)
            tts?.setPitch(0.95f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isCurrentlySpeaking.set(true)
                }

                override fun onDone(utteranceId: String?) {
                    isCurrentlySpeaking.set(false)
                }

                override fun onError(utteranceId: String?) {
                    isCurrentlySpeaking.set(false)
                }
            })

            isTtsReady = true
            Log.i(TAG, "TextToSpeech initialized successfully.")
        } else {
            Log.e(TAG, "TextToSpeech init failed with status: $status")
            _ttsErrorMessage.value = "TextToSpeech engine unavailable. Sound alarm is active."
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            stopAudio()
        } else {
            if (_isAlarmActive.value) {
                requestAudioFocus()
                triggerImmediateVoiceCommentary()
            }
        }
    }

    fun toggleMute() {
        setMuted(!_isMuted.value)
    }

    /**
     * Overload for backwards compatibility.
     */
    fun startContinuousAlert(personName: String) {
        startContinuousAlert(0L, personName)
    }

    /**
     * Activates simultaneous repeating warning beep AND spoken commentary.
     * Both play concurrently without stopping the beep.
     */
    @Synchronized
    fun startContinuousAlert(personId: Long, personName: String) {
        val personChanged = (activePersonId != personId)
        activePersonId = personId
        activePersonName = personName
        _isAlarmActive.value = true

        requestAudioFocus()

        // 1. Launch Continuous Beep Job (repeats continuously without stopping)
        if (beepJob?.isActive != true) {
            beepJob = scope.launch {
                try {
                    while (isActive && _isAlarmActive.value) {
                        if (!_isMuted.value) {
                            try {
                                // Dynamically mix: if voice is actively speaking, use ducked volume
                                val activeToneGen = if (isCurrentlySpeaking.get()) {
                                    toneGeneratorDucked ?: toneGeneratorNormal
                                } else {
                                    toneGeneratorNormal
                                }

                                activeToneGen?.startTone(
                                    ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
                                    BEEP_DURATION_MS
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Tone playback error", e)
                            }

                            vibratePulse()
                            delay(BEEP_DURATION_MS.toLong())

                            toneGeneratorNormal?.stopTone()
                            toneGeneratorDucked?.stopTone()
                            delay(BEEP_PAUSE_MS.toLong())
                        } else {
                            delay(200)
                        }
                    }
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in beep loop", e)
                } finally {
                    toneGeneratorNormal?.stopTone()
                    toneGeneratorDucked?.stopTone()
                }
            }
        }

        // 2. Launch Voice Job (speaks immediately and repeats periodically)
        if (voiceJob?.isActive != true || personChanged) {
            voiceJob?.cancel()
            voiceJob = scope.launch {
                try {
                    while (isActive && _isAlarmActive.value) {
                        val currentId = activePersonId ?: personId
                        val currentName = activePersonName.ifEmpty { personName }

                        val humor = humorEngine
                        val intervalMs: Long

                        if (humor != null && humor.settings.value.isEnabled) {
                            val commentary = humor.getNextCommentaryForPerson(currentId, currentName)
                            _currentCommentary.value = commentary

                            if (!_isMuted.value) {
                                speakPhrase(commentary.fullSpokenWarning)
                            }
                            intervalMs = humor.settings.value.intervalSeconds * 1000L
                        } else {
                            _currentCommentary.value = null
                            if (!_isMuted.value) {
                                speakPhrase("DANGER! DANGER!")
                            }
                            intervalMs = DEFAULT_SPEECH_INTERVAL_MS
                        }

                        delay(intervalMs)
                    }
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in voice loop", e)
                } finally {
                    tts?.stop()
                    isCurrentlySpeaking.set(false)
                }
            }
        }
    }

    private fun triggerImmediateVoiceCommentary() {
        scope.launch {
            val pId = activePersonId ?: return@launch
            val pName = activePersonName
            val humor = humorEngine
            if (humor != null && humor.settings.value.isEnabled) {
                val commentary = humor.getNextCommentaryForPerson(pId, pName)
                _currentCommentary.value = commentary
                speakPhrase(commentary.fullSpokenWarning)
            } else {
                speakPhrase("DANGER! DANGER!")
            }
        }
    }

    private fun speakPhrase(phrase: String) {
        if (isTtsReady && tts != null) {
            isCurrentlySpeaking.set(true)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            }

            // Speak loudly on STREAM_ALARM
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "alert_speech")
        } else {
            isCurrentlySpeaking.set(false)
            _ttsErrorMessage.value = "Text-to-Speech unavailable. Loud warning beep active."
        }
    }

    /**
     * Stops both continuous beep and voice warnings when person disappears beyond grace period.
     */
    @Synchronized
    fun stopContinuousAlert() {
        _isAlarmActive.value = false
        beepJob?.cancel()
        beepJob = null
        voiceJob?.cancel()
        voiceJob = null
        isCurrentlySpeaking.set(false)
        activePersonId = null
        activePersonName = ""
        _currentCommentary.value = null
        stopAudio()
        abandonAudioFocus()
    }

    private fun vibratePulse() {
        try {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(
                            VibrationEffect.createOneShot(
                                180,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(180)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun stopAudio() {
        try {
            toneGeneratorNormal?.stopTone()
            toneGeneratorDucked?.stopTone()
            tts?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
    }

    fun clearTtsError() {
        _ttsErrorMessage.value = null
    }

    fun release() {
        stopContinuousAlert()
        try {
            toneGeneratorNormal?.release()
            toneGeneratorNormal = null
            toneGeneratorDucked?.release()
            toneGeneratorDucked = null
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio resources", e)
        }
    }
}
