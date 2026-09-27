package com.samhith.aurio.model

import kotlin.math.PI

/**
 * Cross-platform Spatial Audio Modes:
 * - OFF: Standard untouched high-fidelity stereo
 * - EIGHT_D: Whole track (vocals + dynamic punchy beat) travels in a continuous 360° binaural orbit
 * - SIXTEEN_D: Duality orbit — vocals on one side, beats on the opposite 180° side with 35% sub-bass center anchor
 */
enum class SpatialMode(val displayName: String) {
    OFF("Off"),
    EIGHT_D("8D"),
    SIXTEEN_D("16D")
}

data class SpatialAudioConfig(
    val mode: SpatialMode = SpatialMode.OFF,
    val rotationSeconds: Float = 12f,
    val intensity: Float = 0.85f
)

data class SpatialTelemetry(
    val vocalAngle: Float = 0f,
    val beatAngle: Float = PI.toFloat(),
    val airAngle: Float = (PI / 2).toFloat(),
    val lastBeatAtNanos: Long = 0L
)

