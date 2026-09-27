package com.samhith.aurio.audio

import com.samhith.aurio.model.SongItem
import com.samhith.aurio.model.SpatialMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Common cross-platform audio controller interface.
 * Implemented by AudioPlayerManager (Android Media3) on Android and IosAudioPlayerManager (AVAudioEngine) on iOS.
 */
interface AudioPlayerController {
    val currentSong: StateFlow<SongItem?>
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val spatialMode: StateFlow<SpatialMode>

    fun play(song: SongItem)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun setSpatialMode(mode: SpatialMode)
    fun setSpatialRotationSeconds(seconds: Float)
    fun setSpatialIntensity(intensity: Float)
}
