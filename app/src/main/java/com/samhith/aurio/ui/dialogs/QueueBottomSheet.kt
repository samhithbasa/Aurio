package com.samhith.aurio.ui.dialogs

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.ui.auth.aurioGlow
import kotlin.math.roundToInt
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset

/**
 * High-fidelity Queue Modal BottomSheet with Now Playing overview,
 * drag-and-drop / shift reordering of upcoming tracks, jump to track,
 * and track removal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    currentSong: SongItem?,
    queue: List<SongItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleShuffle: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ClaySurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ClayInset)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = ClayPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Playback Queue (${queue.size})",
                        color = ClayLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Shuffle Toggle
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) ClayPrimary else ClaySecondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Now Playing Section
            Text(
                text = "NOW PLAYING",
                color = ClayPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (currentSong != null) {
                NowPlayingQueueCard(song = currentSong, isPlaying = isPlaying)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ClayInset, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 2. Next in Queue Section
            Text(
                text = "UP NEXT (DRAG / MOVE TO REORDER)",
                color = ClaySecondaryLabel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty",
                        color = ClaySecondaryLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(queue, key = { _, item -> item.id }) { index, song ->
                        val isCurrent = index == currentIndex
                        QueueTrackItem(
                            song = song,
                            index = index,
                            totalCount = queue.size,
                            isCurrent = isCurrent,
                            onPlay = { onSongClick(index) },
                            onRemove = { onRemoveSong(index) },
                            onReorderTo = { targetIndex ->
                                onReorder(index, targetIndex)
                            },
                            onMoveUp = {
                                if (index > 0) onReorder(index, index - 1)
                            },
                            onMoveDown = {
                                if (index < queue.size - 1) onReorder(index, index + 1)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingQueueCard(
    song: SongItem,
    isPlaying: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ClaySurface)
            .border(1.dp, ClayPrimary.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .aurioGlow(ClayPrimary, alpha = 0.25f, blurRadius = 14.dp)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ClayInset),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = ClayPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = ClayPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist.ifBlank { song.album.ifBlank { "Aurio Music" } },
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Animated Equalizer Wave Bars
            if (isPlaying) {
                LiveWaveEqualizer(modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
private fun QueueTrackItem(
    song: SongItem,
    index: Int,
    totalCount: Int,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onReorderTo: (targetIndex: Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { 62.dp.toPx() } }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val currentReorderTo by rememberUpdatedState(onReorderTo)
    val currentIndex by rememberUpdatedState(index)
    val currentTotalCount by rememberUpdatedState(totalCount)

    val targetIndex = remember(dragAccumulator, currentIndex, currentTotalCount) {
        val steps = (dragAccumulator / itemHeightPx).roundToInt()
        (currentIndex + steps).coerceIn(0, currentTotalCount - 1)
    }

    val bg = when {
        isDragging -> ClayInset
        isCurrent -> ClaySurface
        else -> ClaySurface
    }
    val border = when {
        isDragging -> ClayPrimary
        isCurrent -> ClayPrimary.copy(alpha = 0.6f)
        else -> ClayInset
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 100f else 1f)
            .offset { IntOffset(0, if (isDragging) dragAccumulator.roundToInt() else 0) }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .then(
                if (isDragging) {
                    Modifier.aurioGlow(ClayPrimary, alpha = 0.5f, blurRadius = 16.dp)
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        dragAccumulator = 0f
                    },
                    onDragEnd = {
                        val steps = (dragAccumulator / itemHeightPx).roundToInt()
                        val finalTarget = (currentIndex + steps).coerceIn(0, currentTotalCount - 1)
                        val fromIdx = currentIndex
                        isDragging = false
                        dragAccumulator = 0f
                        if (finalTarget != fromIdx) {
                            currentReorderTo(finalTarget)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragAccumulator = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount.y
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPlay
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle with instant vertical drag detector
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragAccumulator = 0f
                            },
                            onDragEnd = {
                                val steps = (dragAccumulator / itemHeightPx).roundToInt()
                                val finalTarget = (currentIndex + steps).coerceIn(0, currentTotalCount - 1)
                                val fromIdx = currentIndex
                                isDragging = false
                                dragAccumulator = 0f
                                if (finalTarget != fromIdx) {
                                    currentReorderTo(finalTarget)
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                dragAccumulator = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging || isCurrent) ClayPrimary else ClaySecondaryLabel,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Position index (shows dynamic target position while dragging)
            Text(
                text = if (isDragging) "${targetIndex + 1}" else "${currentIndex + 1}",
                color = if (isDragging || isCurrent) ClayPrimary else ClaySecondaryLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(20.dp)
            )

            // Artwork
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClaySurface),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
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

            Spacer(modifier = Modifier.width(10.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent || isDragging) ClayPrimary else ClayLabel,
                    fontSize = 13.5.sp,
                    fontWeight = if (isCurrent || isDragging) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist.ifBlank { song.album.ifBlank { "Aurio Music" } },
                    color = ClaySecondaryLabel,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Move Up/Down Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentIndex > 0) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (currentIndex < currentTotalCount - 1) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Delete / Remove
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = ClaySecondaryLabel,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveWaveEqualizer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val h1 by transition.animateFloat(
        initialValue = 4f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 16f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 6f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "h3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h1.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(ClayPrimary)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h2.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(ClayPrimary)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(ClayPrimary)
        )
    }
}
