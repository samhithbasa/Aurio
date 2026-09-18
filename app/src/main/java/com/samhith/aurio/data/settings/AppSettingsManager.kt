package com.samhith.aurio.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.samhith.aurio.data.player.MusicHapticsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AppSettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aurio_app_settings", Context.MODE_PRIVATE)

    private val _streamingQuality = MutableStateFlow(
        prefs.getString("streaming_quality", "Lossless (320 kbps)") ?: "Lossless (320 kbps)"
    )
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(
        prefs.getString("download_quality", "High (320 kbps)") ?: "High (320 kbps)"
    )
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _isWifiOnlyStreaming = MutableStateFlow(
        prefs.getBoolean("wifi_only_streaming", false)
    )
    val isWifiOnlyStreaming: StateFlow<Boolean> = _isWifiOnlyStreaming.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(
        prefs.getString("equalizer_preset", "Aurio Dynamic Bass") ?: "Aurio Dynamic Bass"
    )
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(
        prefs.getInt("crossfade_seconds", 3)
    )
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _isBeatHapticsEnabled = MutableStateFlow(
        prefs.getBoolean("beat_haptics_enabled", true)
    )
    val isBeatHapticsEnabled: StateFlow<Boolean> = _isBeatHapticsEnabled.asStateFlow()

    fun setStreamingQuality(quality: String) {
        prefs.edit().putString("streaming_quality", quality).apply()
        _streamingQuality.value = quality
    }

    fun setDownloadQuality(quality: String) {
        prefs.edit().putString("download_quality", quality).apply()
        _downloadQuality.value = quality
    }

    fun setWifiOnlyStreaming(enabled: Boolean) {
        prefs.edit().putBoolean("wifi_only_streaming", enabled).apply()
        _isWifiOnlyStreaming.value = enabled
    }

    fun setEqualizerPreset(preset: String) {
        prefs.edit().putString("equalizer_preset", preset).apply()
        _equalizerPreset.value = preset
    }

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt("crossfade_seconds", seconds).apply()
        _crossfadeSeconds.value = seconds
    }

    fun setBeatHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("beat_haptics_enabled", enabled).apply()
        _isBeatHapticsEnabled.value = enabled
        MusicHapticsManager.getInstance(context).setEnabled(enabled)
    }

    fun getCacheSizeBytes(): Long {
        var size = 0L
        try {
            size += getDirSize(context.cacheDir)
            context.externalCacheDir?.let { size += getDirSize(it) }
        } catch (_: Exception) {}
        return size
    }

    fun clearCache(): Long {
        val initialSize = getCacheSizeBytes()
        try {
            deleteDir(context.cacheDir)
            context.externalCacheDir?.let { deleteDir(it) }
        } catch (_: Exception) {}
        return initialSize
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytes = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            bytes += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return bytes
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        val files = dir.listFiles() ?: return true
        for (file in files) {
            if (file.isDirectory) deleteDir(file) else file.delete()
        }
        return true
    }

    companion object {
        @Volatile
        private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
