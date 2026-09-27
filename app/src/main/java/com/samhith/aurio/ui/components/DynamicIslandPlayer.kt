package com.samhith.aurio.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.data.player.SpatialMode
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary

/**
 * Apple-style Dynamic Island Music Player:
 * - Collapsed: Sleek pill at top with mini album art on left and animated equalizer on right.
 * - Long-Press: Smoothly expands with spring physics into the full Dynamic Island Mini Player widget matching the reference design.
 * - Tap: Opens Full Screen Player.
 * - Tap outside or swipe up: Collapses back to the Dynamic Island pill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DynamicIslandPlayer(
    playerManager: AudioPlayerManager,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    onShowBanner: (String) -> Unit = {}
) {
    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val positionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val likedSongIds by playerManager.likedSongIds.collectAsState()
    val spatialMode by playerManager.spatialAudioManager.mode.collectAsState()

    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val song = currentSong ?: return

    val isLiked = likedSongIds.contains(song.id)

    // BackHandler to collapse island if expanded
    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Scrim backdrop when expanded to dismiss on tap outside
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isExpanded = false }
                    )
            )
        }

        // Island Container (Full width keeping 10dp gap from left and right)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 6.dp)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (isExpanded) 16.dp else 6.dp,
                    shape = RoundedCornerShape(if (isExpanded) 34.dp else 24.dp),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                    ambientColor = Color.Black.copy(alpha = 0.25f)
                )
                .clip(RoundedCornerShape(if (isExpanded) 34.dp else 24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1B1B1E),
                            Color(0xFF101012),
                            Color(0xFF09090B)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isExpanded) 0.30f else 0.20f),
                            ClayPrimary.copy(alpha = if (isExpanded) 0.18f else 0.10f),
                            Color(0xFF2C2C2E).copy(alpha = 0.50f)
                        )
                    ),
                    shape = RoundedCornerShape(if (isExpanded) 34.dp else 24.dp)
                )
                .drawWithContent {
                    drawContent()
                    // Apple specular glass top sheen
                    val sheenHeight = size.height * 0.42f
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isExpanded) 0.22f else 0.16f),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = sheenHeight
                        ),
                        size = Size(size.width, sheenHeight),
                        cornerRadius = CornerRadius(
                            if (isExpanded) 34.dp.toPx() else 24.dp.toPx(),
                            if (isExpanded) 34.dp.toPx() else 24.dp.toPx()
                        )
                    )
                }
                .pointerInput(isExpanded) {
                    if (isExpanded) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount < -15f) { // Swiped up
                                change.consume()
                                isExpanded = false
                            }
                        }
                    } else {
                        detectTapGestures(
                            onTap = {
                                onOpenFullPlayer()
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpanded = true
                            }
                        )
                    }
                }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            if (isExpanded) {
                // =========================================================================
                // EXPANDED DYNAMIC ISLAND MINI PLAYER (Matching Reference Screenshot)
                // =========================================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    // 1. TOP ROW: Artwork + Song Title / Artist + Output Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    isExpanded = false
                                    onOpenFullPlayer()
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album Artwork (Rounded Square)
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF242426))
                                .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.thumbnailUrl.isNotBlank()) {
                                HighQualityArtwork(
                                    url = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = ClayPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Song Title & Artist
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = song.artist,
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Top-right Audio Badge (Speaker / Route)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    val nextMode = when (spatialMode) {
                                        SpatialMode.OFF -> SpatialMode.EIGHT_D
                                        SpatialMode.EIGHT_D -> SpatialMode.SIXTEEN_D
                                        SpatialMode.SIXTEEN_D -> SpatialMode.OFF
                                    }
                                    playerManager.spatialAudioManager.setMode(nextMode)
                                    onShowBanner(if (nextMode == SpatialMode.OFF) "Spatial Audio Off" else "Spatial Audio ${nextMode.displayName} Enabled 🎧")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (spatialMode != SpatialMode.OFF) Icons.Default.Headphones else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Audio Output",
                                tint = if (spatialMode != SpatialMode.OFF) ClayPrimary else Color(0xFF8E8E93),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. MIDDLE ROW: Elapsed Time + Scrubbable Progress Bar + Remaining Time
                    DynamicIslandScrubber(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = { targetMs ->
                            playerManager.seekTo(targetMs)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. BOTTOM ROW: Star + [Prev, Play/Pause, Next] + Route/AirPlay
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Star / Favorite
                        IconButton(
                            onClick = {
                                playerManager.toggleLikeSong(song)
                                val updatedLiked = !isLiked
                                onShowBanner(if (updatedLiked) "Added to Liked Songs ❤️" else "Removed from Liked Songs")
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isLiked) ClayPrimary else Color(0xFF8E8E93),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Center: Playback Controls (Previous, Play/Pause, Next)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Previous Track
                            IconButton(
                                onClick = { playerManager.playPrevious() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Play / Pause / Buffering
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { playerManager.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        color = ClayPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            // Next Track
                            IconButton(
                                onClick = { playerManager.playNext() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Right: AirPlay / Spatial Audio Toggle
                        IconButton(
                            onClick = {
                                val nextMode = when (spatialMode) {
                                    SpatialMode.OFF -> SpatialMode.EIGHT_D
                                    SpatialMode.EIGHT_D -> SpatialMode.SIXTEEN_D
                                    SpatialMode.SIXTEEN_D -> SpatialMode.OFF
                                }
                                playerManager.spatialAudioManager.setMode(nextMode)
                                onShowBanner(if (nextMode == SpatialMode.OFF) "Spatial Audio Off" else "Spatial Audio: ${nextMode.displayName} Active 🎧")
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Airplay,
                                contentDescription = "AirPlay",
                                tint = if (spatialMode != SpatialMode.OFF) ClayPrimary else Color(0xFF8E8E93),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            } else {
                // =========================================================================
                // COMPACT DYNAMIC ISLAND PILL (Collapsed State - Full Width with 10dp Gap)
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Mini Album Artwork
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF242426))
                            .border(0.6.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.thumbnailUrl.isNotBlank()) {
                            HighQualityArtwork(
                                url = song.thumbnailUrl,
                                contentDescription = song.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = ClayPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Center: Song Title and Artist Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = song.artist,
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Animated Equalizer Waveform + Play/Pause Action
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IslandMiniEqualizerWave(
                            isPlaying = isPlaying
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { playerManager.togglePlayPause() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = ClayPrimary,
                                    strokeWidth = 1.8.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Apple-styled scrubber row with elapsed and remaining timestamps.
 */
@Composable
private fun DynamicIslandScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val displayFraction = if (isDragging) dragFraction else currentFraction
    val displayPositionMs = if (isDragging) (dragFraction * durationMs).toLong() else positionMs
    val remainingMs = if (durationMs > displayPositionMs) durationMs - displayPositionMs else 0L

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Elapsed Time
        Text(
            text = formatDuration(displayPositionMs),
            color = Color(0xFF8E8E93),
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Sleek Slider
        Slider(
            value = displayFraction,
            onValueChange = { frac ->
                isDragging = true
                dragFraction = frac
            },
            onValueChangeFinished = {
                isDragging = false
                if (durationMs > 0) {
                    onSeek((dragFraction * durationMs).toLong())
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ClayPrimary,
                inactiveTrackColor = Color(0xFF38383A)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Remaining Time (with negative sign)
        Text(
            text = "-${formatDuration(remainingMs)}",
            color = Color(0xFF8E8E93),
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Animated 4-bar equalizer wave for the compact Dynamic Island.
 */
@Composable
private fun IslandMiniEqualizerWave(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "island_eq_bars")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isPlaying) 14f else 4f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ieq_b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = if (isPlaying) 5f else 8f,
        animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ieq_b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = if (isPlaying) 15f else 6f,
        animationSpec = infiniteRepeatable(tween(440, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ieq_b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 13f,
        targetValue = if (isPlaying) 6f else 9f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ieq_b4"
    )

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barColor = if (isPlaying) ClayPrimary else ClaySecondaryLabel.copy(alpha = 0.6f)
        listOf(bar1, bar2, bar3, bar4).forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.25.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * Formats milliseconds into mm:ss string.
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
