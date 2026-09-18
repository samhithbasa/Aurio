package com.samhith.aurio.data.player

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.audiofx.HapticGenerator
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.sqrt

enum class HapticIntensity(val displayName: String, val pulseDurationMs: Long, val amplitude: Int) {
    GENTLE("Gentle", 40L, 180),
    MEDIUM("Standard", 60L, 225),
    STRONG("Strong", 85L, 255)
}

/**
 * Singleton manager providing Apple Music-style "Music Haptics" on Android.
 * Analyzes audio in real-time via FFT & Waveform Visualizer + HapticGenerator to trigger
 * punchy, beat-synchronized vibrations for kick drums, basslines, and rhythmic accents.
 */
class MusicHapticsManager private constructor(private val context: Context) {

    private val TAG = "MusicHapticsManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("aurio_haptics_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Hardware Vibrator
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Audio Visualizer & Hardware HapticGenerator
    private var visualizer: Visualizer? = null
    private var hapticGenerator: HapticGenerator? = null
    private var currentAudioSessionId: Int = 0
    private var isPlaying: Boolean = false

    // State Flows
    private val _isEnabled = MutableStateFlow(prefs.getBoolean("haptics_enabled", false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _intensity = MutableStateFlow(
        try {
            HapticIntensity.valueOf(prefs.getString("haptics_intensity", HapticIntensity.MEDIUM.name) ?: HapticIntensity.MEDIUM.name)
        } catch (_: Exception) {
            HapticIntensity.MEDIUM
        }
    )
    val intensity: StateFlow<HapticIntensity> = _intensity.asStateFlow()

    // Real-time reactive beat event flag for UI pulse animations
    private val _isBeatActive = MutableStateFlow(false)
    val isBeatActive: StateFlow<Boolean> = _isBeatActive.asStateFlow()

    // Beat Detection Math State
    private var averageBassEnergy: Float = 0f
    private var averageWaveformRms: Float = 0f
    private var lastBeatTimestamp: Long = 0L
    private var beatResetJob: Job? = null

    companion object {
        @Volatile
        private var instance: MusicHapticsManager? = null

        fun getInstance(context: Context): MusicHapticsManager {
            return instance ?: synchronized(this) {
                instance ?: MusicHapticsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Binds the haptics engine to the active ExoPlayer audio session ID.
     */
    fun bindAudioSession(audioSessionId: Int) {
        Log.d(TAG, "bindAudioSession called with audioSessionId: $audioSessionId, isEnabled=${_isEnabled.value}")
        if (audioSessionId <= 0) return
        if (audioSessionId == currentAudioSessionId && visualizer != null) return

        currentAudioSessionId = audioSessionId
        releaseAudioFx()

        if (!_isEnabled.value) return

        initializeHapticEngine(audioSessionId)
    }

    /**
     * Updates the playback state to pause/resume vibration processing.
     */
    fun onPlaybackStateChanged(playing: Boolean) {
        Log.d(TAG, "onPlaybackStateChanged: $playing")
        isPlaying = playing
        if (!playing) {
            _isBeatActive.value = false
        }
    }

    /**
     * Receives direct 16-bit PCM bass analysis from ExoPlayer's HapticBeatAudioProcessor.
     * Fires beat-synchronized haptics directly from the decoded audio pipeline.
     */
    fun onPcmBassSample(rmsBass: Float) {
        if (!_isEnabled.value || !isPlaying) return

        if (averageBassEnergy == 0f) {
            averageBassEnergy = rmsBass
            return
        }

        val now = System.currentTimeMillis()
        val minInterval = when (_intensity.value) {
            HapticIntensity.GENTLE -> 140L
            HapticIntensity.MEDIUM -> 115L
            HapticIntensity.STRONG -> 95L
        }

        val threshold = when (_intensity.value) {
            HapticIntensity.GENTLE -> 1.35f
            HapticIntensity.MEDIUM -> 1.25f
            HapticIntensity.STRONG -> 1.18f
        }

        if (rmsBass > averageBassEnergy * threshold && rmsBass > 0.012f && (now - lastBeatTimestamp) > minInterval) {
            lastBeatTimestamp = now
            val currentIntensity = _intensity.value
            triggerVibration(currentIntensity.pulseDurationMs, currentIntensity.amplitude)

            _isBeatActive.value = true
            beatResetJob?.cancel()
            beatResetJob = scope.launch {
                delay(80)
                _isBeatActive.value = false
            }
        }

        averageBassEnergy = averageBassEnergy * 0.90f + rmsBass * 0.10f
    }

    /**
     * Toggles music haptics On or Off.
     */
    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }

    /**
     * Explicitly sets music haptics enabled state.
     */
    fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "setEnabled: $enabled")
        _isEnabled.value = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()

        if (enabled) {
            testVibration()
            if (currentAudioSessionId > 0) {
                initializeHapticEngine(currentAudioSessionId)
            }
        } else {
            releaseAudioFx()
            _isBeatActive.value = false
        }
    }

    /**
     * Sets the haptic vibration intensity (GENTLE, MEDIUM, STRONG).
     */
    fun setIntensity(newIntensity: HapticIntensity) {
        _intensity.value = newIntensity
        prefs.edit().putString("haptics_intensity", newIntensity.name).apply()
        testVibration()
    }

    /**
     * Fires a double-pulse sample haptic tap with the current intensity.
     */
    fun testVibration() {
        val currentIntensity = _intensity.value
        Log.d(TAG, "testVibration: ${currentIntensity.displayName}, dur=${currentIntensity.pulseDurationMs}ms, amp=${currentIntensity.amplitude}")

        scope.launch {
            triggerVibration(currentIntensity.pulseDurationMs, currentIntensity.amplitude)
            _isBeatActive.value = true
            delay(currentIntensity.pulseDurationMs + 40)
            triggerVibration(currentIntensity.pulseDurationMs, currentIntensity.amplitude)
            delay(100)
            _isBeatActive.value = false
        }
    }

    private fun initializeHapticEngine(audioSessionId: Int) {
        try {
            // 1. Check if Android 12+ Audio-Coupled HapticGenerator is supported by hardware
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && HapticGenerator.isAvailable()) {
                hapticGenerator = HapticGenerator.create(audioSessionId).apply {
                    enabled = true
                }
                Log.d(TAG, "Hardware HapticGenerator initialized for session: $audioSessionId")
            }

            // 2. Initialize Visualizer with both Waveform and FFT capture
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = if (captureSizeRange != null && captureSizeRange.isNotEmpty()) {
                captureSizeRange[0].coerceAtLeast(128)
            } else 128

            visualizer = Visualizer(audioSessionId).apply {
                this.captureSize = captureSize
                val maxRate = Visualizer.getMaxCaptureRate()
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            if (waveform != null && isPlaying && _isEnabled.value) {
                                processWaveformForBeat(waveform)
                            }
                        }

                        override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null && isPlaying && _isEnabled.value) {
                                processFftForBeat(fft)
                            }
                        }
                    },
                    maxRate,
                    true,  // waveform capture enabled
                    true   // FFT capture enabled
                )
                enabled = true
            }
            Log.d(TAG, "Visualizer Beat Engine successfully bound and enabled on session: $audioSessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing Haptic Engine: ${e.message}")
        }
    }

    /**
     * Processes raw waveform samples to detect beat kicks.
     */
    private fun processWaveformForBeat(waveform: ByteArray) {
        try {
            if (waveform.isEmpty()) return

            var sumSquares = 0.0
            for (i in waveform.indices) {
                val sample = (waveform[i].toInt() and 0xFF) - 128
                sumSquares += (sample * sample)
            }
            val rms = sqrt(sumSquares / waveform.size).toFloat()

            if (averageWaveformRms == 0f) {
                averageWaveformRms = rms
                return
            }

            val now = System.currentTimeMillis()
            val minInterval = when (_intensity.value) {
                HapticIntensity.GENTLE -> 140L
                HapticIntensity.MEDIUM -> 120L
                HapticIntensity.STRONG -> 100L
            }

            if (rms > averageWaveformRms * 1.30f && (now - lastBeatTimestamp) > minInterval) {
                lastBeatTimestamp = now
                val currentIntensity = _intensity.value
                triggerVibration(currentIntensity.pulseDurationMs, currentIntensity.amplitude)

                _isBeatActive.value = true
                beatResetJob?.cancel()
                beatResetJob = scope.launch {
                    delay(80)
                    _isBeatActive.value = false
                }
            }

            averageWaveformRms = averageWaveformRms * 0.85f + rms * 0.15f
        } catch (_: Exception) {}
    }

    /**
     * Processes FFT frequency bins to detect sub-bass/bass kick drum beats.
     */
    private fun processFftForBeat(fft: ByteArray) {
        try {
            if (fft.isEmpty()) return

            var currentBassEnergy = 0f
            val maxBins = (fft.size / 2).coerceAtMost(8)

            for (i in 1 until maxBins) {
                val real = fft[2 * i].toFloat()
                val imag = fft[2 * i + 1].toFloat()
                val magnitude = hypot(real, imag)
                currentBassEnergy += magnitude
            }

            if (averageBassEnergy == 0f) {
                averageBassEnergy = currentBassEnergy
                return
            }

            val now = System.currentTimeMillis()
            val minInterval = when (_intensity.value) {
                HapticIntensity.GENTLE -> 140L
                HapticIntensity.MEDIUM -> 120L
                HapticIntensity.STRONG -> 100L
            }

            if (currentBassEnergy > averageBassEnergy * 1.32f && (now - lastBeatTimestamp) > minInterval) {
                lastBeatTimestamp = now
                val currentIntensity = _intensity.value
                triggerVibration(currentIntensity.pulseDurationMs, currentIntensity.amplitude)

                _isBeatActive.value = true
                beatResetJob?.cancel()
                beatResetJob = scope.launch {
                    delay(80)
                    _isBeatActive.value = false
                }
            }

            averageBassEnergy = averageBassEnergy * 0.88f + currentBassEnergy * 0.12f
        } catch (_: Exception) {}
    }

    /**
     * Dispatches vibration pulse to the phone's vibrator using robust multi-attribute fallback.
     */
    private fun triggerVibration(durationMs: Long, amplitude: Int) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator hardware on device")
            return
        }

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
                val vibAttributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA)
                    .build()
                val effect = if (vibrator.hasAmplitudeControl() && amplitude in 1..255) {
                    VibrationEffect.createOneShot(durationMs, amplitude)
                } else {
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect, vibAttributes)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
                val effect = if (vibrator.hasAmplitudeControl() && amplitude in 1..255) {
                    VibrationEffect.createOneShot(durationMs, amplitude)
                } else {
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect, audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs, audioAttributes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration with USAGE_MEDIA failed, attempting fallback: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "All vibration dispatch methods failed: ${e2.message}")
            }
        }
    }

    private fun releaseAudioFx() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        } catch (_: Exception) {}

        try {
            hapticGenerator?.enabled = false
            hapticGenerator?.release()
            hapticGenerator = null
        } catch (_: Exception) {}
    }

    fun release() {
        releaseAudioFx()
        currentAudioSessionId = 0
    }
}
