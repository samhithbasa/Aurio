package com.samhith.aurio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayCardGradient
import com.samhith.aurio.ui.theme.clayCard
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Capsule/Stadium pill-shaped Mini Player matching the reference design layout:
 * - Fully rounded pill contour (RoundedCornerShape(36.dp))
 * - Circular album artwork encircled by a reactive circular playback progress ring
 * - Track Title and Artist typography
 * - Live animated equalizer waveform indicator
 * - Play/Pause and Next action buttons
 */
@Composable
fun MiniPlayer(
    title: String = "Feel Every Beat",
    artist: String = "Aurio Music",
    thumbnailUrl: String = "",
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    progress: Float = 0.0f,
    albumArt: ImageBitmap? = null,
    onPlayPauseClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onCardClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(36.dp)
    val context = LocalContext.current

    // Pulsing ambient glow animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "mini_player_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isPlaying) 0.35f else 0.15f,
        targetValue = if (isPlaying) 0.58f else 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Swipe left for the next song, right for the previous one. The card follows the finger so
    // the gesture is visible while it happens, then flies out in that direction on release.
    val swipeOffset = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        swipeScope.launch {
                            val width = size.width.toFloat()
                            when {
                                swipeOffset.value <= -swipeThreshold -> {
                                    swipeOffset.animateTo(-width, tween(150))
                                    onNextClick()
                                    // Bring the new song in from the other side
                                    swipeOffset.snapTo(width * 0.45f)
                                    swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }

                                swipeOffset.value >= swipeThreshold -> {
                                    swipeOffset.animateTo(width, tween(150))
                                    onPreviousClick()
                                    swipeOffset.snapTo(-width * 0.45f)
                                    swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }

                                // Not far enough: settle back where it started
                                else -> swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        swipeScope.launch { swipeOffset.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeScope.launch { swipeOffset.snapTo(swipeOffset.value + dragAmount) }
                    }
                )
            }
            .padding(horizontal = 16.dp)
            .height(64.dp)
            .clayCard(
                cornerRadius = 32.dp,
                elevation = 9.dp,
                gradient = ClayCardGradient
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Left: Circular Artwork with Circular Progress Indicator
            Box(
                modifier = Modifier
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Track Ring
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = ClayInset,
                    strokeWidth = 2.5.dp
                )

                // Active Progress Arc
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = ClayPrimary,
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round
                )

                // Inner Circular Thumbnail
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ClayPrimary.copy(alpha = 0.12f), ClayPrimary.copy(alpha = 0.12f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (albumArt != null) {
                        Image(
                            bitmap = albumArt,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = ClayPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Center: Song Title & Artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = ClayLabel,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = artist,
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. Right: Equalizer Wave + Play/Pause + Next
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Live Wave Equalizer
                LiveEqualizerWave(
                    isPlaying = isPlaying,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Play / Pause / Buffering Button
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ClayPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = ClayLabel,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Next Track Button
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = ClayLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 4-bar dynamic audio equalizer waveform matching the reference design.
 */
@Composable
private fun LiveEqualizerWave(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = if (isPlaying) 18f else 6f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = if (isPlaying) 6f else 10f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = if (isPlaying) 20f else 8f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = if (isPlaying) 7f else 12f,
        animationSpec = infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b4"
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barColor = if (isPlaying) ClayPrimary else ClaySecondaryLabel
        listOf(bar1, bar2, bar3, bar4).forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}
