package com.samhith.aurio.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.data.player.SpatialAudioManager
import com.samhith.aurio.data.player.SpatialMode
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClaySurface
import kotlin.math.roundToInt

/**
 * Settings for the 8D / 16D spatial effect: which orbit, how fast, and how strong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialAudioDialog(
    spatialAudioManager: SpatialAudioManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode by spatialAudioManager.mode.collectAsState()
    val rotationSeconds by spatialAudioManager.rotationSeconds.collectAsState()
    val intensity by spatialAudioManager.intensity.collectAsState()
    val headphonesConnected = remember(mode) { spatialAudioManager.isHeadphonesConnected() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ClaySurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ClayInset)
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ClayPrimary.copy(alpha = 0.25f), ClayPrimary.copy(alpha = 0.15f))
                            )
                        )
                        .border(1.dp, ClayPrimary.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SpatialAudio,
                        contentDescription = "Spatial Audio",
                        tint = ClayPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Spatial Audio",
                        color = ClayLabel,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Travels around your head with the beat",
                        color = ClaySecondaryLabel,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            SpatialModeSelector(
                selected = mode,
                onSelect = { spatialAudioManager.setMode(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (!headphonesConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClayInset)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = ClaySecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Connect headphones to hear the effect. Through the speaker both ears " +
                                "hear everything, so the movement disappears.",
                        color = ClaySecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            SpatialSlider(
                title = "Drift between beats",
                // Shown as seconds per full circle, so the number matches what the ear hears
                value = "${rotationSeconds.roundToInt()}s per circle",
                sliderValue = SpatialAudioManager.MAX_ROTATION_SECONDS +
                        SpatialAudioManager.MIN_ROTATION_SECONDS - rotationSeconds,
                valueRange = SpatialAudioManager.MIN_ROTATION_SECONDS..SpatialAudioManager.MAX_ROTATION_SECONDS,
                enabled = mode != SpatialMode.OFF,
                onValueChange = {
                    // Slider runs left-to-right as "slower to faster", so invert it back
                    spatialAudioManager.setRotationSeconds(
                        SpatialAudioManager.MAX_ROTATION_SECONDS +
                                SpatialAudioManager.MIN_ROTATION_SECONDS - it
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            SpatialSlider(
                title = "Strength",
                value = "${(intensity * 100).roundToInt()}%",
                sliderValue = intensity,
                valueRange = 0f..1f,
                enabled = mode != SpatialMode.OFF,
                onValueChange = { spatialAudioManager.setIntensity(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** iOS-style segmented control: a light track with the chosen segment filled in blue. */
@Composable
fun SpatialModeSelector(
    selected: SpatialMode,
    onSelect: (SpatialMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ClayInset)
            .border(1.dp, ClayInset, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SpatialMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ClayPrimary else ClaySurface.copy(alpha = 0f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(mode) }
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) Color.White else ClaySecondaryLabel,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
private fun SpatialSlider(
    title: String,
    value: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = if (enabled) ClayLabel else ClaySecondaryLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = value,
                color = ClaySecondaryLabel,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = ClayPrimary,
                activeTrackColor = ClayPrimary,
                inactiveTrackColor = ClayInset,
                disabledThumbColor = ClayInset,
                disabledActiveTrackColor = ClayInset,
                disabledInactiveTrackColor = ClayInset
            )
        )
    }
}
