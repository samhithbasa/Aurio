package com.samhith.aurio.data.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EqualizerPreset(val displayName: String) {
    FLAT("Flat"),
    POP("Pop"),
    ROCK("Rock"),
    HIPHOP("Hip-Hop"),
    DANCE("Dance"),
    ACOUSTIC("Acoustic"),
    BASS_BOOST("Bass Boost"),
    CUSTOM("Custom")
}

/**
 * Manages Android AudioFX Equalizer and BassBoost attached to ExoPlayer's audio session.
 * Supports 5 frequency bands, bass boost level, preset selection, and SharedPreferences persistence.
 */
class EqualizerManager private constructor(private val context: Context) {

    private val TAG = "EqualizerManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("aurio_equalizer_prefs", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentAudioSessionId: Int = 0

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("eq_enabled", true))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPreset = MutableStateFlow(
        try {
            EqualizerPreset.valueOf(prefs.getString("eq_preset", EqualizerPreset.POP.name) ?: EqualizerPreset.POP.name)
        } catch (_: Exception) {
            EqualizerPreset.POP
        }
    )
    val currentPreset: StateFlow<EqualizerPreset> = _currentPreset.asStateFlow()

    // 5 Bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (normalized -10 to +10 dB)
    private val _bandLevels = MutableStateFlow(
        listOf(
            prefs.getInt("eq_band_0", 2),
            prefs.getInt("eq_band_1", 4),
            prefs.getInt("eq_band_2", 1),
            prefs.getInt("eq_band_3", 3),
            prefs.getInt("eq_band_4", 5)
        )
    )
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(prefs.getInt("eq_bass_boost", 65)) // 0..100 %
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    fun bindAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentAudioSessionId) return
        currentAudioSessionId = audioSessionId
        release()

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _isEnabled.value
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _isEnabled.value
                setStrength((_bassBoostLevel.value * 10).toShort().coerceIn(0, 1000))
            }

            applyBandLevels(_bandLevels.value)
            Log.d(TAG, "Equalizer & BassBoost bound to audioSessionId: $audioSessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing Equalizer: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Error setting EQ enabled: ${e.message}")
        }
    }

    fun setPreset(preset: EqualizerPreset) {
        _currentPreset.value = preset
        prefs.edit().putString("eq_preset", preset.name).apply()

        val levels = when (preset) {
            EqualizerPreset.FLAT -> listOf(0, 0, 0, 0, 0)
            EqualizerPreset.POP -> listOf(2, 4, 1, 3, 5)
            EqualizerPreset.ROCK -> listOf(5, 3, -1, 4, 6)
            EqualizerPreset.HIPHOP -> listOf(7, 5, 0, 2, 4)
            EqualizerPreset.DANCE -> listOf(6, 3, 0, 4, 6)
            EqualizerPreset.ACOUSTIC -> listOf(3, 2, 2, 4, 3)
            EqualizerPreset.BASS_BOOST -> listOf(9, 7, 2, 1, 2)
            EqualizerPreset.CUSTOM -> _bandLevels.value
        }

        val bass = when (preset) {
            EqualizerPreset.FLAT -> 0
            EqualizerPreset.POP -> 60
            EqualizerPreset.ROCK -> 75
            EqualizerPreset.HIPHOP -> 85
            EqualizerPreset.DANCE -> 80
            EqualizerPreset.ACOUSTIC -> 30
            EqualizerPreset.BASS_BOOST -> 95
            EqualizerPreset.CUSTOM -> _bassBoostLevel.value
        }

        setBandLevels(levels, isPresetChange = true)
        setBassBoost(bass)
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        val current = _bandLevels.value.toMutableList()
        if (bandIndex in 0 until current.size) {
            current[bandIndex] = level.coerceIn(-10, 10)
            _currentPreset.value = EqualizerPreset.CUSTOM
            prefs.edit().putString("eq_preset", EqualizerPreset.CUSTOM.name).apply()
            setBandLevels(current)
        }
    }

    private fun setBandLevels(levels: List<Int>, isPresetChange: Boolean = false) {
        _bandLevels.value = levels
        for (i in levels.indices) {
            prefs.edit().putInt("eq_band_$i", levels[i]).apply()
        }
        applyBandLevels(levels)
    }

    private fun applyBandLevels(levels: List<Int>) {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minRange = eq.bandLevelRange?.get(0)?.toInt() ?: -1500
            val maxRange = eq.bandLevelRange?.get(1)?.toInt() ?: 1500

            for (i in 0 until minOf(numBands, levels.size)) {
                // Map -10..+10 to minRange..maxRange millibels
                val normalized = levels[i] / 10f // -1.0 .. 1.0
                val millibels = if (normalized >= 0) {
                    (normalized * maxRange).toInt().toShort()
                } else {
                    (normalized * -minRange).toInt().toShort()
                }
                eq.setBandLevel(i.toShort(), millibels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band levels: ${e.message}")
        }
    }

    fun setBassBoost(levelPercent: Int) {
        val clamped = levelPercent.coerceIn(0, 100)
        _bassBoostLevel.value = clamped
        prefs.edit().putInt("eq_bass_boost", clamped).apply()
        try {
            bassBoost?.setStrength((clamped * 10).toShort().coerceIn(0, 1000))
        } catch (e: Exception) {
            Log.w(TAG, "Error setting BassBoost: ${e.message}")
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
    }

    companion object {
        @Volatile
        private var instance: EqualizerManager? = null

        fun getInstance(context: Context): EqualizerManager {
            return instance ?: synchronized(this) {
                instance ?: EqualizerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
