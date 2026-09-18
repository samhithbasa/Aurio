package com.samhith.aurio.ui.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samhith.aurio.data.ai.AssistantUiAction
import com.samhith.aurio.data.download.DownloadManager
import com.samhith.aurio.data.download.DownloadStatus
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.data.player.RepeatMode
import com.samhith.aurio.ui.auth.aurioGlow
import androidx.compose.material.icons.filled.Vibration
import com.samhith.aurio.ui.dialogs.EqualizerDialog
import com.samhith.aurio.ui.dialogs.LyricsSheet
import com.samhith.aurio.ui.dialogs.MusicHapticsDialog
import com.samhith.aurio.ui.dialogs.QueueBottomSheet
import com.samhith.aurio.ui.dialogs.SleepTimerDialog
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.library.AddToPlaylistSheet
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.data.player.SpatialMode
import com.samhith.aurio.ui.dialogs.SpatialAudioDialog
import com.samhith.aurio.ui.components.HighQualityArtwork
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-screen music player layout matching the user's reference design.
 * Features large ambient glow album artwork, reactive scrubbable seek slider,
 * Shuffle, Repeat modes, Previous, Next, Play/Pause, Share, Download,
 * Interactive Queue Sheet with drag-to-reorder, Sleep Timer dialog, and Equalizer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    playerManager: AudioPlayerManager,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    /** Panel the AI assistant asked to open by voice; cleared through [onAssistantPanelHandled]. */
    assistantPanel: AssistantUiAction? = null,
    onAssistantPanelHandled: () -> Unit = {}
) {
    BackHandler(onBack = onCollapse)
    val context = LocalContext.current

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val isShuffleEnabled by playerManager.isShuffleEnabled.collectAsState()
    val repeatMode by playerManager.repeatMode.collectAsState()
    val playingFrom by playerManager.playingFrom.collectAsState()
    val queue by playerManager.queue.collectAsState()
    val currentIndex by playerManager.currentIndex.collectAsState()
    val likedSongIds by playerManager.likedSongIds.collectAsState()
    val sleepTimerMinutes by playerManager.sleepTimerMinutes.collectAsState()
    val isUnlimitedQueue by playerManager.isUnlimitedQueueEnabled.collectAsState()

    val downloadManager = remember { DownloadManager.getInstance() }
    val downloadStates by downloadManager.downloadStates.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()

    val currentDownloadState = currentSong?.let { downloadStates[it.id] }
    val isDownloaded = currentSong?.let { song ->
        downloadedSongs.any { it.id == song.id } || currentDownloadState?.status == DownloadStatus.COMPLETED
    } ?: false
    val isDownloading = currentDownloadState?.status == DownloadStatus.DOWNLOADING || currentDownloadState?.status == DownloadStatus.ENQUEUED

    val isLiked = currentSong?.let { likedSongIds.contains(it.id) } ?: false
    val isHapticsEnabled by playerManager.musicHapticsManager.isEnabled.collectAsState()
    val isBeatActive by playerManager.musicHapticsManager.isBeatActive.collectAsState()

    val spatialAudioManager = remember { playerManager.spatialAudioManager }
    val spatialMode by spatialAudioManager.mode.collectAsState()

    // Modals & BottomSheets State
    var showSpatialDialog by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showHapticsDialog by remember { mutableStateOf(false) }
    var showSongOptionsDialog by remember { mutableStateOf(false) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    // Voice commands ("show lyrics", "open the queue", "equalizer") open the matching panel here.
    LaunchedEffect(assistantPanel) {
        when (assistantPanel) {
            AssistantUiAction.OPEN_LYRICS -> showLyricsSheet = true
            AssistantUiAction.OPEN_QUEUE -> showQueueSheet = true
            AssistantUiAction.OPEN_SLEEP_TIMER -> showSleepDialog = true
            AssistantUiAction.OPEN_EQUALIZER -> showEqualizerDialog = true
            AssistantUiAction.OPEN_HAPTICS -> showHapticsDialog = true
            else -> Unit
        }
        if (assistantPanel != null) {
            onAssistantPanelHandled()
        }
    }

    // Scrubber drag state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentFraction = remember(playbackPositionMs, durationMs, isDraggingSlider, dragProgress) {
        if (isDraggingSlider) {
            dragProgress
        } else if (durationMs > 0) {
            (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    // Glow pulse transition
    val infiniteTransition = rememberInfiniteTransition(label = "artwork_glow")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = if (isPlaying) 0.35f else 0.15f,
        targetValue = if (isPlaying) 0.55f else 0.15f,
        animationSpec = infiniteRepeatable(tween(2000), AnimRepeatMode.Reverse),
        label = "glow_alpha"
    )

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppleSurface,
                        AppleSurface,
                        AppleBackground
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Intercept all touches on empty spaces to prevent background clicks */ }
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar Navigation & Context
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Collapse Button (Down Chevron)
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = AppleLabel,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Playing From Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM",
                        color = AppleSecondaryLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playingFrom,
                        color = AppleLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Share Button
                IconButton(
                    onClick = {
                        currentSong?.let { song ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Listening to ${song.title} by ${song.artist} on Aurio Music \uD83C\uDFB5")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = AppleLabel,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Large Album Artwork - swipe it left/right to change track, like the mini player
            val artSwipeOffset = remember { Animatable(0f) }
            val artSwipeScope = rememberCoroutineScope()
            val artSwipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f)
                    .offset { IntOffset(artSwipeOffset.value.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                artSwipeScope.launch {
                                    val width = size.width.toFloat()
                                    when {
                                        artSwipeOffset.value <= -artSwipeThreshold -> {
                                            artSwipeOffset.animateTo(-width, tween(150))
                                            playerManager.playNext()
                                            artSwipeOffset.snapTo(width * 0.45f)
                                            artSwipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }

                                        artSwipeOffset.value >= artSwipeThreshold -> {
                                            artSwipeOffset.animateTo(width, tween(150))
                                            playerManager.playPrevious(forcePreviousTrack = true)
                                            artSwipeOffset.snapTo(-width * 0.45f)
                                            artSwipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }

                                        else -> artSwipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            },
                            onDragCancel = {
                                artSwipeScope.launch { artSwipeOffset.animateTo(0f, spring()) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                artSwipeScope.launch { artSwipeOffset.snapTo(artSwipeOffset.value + dragAmount) }
                            }
                        )
                    }
                    .aurioGlow(
                        color = AppleBlue,
                        alpha = glowIntensity,
                        blurRadius = 36.dp
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(AppleSurface)
                    .border(1.2.dp, AppleSeparator, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (currentSong != null && currentSong!!.thumbnailUrl.isNotBlank()) {
                    HighQualityArtwork(
                        url = currentSong!!.thumbnailUrl,
                        contentDescription = currentSong?.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(90.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Track Title, Artist, & Like Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "Feel Every Beat",
                        color = AppleLabel,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = currentSong?.artist?.ifBlank { currentSong?.album?.ifBlank { "Aurio Music" } } ?: "Aurio Music",
                        color = AppleSecondaryLabel,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Heart / Like Button
                IconButton(
                    onClick = { currentSong?.let { playerManager.toggleLikeSong(it) } },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like Song",
                        tint = if (isLiked) AppleBlue else AppleSecondaryLabel,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 4. Scrubbable Progress Bar & Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentFraction,
                    onValueChange = {
                        isDraggingSlider = true
                        dragProgress = it
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0) {
                            val targetMs = (dragProgress * durationMs).toLong()
                            playerManager.seekTo(targetMs)
                        }
                        isDraggingSlider = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AppleBlue,
                        activeTrackColor = AppleBlue,
                        inactiveTrackColor = AppleFill
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPos = if (isDraggingSlider) (dragProgress * durationMs).toLong() else playbackPositionMs
                    Text(
                        text = formatTime(displayedPos),
                        color = AppleSecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = AppleSecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // 5. Main Playback Controls: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = { playerManager.toggleShuffle() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) AppleBlue else AppleSecondaryLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = { playerManager.playPrevious() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = AppleLabel,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Large Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AppleBlue)
                        .aurioGlow(AppleBlue, alpha = 0.45f, blurRadius = 18.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { playerManager.togglePlayPause() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = AppleOnAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = AppleOnAccent,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Next Button
                IconButton(
                    onClick = { playerManager.playNext() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = AppleLabel,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repeat Mode Button
                IconButton(
                    onClick = { playerManager.toggleRepeat() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) AppleBlue else AppleSecondaryLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 6. Lyrics Clickable Panel in Between Controls and Bottom Row
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showLyricsSheet = true }
                    )
                    .padding(horizontal = 24.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Lyrics",
                    color = AppleLabel,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Show Lyrics",
                    tint = AppleBlue,
                    modifier = Modifier.size(20.dp)
                )
            }


            // 7. Spatial Audio: its own card, so the tool row below keeps its six actions
            SpatialAudioCard(
                spatialAudioManager = spatialAudioManager,
                onOpenSettings = { showSpatialDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 8. Sleek Dark Rounded Bottom Tool Container (Queue, Sleep, EQ, Save, Radio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppleSurface.copy(alpha = 0.9f))
                    .border(1.dp, AppleSeparator, RoundedCornerShape(22.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action 1: Queue
                    BottomToolButton(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        label = "Queue",
                        badge = "${queue.size}",
                        onClick = { showQueueSheet = true }
                    )

                    // Action 2: Sleep
                    BottomToolButton(
                        icon = Icons.Default.Bedtime,
                        label = "Sleep",
                        isActive = sleepTimerMinutes != null,
                        badge = if (sleepTimerMinutes != null && sleepTimerMinutes != -1) "${sleepTimerMinutes}m" else null,
                        onClick = { showSleepDialog = true }
                    )

                    // Action 3: EQ
                    BottomToolButton(
                        icon = Icons.Default.Tune,
                        label = "EQ",
                        onClick = { showEqualizerDialog = true }
                    )

                    // Action 4: Haptics (Beat Vibration)
                    BottomToolButton(
                        icon = Icons.Default.Vibration,
                        label = "Haptics",
                        isActive = isHapticsEnabled,
                        badge = if (isHapticsEnabled && isBeatActive && isPlaying) "●" else null,
                        onClick = { showHapticsDialog = true }
                    )

                    // Action 5: Download / Offline
                    DownloadToolButton(
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        progress = currentDownloadState?.progress ?: 0f,
                        onClick = {
                            currentSong?.let { song ->
                                if (!isDownloaded && !isDownloading) {
                                    downloadManager.downloadSong(song)
                                }
                            }
                        }
                    )

                    // Action 6: Unlimited Queue (Auto)
                    BottomToolButton(
                        icon = Icons.Default.AllInclusive,
                        label = "Auto",
                        isActive = isUnlimitedQueue,
                        onClick = {
                            playerManager.toggleUnlimitedQueue()
                        }
                    )
                }
            }
        }

        // --- BOTTOM SHEETS & DIALOGS ---

        if (showSpatialDialog) {
            SpatialAudioDialog(
                spatialAudioManager = spatialAudioManager,
                onDismiss = { showSpatialDialog = false }
            )
        }

        if (showLyricsSheet) {
            LyricsSheet(
                song = currentSong,
                playbackPositionMs = playbackPositionMs,
                onSeekTo = { posMs ->
                    playerManager.seekTo(posMs)
                },
                onDismiss = { showLyricsSheet = false }
            )
        }

        if (showQueueSheet) {
            QueueBottomSheet(
                currentSong = currentSong,
                queue = queue,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                isShuffleEnabled = isShuffleEnabled,
                onDismiss = { showQueueSheet = false },
                onSongClick = { index ->
                    playerManager.jumpToQueueIndex(index)
                },
                onRemoveSong = { index ->
                    playerManager.removeFromQueue(index)
                },
                onReorder = { from, to ->
                    playerManager.reorderQueue(from, to)
                },
                onToggleShuffle = {
                    playerManager.toggleShuffle()
                }
            )
        }

        if (showSleepDialog) {
            SleepTimerDialog(
                currentMinutes = sleepTimerMinutes,
                onDismiss = { showSleepDialog = false },
                onSetTimer = { mins ->
                    playerManager.setSleepTimer(mins)
                },
                onCancelTimer = {
                    playerManager.cancelSleepTimer()
                }
            )
        }

        if (showEqualizerDialog) {
            EqualizerDialog(
                equalizerManager = playerManager.equalizerManager,
                onDismiss = { showEqualizerDialog = false }
            )
        }

        if (showHapticsDialog) {
            MusicHapticsDialog(
                musicHapticsManager = playerManager.musicHapticsManager,
                isPlaying = isPlaying,
                onDismiss = { showHapticsDialog = false }
            )
        }

        // Snapshot the song at the moment the dialog opens, so its action lambdas act on a
        // stable value rather than re-reading the live `currentSong` state at click-time —
        // otherwise if playback stops/changes while this dialog is open, tapping any action
        // (Add to Queue, Share, etc.) would null-assert-crash.
        val dialogSong = currentSong
        if (showSongOptionsDialog && dialogSong != null) {
            SongActionDialog(
                song = dialogSong,
                isLiked = isLiked,
                onDismiss = { showSongOptionsDialog = false },
                onAddToQueue = { playerManager.addToQueue(dialogSong) },
                onPlayNext = { playerManager.playNextInQueue(dialogSong) },
                onToggleLike = { playerManager.toggleLikeSong(dialogSong) },
                onAddToPlaylist = { songForPlaylistSheet = dialogSong },
                onShare = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listening to ${dialogSong.title} by ${dialogSong.artist} on Aurio Music 🎵")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                },
                onPlayNow = { playerManager.playSong(dialogSong) }
            )
        }

        // --- ADD TO PLAYLIST SHEET ---
        songForPlaylistSheet?.let { song ->
            AddToPlaylistSheet(
                song = song,
                onDismiss = { songForPlaylistSheet = null }
            )
        }
    }
}

/**
 * Download action with a real progress ring.
 *
 * While a song downloads the ring fills with the actual percentage, and falls back to a spinning
 * ring when the server does not report a file size. It reads the same download state whether the
 * download was started by tapping here or by asking the assistant to "download this song".
 */
@Composable
private fun DownloadToolButton(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "DownloadProgress"
    )

    // A gentle pop when the download lands
    val savedScale by animateFloatAsState(
        targetValue = if (isDownloaded) 1f else 0.8f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "DownloadSavedScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isDownloading) {
                if (progress > 0.01f) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(28.dp),
                        color = AppleBlue,
                        trackColor = AppleFill,
                        strokeWidth = 2.5.dp,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    // Size unknown: keep it moving so it never looks stalled
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = AppleBlue,
                        trackColor = AppleFill,
                        strokeWidth = 2.5.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            Icon(
                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                contentDescription = "Download",
                tint = if (isDownloaded || isDownloading) AppleBlue else AppleSecondaryLabel,
                modifier = Modifier
                    .size(if (isDownloading) 14.dp else 22.dp)
                    .scale(if (isDownloaded) savedScale else 1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                isDownloaded -> "Saved"
                isDownloading && progress > 0.01f -> "${(animatedProgress * 100).toInt()}%"
                isDownloading -> "Saving"
                else -> "Download"
            },
            color = if (isDownloaded || isDownloading) AppleBlue else AppleSecondaryLabel,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = if (isDownloaded || isDownloading) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun BottomToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) AppleBlue else AppleSecondaryLabel,
                modifier = Modifier.size(22.dp)
            )
            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(AppleBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge,
                        color = AppleOnAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) AppleBlue else AppleSecondaryLabel,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}