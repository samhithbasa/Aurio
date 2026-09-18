package com.samhith.aurio.ui.library

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samhith.aurio.data.download.DownloadManager
import com.samhith.aurio.data.download.DownloadState
import com.samhith.aurio.data.download.DownloadStatus
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent

/**
 * Dedicated Offline Downloads page with:
 * - Real-time download progress rings for active downloads in Aurio crimson/coral
 * - Greyed-out state with progress indicator for in-progress tracks
 * - Completed offline tracks with coral checkmark and offline badge
 * - Plays fully offline without network errors
 * - Long-press support for SongActionDialog and AddToPlaylistSheet
 */
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onListenTogetherClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onOpenFullPlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val downloadManager = remember { DownloadManager.getInstance().apply { initialize(context) } }
    val libraryRepository = remember { LibraryRepository.getInstance().apply { initialize(context) } }

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()
    val downloadStates by downloadManager.downloadStates.collectAsState()
    val likedSongIds by libraryRepository.likedSongIds.collectAsState()

    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    val progress = if (durationMs > 0) (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Merge downloaded songs with any active downloads
    val allItems = buildList {
        // Active downloads first (ENQUEUED, DOWNLOADING)
        for ((songId, state) in downloadStates) {
            if (state.status == DownloadStatus.DOWNLOADING || state.status == DownloadStatus.ENQUEUED) {
                state.song?.let { add(Pair(it, state)) }
            }
        }
        // Then completed downloads
        for (song in downloadedSongs) {
            val state = downloadStates[song.id]
            if (state == null || state.status == DownloadStatus.COMPLETED) {
                add(Pair(song, state ?: DownloadState(song.id, 1f, DownloadStatus.COMPLETED, song = song)))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppleBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ═══ Header ═══════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppleLabel)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Downloads",
                            color = AppleLabel,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Offline badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppleBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.OfflineBolt,
                                    null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Offline",
                                    color = AppleBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                    Text(
                        "${downloadedSongs.size} songs available offline",
                        color = AppleSecondaryLabel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Play All button
                if (downloadedSongs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(AppleBlue, AppleBlue)))
                            .clickable {
                                playerManager.playQueue(downloadedSongs, 0, "Downloads")
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, "Play All", tint = AppleOnAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play", color = AppleOnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // ═══ Songs List ═══════════════════════════════════════
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 150.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
            ) {
                if (allItems.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = AppleGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No downloads yet",
                                color = AppleSecondaryLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                "Download songs for offline listening",
                                color = AppleGray,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                } else {
                    itemsIndexed(allItems) { index, (song, state) ->
                        DownloadedSongRow(
                            song = song,
                            downloadState = state,
                            isCurrentlyPlaying = currentSong?.id == song.id,
                            onSongClick = {
                                if (state.status == DownloadStatus.COMPLETED) {
                                    val completedSongs = allItems
                                        .filter { it.second.status == DownloadStatus.COMPLETED }
                                        .map { it.first }
                                    val idx = completedSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    playerManager.playQueue(completedSongs, idx, "Downloads")
                                }
                            },
                            onSongLongClick = {
                                if (state.status == DownloadStatus.COMPLETED) {
                                    selectedSongForAction = song
                                }
                            },
                            onDeleteClick = {
                                downloadManager.removeDownload(song.id)
                            }
                        )
                    }
                }
            }
        }

        // ═══ Bottom: MiniPlayer + NavBar ══════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                title = currentSong?.title ?: "Feel Every Beat",
                artist = currentSong?.artist ?: "Aurio Music",
                thumbnailUrl = currentSong?.thumbnailUrl ?: "",
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                progress = progress,
                onPlayPauseClick = { playerManager.togglePlayPause() },
                onNextClick = { playerManager.playNext() },
                onPreviousClick = { playerManager.playPrevious(forcePreviousTrack = true) },
                onCardClick = onOpenFullPlayer,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            CurvedBottomNavBar(
                selectedTab = HomeTab.LIBRARY,
                onTabSelected = {
                    when (it) {
                        HomeTab.HOME -> onHomeClick()
                        HomeTab.LISTEN_TOGETHER -> onListenTogetherClick()
                        HomeTab.LIBRARY -> onLibraryClick()
                        HomeTab.PROFILE -> onProfileClick()
                    }
                }
            )
        }

        // ═══ Long-Press Song Action Dialog ════════════════════════
        if (selectedSongForAction != null) {
            val song = selectedSongForAction!!
            val isSongLiked = likedSongIds.contains(song.id)
            SongActionDialog(
                song = song,
                isLiked = isSongLiked,
                onDismiss = { selectedSongForAction = null },
                onAddToQueue = { playerManager.addToQueue(song) },
                onPlayNext = { playerManager.playNextInQueue(song) },
                onAddToPlaylist = { songForPlaylistSheet = song },
                onToggleLike = {
                    libraryRepository.toggleLikeSong(song)
                    playerManager.toggleLikeSong(song)
                },
                onShare = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listening to ${song.title} by ${song.artist} on Aurio Music 🎵")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                },
                onPlayNow = { playerManager.playSong(song) }
            )
        }

        // ═══ Add to Playlist Sheet ════════════════════════════════
        if (songForPlaylistSheet != null) {
            AddToPlaylistSheet(
                song = songForPlaylistSheet!!,
                onDismiss = { songForPlaylistSheet = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadedSongRow(
    song: SongItem,
    downloadState: DownloadState,
    isCurrentlyPlaying: Boolean,
    onSongClick: () -> Unit,
    onSongLongClick: () -> Unit = {},
    onDeleteClick: () -> Unit
) {
    val isDownloading = downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.ENQUEUED
    val isCompleted = downloadState.status == DownloadStatus.COMPLETED
    val isFailed = downloadState.status == DownloadStatus.FAILED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrentlyPlaying) AppleBlue.copy(alpha = 0.06f)
                else Color.Transparent
            )
            .alpha(if (isDownloading) 0.6f else 1f)
            .combinedClickable(
                enabled = isCompleted,
                onClick = onSongClick,
                onLongClick = onSongLongClick
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art with progress overlay
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppleSurface),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = AppleGray, modifier = Modifier.size(22.dp))
                }
            }

            // Progress ring overlay for downloading
            if (isDownloading) {
                CircularProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.size(50.dp),
                    color = AppleBlue,
                    trackColor = AppleBlue.copy(alpha = 0.15f),
                    strokeWidth = 3.dp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrentlyPlaying) AppleBlue else AppleLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artist,
                    color = AppleSecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isDownloading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${(downloadState.progress * 100).toInt()}%",
                        color = AppleBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        // Status indicator
        if (isCompleted) {
            Icon(
                Icons.Default.CheckCircle,
                "Downloaded",
                tint = AppleBlue,
                modifier = Modifier.size(18.dp).padding(end = 4.dp)
            )
        }

        if (isFailed) {
            Text(
                "Failed",
                color = AppleBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Delete button (only for completed)
        if (isCompleted) {
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    "Remove",
                    tint = AppleGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
