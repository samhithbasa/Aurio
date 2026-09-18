package com.samhith.aurio.ui.ai

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.data.ai.AssistantTrainingState
import com.samhith.aurio.data.ai.AssistantWakeState
import kotlin.math.sin
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleBlueLight
import com.samhith.aurio.ui.theme.AppleOnAccent

/**
 * Apple Siri-style bottom floating sound wave overlay with personalized AI Assistant Name training.
 * Pops up whenever "Hey [AssistantName]" is spoken or triggered, showing fluid glowing soundwaves,
 * live speech transcripts, 3-step voice training indicators, and mood prompts.
 */
@Composable
fun SiriWaveAssistantOverlay(
    isVisible: Boolean,
    state: AssistantWakeState,
    assistantName: String = "Aurio",
    trainingState: AssistantTrainingState = AssistantTrainingState(),
    liveTranscript: String,
    responseMessage: String?,
    rmsVolume: Float,
    onDismiss: () -> Unit,
    onChipClick: (String) -> Unit,
    onConfirmCustomName: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val assistantBitmap = remember {
        try {
            val stream = try {
                context.assets.open("Ai_Assistant.png")
            } catch (_: Exception) {
                context.assets.open("Ai_Assistannt.png")
            }
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    // Siri Wave animations
    val infiniteTransition = rememberInfiniteTransition(label = "SiriWaveAnim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Siri Floating Pill Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = AppleBlue,
                        ambientColor = AppleBlue
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                AppleSurface.copy(alpha = 0.94f),
                                AppleSurface.copy(alpha = 0.96f),
                                AppleBackground
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AppleBlue,
                                AppleBlue,
                                AppleBlueLight,
                                AppleBlue
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar: AI Avatar, Status Badge & Dismiss Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Avatar Icon with glow
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .scale(glowPulse)
                                    .clip(CircleShape)
                                    .border(1.2.dp, AppleBlue, CircleShape)
                                    .background(AppleSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (assistantBitmap != null) {
                                    Image(
                                        bitmap = assistantBitmap,
                                        contentDescription = "$assistantName AI",
                                        modifier = Modifier.size(38.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "$assistantName AI",
                                        tint = AppleBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (trainingState.isTraining) "Name Your AI" else "$assistantName AI",
                                        color = AppleLabel,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AppleBlue.copy(alpha = 0.25f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                trainingState.isTraining -> "STEP ${trainingState.step.coerceIn(1, 3)}/3"
                                                state == AssistantWakeState.AWAKE_LISTENING -> "LISTENING"
                                                state == AssistantWakeState.PROCESSING -> "THINKING"
                                                state == AssistantWakeState.SPEAKING -> "DONE"
                                                else -> "READY"
                                            },
                                            color = AppleBlue,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.6.sp
                                        )
                                    }
                                }
                                Text(
                                    text = if (trainingState.isTraining) "3-Step Voice Training" else "Tap-to-Talk Music Intelligence",
                                    color = AppleSecondaryLabel,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(AppleFill)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = AppleSecondaryLabel,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Siri Waveform Visualizer
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(32.dp)
                    ) {
                        val barCount = 13
                        for (i in 0 until barCount) {
                            val factor = sin((wavePhase * 2 * Math.PI + i * 0.5).toDouble()).toFloat()
                            val barHeight = when {
                                state == AssistantWakeState.AWAKE_LISTENING -> (8f + (rmsVolume * 22f) + (factor * 7f).coerceAtLeast(0f)).dp
                                state == AssistantWakeState.SPEAKING -> (6f + (factor * 16f).coerceAtLeast(3f)).dp
                                state == AssistantWakeState.PROCESSING -> (8f + (factor * 9f).coerceAtLeast(2f)).dp
                                trainingState.isTraining -> (8f + (rmsVolume * 18f) + (factor * 6f).coerceAtLeast(0f)).dp
                                else -> 5.dp
                            }

                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(barHeight.coerceIn(4.dp, 28.dp))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                AppleBlue,
                                                AppleBlue,
                                                AppleBlueLight
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (trainingState.isTraining) {
                        // Training Mode UI
                        AssistantTrainingView(
                            trainingState = trainingState,
                            onConfirmName = onConfirmCustomName
                        )
                    } else {
                        // Normal Assistant Mode UI
                        val displayText = when {
                            !responseMessage.isNullOrBlank() -> responseMessage
                            liveTranscript.isNotBlank() -> "\"$liveTranscript\""
                            state == AssistantWakeState.AWAKE_LISTENING -> "Listening... try 'Play Starboy', 'Shuffle on' or 'Show lyrics'"
                            state == AssistantWakeState.PROCESSING -> "Finding music..."
                            else -> "Tap the bubble anytime to talk to $assistantName"
                        }

                        Text(
                            text = displayText,
                            color = if (!responseMessage.isNullOrBlank()) AppleLabel else AppleBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Vibe / Action Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickChip("🔥 Workout") { onChipClick("Play workout hits") }
                            QuickChip("🌙 Chill Lofi") { onChipClick("Play chill lofi music") }
                            QuickChip("🎉 Party Bangers") { onChipClick("Play party dance hits") }
                            QuickChip("💔 Sad Songs") { onChipClick("Play sad emotional songs") }
                            QuickChip("⚡ Haptics On") { onChipClick("Turn on haptics") }
                            QuickChip("⏭️ Next Track") { onChipClick("Next song") }
                            QuickChip("⏸️ Pause") { onChipClick("Pause") }
                            QuickChip("🔀 Shuffle On") { onChipClick("Shuffle on") }
                            QuickChip("🎤 Lyrics") { onChipClick("Show lyrics") }
                            QuickChip("🎚️ Equalizer") { onChipClick("Open equalizer") }
                            QuickChip("😴 Sleep 30m") { onChipClick("Sleep timer 30 minutes") }
                            QuickChip("❤️ Like") { onChipClick("Like this song") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantTrainingView(
    trainingState: AssistantTrainingState,
    onConfirmName: (String) -> Unit
) {
    var textInput by remember(trainingState.proposedName) {
        mutableStateOf(trainingState.proposedName)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Dots (1/3, 2/3, 3/3)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (stepIndex in 1..3) {
                val isCurrent = trainingState.step == stepIndex
                val isCompleted = trainingState.step > stepIndex

                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> AppleBlue
                                isCurrent -> AppleBlue
                                else -> AppleFill
                            }
                        )
                        .border(
                            1.dp,
                            if (isCurrent || isCompleted) AppleBlue else AppleFill,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = AppleBackground,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Text(
                            text = "$stepIndex",
                            color = if (isCurrent) AppleLabel else AppleSecondaryLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Prompt message
        val prompt = when {
            trainingState.promptText.isNotBlank() -> trainingState.promptText
            trainingState.step == 0 -> "Suggest a name for your AI assistant"
            trainingState.step == 1 -> "Please say '${trainingState.proposedName}' again (1 of 3)"
            trainingState.step == 2 -> "Say '${trainingState.proposedName}' one more time (2 of 3)"
            else -> "Say your chosen name"
        }

        Text(
            text = prompt,
            color = AppleBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Editable candidate name box with Confirm Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleSurface)
                    .border(1.dp, AppleBlue.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (textInput.isBlank()) {
                    Text(
                        text = "Speak or type name...",
                        color = AppleSecondaryLabel,
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppleLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(AppleBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (textInput.isNotBlank()) onConfirmName(textInput)
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Confirm Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AppleBlue, AppleBlue)
                        )
                    )
                    .clickable {
                        val nameToSave = textInput.ifBlank { trainingState.proposedName.ifBlank { "Aurio" } }
                        onConfirmName(nameToSave)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = AppleOnAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Confirm",
                        color = AppleOnAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick suggestions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val suggestions = listOf("Jarvis", "Nova", "Tars", "Friday", "Echo", "Aurio")
            for (name in suggestions) {
                QuickChip("✨ $name") {
                    textInput = name
                    onConfirmName(name)
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppleFill)
            .border(1.dp, AppleSeparator, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = AppleSecondaryLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
