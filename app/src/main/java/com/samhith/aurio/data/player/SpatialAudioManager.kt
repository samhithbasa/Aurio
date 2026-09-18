package com.samhith.aurio.data.player

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The "8D audio" effect people share online: an ordinary stereo song made to orbit the listener's
 * head. There is no 8D or 16D file format - it is an effect applied to any track - so [SpatialMode]
 * is simply how wide and how busy that orbit is.
 */
enum class SpatialMode(val displayName: String) {
    OFF("Off"),
    EIGHT_D("8D"),
    SIXTEEN_D("16D")
}

/**
 * Settings for the spatial effect, shared between the UI and [SpatialAudioProcessor].
 *
 * The effect only exists between two ears: through the phone speaker both ears hear both channels,
 * so the orbit collapses and the song just sounds thin. [isHeadphonesConnected] lets the UI say so.
 */
class SpatialAudioManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aurio_spatial_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        runCatching { SpatialMode.valueOf(prefs.getString(PREF_MODE, SpatialMode.OFF.name)!!) }
            .getOrDefault(SpatialMode.OFF)
    )
    val mode: StateFlow<SpatialMode> = _mode.asStateFlow()

    /** Seconds per full orbit. Lower is faster and more obvious. */
    private val _rotationSeconds = MutableStateFlow(prefs.getFloat(PREF_ROTATION, 12f))
    val rotationSeconds: StateFlow<Float> = _rotationSeconds.asStateFlow()

    /** 0..1: how far the sound swings and how much room is added around it. */
    private val _intensity = MutableStateFlow(prefs.getFloat(PREF_INTENSITY, 0.85f))
    val intensity: StateFlow<Float> = _intensity.asStateFlow()

    val isEnabled: Boolean
        get() = _mode.value != SpatialMode.OFF

    /**
     * Where the moving sound is right now (radians; 0 = front, +pi/2 = right), written by the audio
     * thread and read by the player's animation so the picture matches what the ears hear.
     */
    @Volatile
    var liveAngle: Float = 0f

    /** 16D only: position of the bouncing hi-hat / clap layer. */
    @Volatile
    var liveAirAngle: Float = (Math.PI / 2).toFloat()

    /** When the processor last detected a beat, for the player's pulse. */
    @Volatile
    var lastBeatAtNanos: Long = 0L

    fun setMode(mode: SpatialMode) {
        _mode.value = mode
        prefs.edit().putString(PREF_MODE, mode.name).apply()
    }

    fun setRotationSeconds(seconds: Float) {
        val clamped = seconds.coerceIn(MIN_ROTATION_SECONDS, MAX_ROTATION_SECONDS)
        _rotationSeconds.value = clamped
        prefs.edit().putFloat(PREF_ROTATION, clamped).apply()
    }

    fun setIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _intensity.value = clamped
        prefs.edit().putFloat(PREF_INTENSITY, clamped).apply()
    }

    /** True for wired or Bluetooth headphones: the only way the effect actually works. */
    fun isHeadphonesConnected(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_HEARING_AID -> true

                else -> false
            }
        }
    }

    companion object {
        const val MIN_ROTATION_SECONDS = 3f
        const val MAX_ROTATION_SECONDS = 20f

        private const val PREF_MODE = "spatial_mode"
        private const val PREF_ROTATION = "spatial_rotation_seconds"
        private const val PREF_INTENSITY = "spatial_intensity"

        @Volatile
        private var instance: SpatialAudioManager? = null

        fun getInstance(context: Context): SpatialAudioManager {
            return instance ?: synchronized(this) {
                instance ?: SpatialAudioManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
