package com.samhith.aurio.ui.library

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent

/**
 * Dedicated Liked Songs page with search filter, play all, shuffle,
 * full song list with unlike action, long-press action menu, and bottom MiniPlayer + NavBar.
 */
@Composable
fun LikedSongsScreen(
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
    val libraryRepository = remember { LibraryRepository.getInstance().apply { initialize(context) } }

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val likedSongs by libraryRepository.likedSongs.collectAsState()
    val likedSongIds by libraryRepository.likedSongIds.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    val filteredSongs = if (searchQuery.isBlank()) likedSongs
    else likedSongs.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.artist.contains(searchQuery, ignoreCase = true)
    }

    val progress = if (durationMs > 0) (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

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
                    Text(
                        "Liked Songs",
                        color = AppleLabel,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        "${likedSongs.size} songs",
                        color = AppleSecondaryLabel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Play All button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(AppleBlue, AppleBlue)))
                        .clickable {
                            if (likedSongs.isNotEmpty()) {
                                playerManager.playQueue(likedSongs, 0, "Liked Songs")
                            }
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

                // Shuffle button
                IconButton(onClick = {
                    if (likedSongs.isNotEmpty()) {
                        val shuffled = likedSongs.shuffled()
                        playerManager.playQueue(shuffled, 0, "Liked Songs (Shuffle)")
                    }
                }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = AppleBlue, modifier = Modifier.size(22.dp))
                }
            }

            // ═══ Search Filter ════════════════════════════════════
            if (likedSongs.size > 5) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleSurface)
                        .border(1.dp, AppleSeparator, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = AppleSecondaryLabel, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = AppleLabel,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.SansSerif
                            ),
                            cursorBrush = SolidColor(AppleBlue),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Find in liked songs...",
                                        color = AppleGray,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ═══ Songs List ═══════════════════════════════════════
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 150.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
            ) {
                if (filteredSongs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = AppleGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "No songs match \"$searchQuery\""
                                else "No liked songs yet",
                                color = AppleSecondaryLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                if (searchQuery.isNotBlank()) "Try a different search"
                                else "Like songs to see them here",
                                color = AppleGray,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredSongs) { index, song ->
                        LikedSongRow(
                            song = song,
                            isCurrentlyPlaying = currentSong?.id == song.id,
                            isLiked = likedSongIds.contains(song.id),
                            onSongClick = {
                                playerManager.playQueue(filteredSongs, index, "Liked Songs")
                            },
                            onSongLongClick = {
                                selectedSongForAction = song
                            },
                            onUnlike = {
                                libraryRepository.toggleLikeSong(song)
                                playerManager.toggleLikeSong(song)
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

        // ═══ Song Long-Press Action Dialog ════════════════════════
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
private fun LikedSongRow(
    song: SongItem,
    isCurrentlyPlaying: Boolean,
    isLiked: Boolean,
    onSongClick: () -> Unit,
    onSongLongClick: () -> Unit = {},
    onUnlike: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrentlyPlaying) AppleBlue.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onSongClick,
                onLongClick = onSongLongClick
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art
        Box(
            modifier = Modifier
                .size(50.dp)
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
            Text(
                song.artist,
                color = AppleSecondaryLabel,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        if (song.durationText.isNotBlank()) {
            Text(
                song.durationText,
                color = AppleSecondaryLabel,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Unlike Button
        IconButton(onClick = onUnlike, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Like",
                tint = if (isLiked) AppleBlue else AppleGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
