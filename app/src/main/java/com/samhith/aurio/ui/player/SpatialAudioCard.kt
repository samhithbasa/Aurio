package com.samhith.aurio.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.data.player.SpatialAudioManager
import com.samhith.aurio.data.player.SpatialMode
import com.samhith.aurio.ui.dialogs.SpatialModeSelector
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSurface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spatial Audio control in the player: a live view of where the sound is around your head, the
 * mode selector, and a shortcut into the full settings.
 *
 * The dots are driven by the audio engine itself, so they hop exactly when the sound hops, and the
 * head pulses on every beat the engine detects.
 */
@Composable
fun SpatialAudioCard(
    spatialAudioManager: SpatialAudioManager,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode by spatialAudioManager.mode.collectAsState()
    val isActive = mode != SpatialMode.OFF

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleSurface.copy(alpha = 0.9f))
            .border(
                width = 1.dp,
                color = if (isActive) AppleBlue.copy(alpha = 0.35f) else AppleSeparator,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveOrbit(
                spatialAudioManager = spatialAudioManager,
                mode = mode,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                CardText(
                    text = "Spatial Audio",
                    color = AppleLabel,
                    size = 13.5,
                    weight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(1.dp))
                CardText(
                    text = when (mode) {
                        SpatialMode.OFF -> "Best with headphones"
                        SpatialMode.EIGHT_D -> "Travels around you with the beat"
                        SpatialMode.SIXTEEN_D -> "Vocals orbit · beats bounce ear to ear"
                    },
                    color = if (isActive) AppleBlue else AppleSecondaryLabel,
                    size = 11.0,
                    weight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = isActive, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleFill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenSettings
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    CardText(text = "Adjust", color = AppleBlue, size = 11.5, weight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SpatialModeSelector(
            selected = mode,
            onSelect = { spatialAudioManager.setMode(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** What the audio engine is doing right now, sampled once per frame. */
private data class OrbitFrame(val angle: Float, val airAngle: Float, val beatGlow: Float)

/**
 * Top-down view of the listener: the ring is the path around the head, the dot is the sound.
 * Angle 0 is in front (top), +90 degrees is the right ear.
 */
@Composable
private fun LiveOrbit(
    spatialAudioManager: SpatialAudioManager,
    mode: SpatialMode,
    modifier: Modifier = Modifier
) {
    val isActive = mode != SpatialMode.OFF
    val frame by produceState(OrbitFrame(0f, (Math.PI / 2).toFloat(), 0f), isActive) {
        while (isActive) {
            withFrameNanos { now ->
                val sinceBeat = (now - spatialAudioManager.lastBeatAtNanos).coerceAtLeast(0L)
                // Bright on the beat, fading over ~250 ms
                val glow = (1f - sinceBeat / BEAT_GLOW_NANOS).coerceIn(0f, 1f)
                value = OrbitFrame(spatialAudioManager.liveAngle, spatialAudioManager.liveAirAngle, glow)
            }
        }
    }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbitRadius = radius * 0.78f

        drawCircle(
            color = if (isActive) AppleBlue.copy(alpha = 0.25f + 0.25f * frame.beatGlow) else AppleSeparator,
            radius = orbitRadius,
            center = center,
            style = Stroke(width = radius * 0.08f)
        )

        // The listener's head: swells a little on each beat
        drawCircle(
            color = if (isActive) AppleBlue.copy(alpha = 0.18f + 0.3f * frame.beatGlow) else AppleFill,
            radius = radius * (0.24f + 0.07f * frame.beatGlow),
            center = center
        )

        if (!isActive) return@Canvas

        fun positionOf(angle: Float) = Offset(
            x = center.x + orbitRadius * sin(angle),
            y = center.y - orbitRadius * cos(angle)
        )

        if (mode == SpatialMode.SIXTEEN_D) {
            drawSound(positionOf(frame.airAngle), radius, AppleBlue.copy(alpha = 0.6f), frame.beatGlow)
        }
        drawSound(positionOf(frame.angle), radius, AppleBlue, frame.beatGlow)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSound(
    position: Offset,
    radius: Float,
    color: Color,
    beatGlow: Float
) {
    drawCircle(
        color = color.copy(alpha = color.alpha * (0.18f + 0.22f * beatGlow)),
        radius = radius * (0.28f + 0.12f * beatGlow),
        center = position
    )
    drawCircle(color = color, radius = radius * 0.14f, center = position)
}

@Composable
private fun CardText(text: String, color: Color, size: Double, weight: FontWeight) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
        maxLines = 1
    )
}

private const val BEAT_GLOW_NANOS = 250_000_000f
