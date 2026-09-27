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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import com.samhith.aurio.data.player.EqualizerManager
import com.samhith.aurio.data.player.EqualizerPreset
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayPrimaryGradient

/**
 * 5-Band AudioFX Equalizer dialog with preset selector chips,
 * interactive frequency sliders (-10 dB to +10 dB), and dynamic Bass Boost control.
 */
@Composable
fun EqualizerDialog(
    equalizerManager: EqualizerManager,
    onDismiss: () -> Unit
) {
    val isEnabled by equalizerManager.isEnabled.collectAsState()
    val currentPreset by equalizerManager.currentPreset.collectAsState()
    val bandLevels by equalizerManager.bandLevels.collectAsState()
    val bassBoost by equalizerManager.bassBoostLevel.collectAsState()

    val bandFrequencies = remember {
        listOf("60Hz", "230Hz", "910Hz", "3.6k", "14kHz")
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClaySurface,
                            ClayBackground
                        )
                    )
                )
                .border(1.2.dp, ClayInset, RoundedCornerShape(24.dp))
                .aurioGlow(ClayPrimary, alpha = 0.25f, blurRadius = 24.dp)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Header with Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ClayInset),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Equalizer",
                                tint = ClayPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Audio Equalizer",
                            color = ClayLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { equalizerManager.setEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ClayPrimary,
                                uncheckedThumbColor = ClayInset,
                                uncheckedTrackColor = ClayInset
                            )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = ClaySecondaryLabel,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Preset Selection Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EqualizerPreset.values()) { preset ->
                        val isSelected = currentPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.background(ClayPrimaryGradient)
                                    } else {
                                        Modifier
                                            .background(ClaySurface)
                                            .border(1.dp, ClayInset, RoundedCornerShape(16.dp))
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = isEnabled,
                                    onClick = { equalizerManager.setPreset(preset) }
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.displayName,
                                color = if (isSelected) Color.White else if (isEnabled) ClaySecondaryLabel else ClaySecondaryLabel,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ClayInset, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // 3. 5-Band Equalizer Sliders
                Text(
                    text = "FREQUENCY BANDS (-10dB TO +10dB)",
                    color = ClaySecondaryLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bandFrequencies.forEachIndexed { index, freq ->
                        val level = bandLevels.getOrElse(index) { 0 }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (level > 0) "+$level" else "$level",
                                color = if (isEnabled) ClayPrimary else ClaySecondaryLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Compact vertical slider representation
                            Slider(
                                value = level.toFloat(),
                                onValueChange = {
                                    equalizerManager.setBandLevel(index, it.toInt())
                                },
                                valueRange = -10f..10f,
                                steps = 19,
                                enabled = isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = ClayPrimary,
                                    activeTrackColor = ClayPrimary,
                                    inactiveTrackColor = ClayInset
                                ),
                                modifier = Modifier.height(110.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = freq,
                                color = if (isEnabled) ClaySecondaryLabel else ClaySecondaryLabel,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ClayInset, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // 4. Bass Boost Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass Boost Level",
                        color = ClayLabel,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "$bassBoost%",
                        color = ClayPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Slider(
                    value = bassBoost.toFloat(),
                    onValueChange = { equalizerManager.setBassBoost(it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = ClayPrimary,
                        activeTrackColor = ClayPrimary,
                        inactiveTrackColor = ClayInset
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
