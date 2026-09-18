package com.samhith.aurio.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.data.player.HapticIntensity
import com.samhith.aurio.data.player.MusicHapticsManager
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicHapticsDialog(
    musicHapticsManager: MusicHapticsManager,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled by musicHapticsManager.isEnabled.collectAsState()
    val intensity by musicHapticsManager.intensity.collectAsState()
    val isBeatActive by musicHapticsManager.isBeatActive.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Animated scale for live beat pulse indicator
    val beatScale by animateFloatAsState(
        targetValue = if (isBeatActive && isEnabled && isPlaying) 1.25f else 1.0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "BeatScaleAnimation"
    )

    val beatGlowAlpha by animateFloatAsState(
        targetValue = if (isBeatActive && isEnabled && isPlaying) 0.6f else 0.15f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "BeatGlowAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppleSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppleFill)
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AppleBlue.copy(alpha = 0.25f), AppleBlue.copy(alpha = 0.15f))
                                )
                            )
                            .border(1.dp, AppleBlue.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Music Haptics",
                            tint = AppleBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Music Haptics",
                            color = AppleLabel,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Vibrate in sync with beats & bass",
                            color = AppleSecondaryLabel,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(AppleFill, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Master On/Off Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppleSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isEnabled) AppleBlue.copy(alpha = 0.5f) else AppleSeparator
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Beat Vibration",
                            color = AppleLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEnabled) {
                                if (isPlaying) "Active & pulsing to music rhythms" else "Enabled (waiting for playback)"
                            } else {
                                "Turned off (saves battery)"
                            },
                            color = if (isEnabled) AppleBlue else AppleSecondaryLabel,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { musicHapticsManager.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppleOnAccent,
                            checkedTrackColor = AppleBlue,
                            uncheckedThumbColor = AppleFill,
                            uncheckedTrackColor = AppleFill,
                            uncheckedBorderColor = AppleSeparator
                        )
                    )
                }
            }

            // Live Beat Indicator Animation (Visible when Enabled)
            AnimatedVisibility(
                visible = isEnabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppleSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppleSeparator)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing Ring Graphic
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(beatScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                AppleBlue.copy(alpha = beatGlowAlpha),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .border(
                                        width = if (isBeatActive && isPlaying) 2.dp else 1.dp,
                                        color = if (isBeatActive && isPlaying) AppleBlue else AppleFill,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = if (isBeatActive && isPlaying) AppleLabel else AppleBlue.copy(alpha = 0.7f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPlaying) "Real-Time Beat Sync" else "Playback Paused",
                                    color = AppleLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = if (isPlaying) "Synthesizing low-end kicks & transients" else "Press play to feel the rhythm in your hands",
                                    color = AppleSecondaryLabel,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Intensity Selector Section
            Text(
                text = "HAPTIC INTENSITY",
                color = AppleBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HapticIntensity.entries.forEach { option ->
                    val isSelected = intensity == option

                    val backgroundModifier = if (isSelected) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(AppleBlue.copy(alpha = 0.35f), AppleBlue.copy(alpha = 0.2f))
                            )
                        )
                    } else {
                        Modifier.background(AppleFill)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .then(backgroundModifier)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AppleBlue else AppleSeparator,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                musicHapticsManager.setIntensity(option)
                            }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = option.displayName,
                                color = if (isSelected) AppleLabel else AppleSecondaryLabel,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (option) {
                                    HapticIntensity.GENTLE -> "Light tap"
                                    HapticIntensity.MEDIUM -> "Balanced"
                                    HapticIntensity.STRONG -> "Deep bass"
                                },
                                color = if (isSelected) AppleBlue else AppleGray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Vibration Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleFill)
                    .border(1.dp, AppleSeparator, RoundedCornerShape(12.dp))
                    .clickable {
                        musicHapticsManager.testVibration()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Test",
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Test ${intensity.displayName} Vibration Pulse",
                        color = AppleLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Educational Info Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppleSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, AppleSeparator, RoundedCornerShape(10.dp))
                .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AppleSecondaryLabel,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Music Haptics uses real-time low-frequency audio analysis to fire haptic taps on kick drums & bass drops. Turn off anytime to conserve battery.",
                    color = AppleSecondaryLabel,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
