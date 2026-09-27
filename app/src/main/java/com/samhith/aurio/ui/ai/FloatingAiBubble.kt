package com.samhith.aurio.ui.ai

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.samhith.aurio.data.ai.AssistantWakeState
import kotlin.math.roundToInt
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayMintDark
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClayCardGradient

/**
 * Floating draggable AI Assistant bubble visible across all screens.
 * Displays the 3D assistant avatar with audio-reactive breathing glow and smooth edge snapping.
 */
@Composable
fun FloatingAiBubble(
    wakeState: AssistantWakeState = AssistantWakeState.IDLE,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Load AI Assistant Bitmap from assets
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

    val isAwake = wakeState == AssistantWakeState.AWAKE_LISTENING ||
            wakeState == AssistantWakeState.SPEAKING ||
            wakeState == AssistantWakeState.PROCESSING

    // Infinite breathing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "AiBubbleGlow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAwake) 1.25f else 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAwake) 600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isAwake) 0.85f else 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAwake) 600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val bubbleSizePx = with(density) { 62.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }

        // Position coordinates with default anchored to right edge
        var offsetX by remember { mutableFloatStateOf(screenWidthPx - bubbleSizePx - marginPx) }
        var offsetY by remember { mutableFloatStateOf(screenHeightPx * 0.65f) }
        var isDragging by remember { mutableStateOf(false) }

        // Animated snapping to nearest edge on drag release
        val animatedOffsetX by animateFloatAsState(
            targetValue = offsetX,
            animationSpec = spring(stiffness = 400f, dampingRatio = 0.75f),
            label = "snapX"
        )

        val animatedOffsetY by animateFloatAsState(
            targetValue = offsetY,
            animationSpec = spring(stiffness = 400f, dampingRatio = 0.75f),
            label = "snapY"
        )

        // Adjust bounds if screen resizes
        LaunchedEffect(screenWidthPx, screenHeightPx) {
            if (!isDragging && (offsetX > screenWidthPx - bubbleSizePx || offsetX < 0)) {
                offsetX = (screenWidthPx - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (if (isDragging) offsetX else animatedOffsetX).roundToInt(),
                        (if (isDragging) offsetY else animatedOffsetY).roundToInt()
                    )
                }
                .size(62.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            // Snap to nearest horizontal edge
                            val midPoint = screenWidthPx / 2f
                            offsetX = if (offsetX + bubbleSizePx / 2f < midPoint) {
                                marginPx
                            } else {
                                screenWidthPx - bubbleSizePx - marginPx
                            }
                            // Clamp vertical bounds
                            offsetY = offsetY.coerceIn(marginPx + 60f, screenHeightPx - bubbleSizePx - marginPx - 160f)
                        },
                        onDragCancel = {
                            isDragging = false
                            val midPoint = screenWidthPx / 2f
                            offsetX = if (offsetX + bubbleSizePx / 2f < midPoint) marginPx else screenWidthPx - bubbleSizePx - marginPx
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidthPx - bubbleSizePx)
                            offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, screenHeightPx - bubbleSizePx - marginPx)
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Animated Glowing Halo
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(glowScale)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFAAAAAA).copy(alpha = glowAlpha * 0.5f),
                                    Color(0xFFAAAAAA).copy(alpha = glowAlpha * 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
            )

            // Inner Avatar Bubble with glossy 3D border and specular sheen
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = Color(0x28000000),
                        ambientColor = Color(0x10000000)
                    )
                    .clip(CircleShape)
                    .background(ClayCardGradient)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFAAAAAA).copy(alpha = 0.8f),
                                Color(0xFF8E8E93).copy(alpha = 0.5f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (assistantBitmap != null) {
                    Image(
                        bitmap = assistantBitmap,
                        contentDescription = "Aurio AI Assistant",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Aurio AI",
                        tint = ClayLabel,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Top specular gloss reflection sheen
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Status Indicator Dot (Green/Crimson if active)
            if (isAwake) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (wakeState == AssistantWakeState.AWAKE_LISTENING) ClayPrimary else ClayMintDark)
                        .border(1.5.dp, ClayInset, CircleShape)
                )
            }
        }
    }
}
