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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samhith.aurio.data.ai.AiVoiceState
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClaySecondaryLabel

/**
 * AI Voice Assistant Modal Dialog with dynamic audio visualizer, real-time speech transcription,
 * interactive quick command chips, and seamless text query fallback.
 */
@Composable
fun AiAssistantDialog(
    voiceState: AiVoiceState,
    transcript: String,
    assistantReply: String?,
    rmsVolume: Float,
    errorMessage: String?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSubmitTextQuery: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var textInput by remember { mutableStateOf("") }

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

    // Mic Button pulse animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "AiDialogPulse")
    val micPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (voiceState == AiVoiceState.LISTENING) 1.18f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    val visualizerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visualizerPhase"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClaySurface,
                            ClayBackground,
                            ClayBackground
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ClayPrimary.copy(alpha = 0.8f),
                            ClayPrimary.copy(alpha = 0.3f),
                            ClayInset
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, ClayPrimary, CircleShape)
                                .background(ClaySurface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (assistantBitmap != null) {
                                Image(
                                    bitmap = assistantBitmap,
                                    contentDescription = "AI Assistant",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = ClayPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Aurio AI Assistant",
                                    color = ClayLabel,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClayPrimary.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "VOICE",
                                        color = ClayPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Text(
                                text = "Voice Music & Playback Intelligence",
                                color = ClaySecondaryLabel,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ClayInset)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Central Dynamic Visualizer Stage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ClaySurface)
                        .border(1.dp, ClayInset, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Multi-Bar Waveform reacting to Voice RMS volume
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(44.dp)
                        ) {
                            val barCount = 11
                            for (i in 0 until barCount) {
                                val offsetFactor = kotlin.math.sin((visualizerPhase * 2 * Math.PI + i * 0.6).toDouble()).toFloat()
                                val baseHeight = when (voiceState) {
                                    AiVoiceState.LISTENING -> (14f + (rmsVolume * 26f) + (offsetFactor * 8f).coerceAtLeast(0f)).dp
                                    AiVoiceState.SPEAKING -> (10f + (offsetFactor * 18f).coerceAtLeast(4f)).dp
                                    AiVoiceState.PROCESSING -> (12f + (offsetFactor * 10f).coerceAtLeast(2f)).dp
                                    else -> 8.dp
                                }

                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(baseHeight.coerceIn(6.dp, 40.dp))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    ClayPrimary,
                                                    ClayPrimary,
                                                    ClayPrimary
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dynamic Transcript / Feedback Display
                        val displayMainText = when {
                            !errorMessage.isNullOrBlank() -> errorMessage
                            !assistantReply.isNullOrBlank() -> assistantReply
                            transcript.isNotBlank() -> "\"$transcript\""
                            voiceState == AiVoiceState.LISTENING -> "Listening to your voice..."
                            voiceState == AiVoiceState.PROCESSING -> "Processing request..."
                            voiceState == AiVoiceState.SPEAKING -> "Speaking..."
                            else -> "Ask me to play any song, artist, playlist, or toggle haptics"
                        }

                        val textColor = when {
                            !errorMessage.isNullOrBlank() -> ClayPrimary
                            !assistantReply.isNullOrBlank() -> ClayLabel
                            transcript.isNotBlank() -> ClayPrimary
                            else -> ClaySecondaryLabel
                        }

                        Text(
                            text = displayMainText,
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = if (!assistantReply.isNullOrBlank() || transcript.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Suggestion Prompt Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip("🎵 Play Die With A Smile") { onSubmitTextQuery("Play Die With A Smile") }
                    SuggestionChip("⏸️ Pause") { onSubmitTextQuery("Pause") }
                    SuggestionChip("▶️ Resume") { onSubmitTextQuery("Resume") }
                    SuggestionChip("⏭️ Next Song") { onSubmitTextQuery("Next song") }
                    SuggestionChip("❤️ Like Track") { onSubmitTextQuery("Like this song") }
                    SuggestionChip("⚡ Turn On Haptics") { onSubmitTextQuery("Turn on haptics") }
                    SuggestionChip("📁 Open Library") { onSubmitTextQuery("Open library") }
                    SuggestionChip("🎶 What's Playing?") { onSubmitTextQuery("What is playing") }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Controls: Glowing Mic Button & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .scale(micPulseScale)
                            .size(68.dp)
                            .shadow(
                                elevation = if (voiceState == AiVoiceState.LISTENING) 18.dp else 6.dp,
                                shape = CircleShape,
                                spotColor = ClayPrimary,
                                ambientColor = ClayPrimary
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = if (voiceState == AiVoiceState.LISTENING) {
                                        listOf(ClayPrimary, ClayPrimary)
                                    } else {
                                        listOf(ClayInset, ClaySurface)
                                    }
                                )
                            )
                            .border(
                                width = 2.dp,
                                brush = Brush.sweepGradient(
                                    colors = listOf(ClayPrimary, ClayPrimary, ClayPrimary)
                                ),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (voiceState == AiVoiceState.LISTENING) {
                                        onStopListening()
                                    } else {
                                        onStartListening()
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (voiceState == AiVoiceState.PROCESSING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (voiceState == AiVoiceState.LISTENING) Icons.Default.Mic else Icons.Default.Mic,
                                contentDescription = "Mic Toggle",
                                tint = if (voiceState == AiVoiceState.LISTENING) Color.White else ClayPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (voiceState) {
                        AiVoiceState.LISTENING -> "Tap to finish speaking"
                        AiVoiceState.PROCESSING -> "Finding music..."
                        AiVoiceState.SPEAKING -> "Playing & speaking response"
                        else -> "Tap microphone to speak"
                    },
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Text Query Input Fallback Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClayInset)
                        .border(1.dp, ClayInset, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = ClayLabel,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(ClayPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    val q = textInput.trim()
                                    textInput = ""
                                    keyboardController?.hide()
                                    onSubmitTextQuery(q)
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (textInput.isEmpty()) {
                                Text(
                                    text = "Or type a music command...",
                                    color = ClaySecondaryLabel,
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                val q = textInput.trim()
                                textInput = ""
                                keyboardController?.hide()
                                onSubmitTextQuery(q)
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (textInput.isNotBlank()) ClayPrimary else ClayInset)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (textInput.isNotBlank()) ClayLabel else ClaySecondaryLabel,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ClayInset)
            .border(1.dp, ClayInset, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = ClaySecondaryLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
