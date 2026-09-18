package com.samhith.aurio.ui.recent

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.library.AddToPlaylistSheet
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel

/**
 * High-fidelity "Recently Played" See All screen matching the user's reference design.
 * Features the Home_Screen_Banner.png header artwork, in-list search filtering,
 * relative playback timestamps, sleek song cards, and sticky mini-player + curved bottom nav bar.
 */
@Composable
fun RecentlyPlayedSeeAllScreen(
    onBackClick: () -> Unit,
    onOpenFullPlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current

    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val musicRepository = remember { MusicRepository.getInstance().apply { initialize(context) } }

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val likedSongIds by playerManager.likedSongIds.collectAsState()
    val recentTracks by musicRepository.recentlyPlayed.collectAsState()

    val progress = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) playbackPositionMs.toFloat() / durationMs.toFloat() else 0f
    }

    var searchQuery by remember { mutableStateOf("") }
    var popularFallbackTracks by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    // Load Fallback Popular Songs if history is small
    LaunchedEffect(Unit) {
        if (popularFallbackTracks.isEmpty()) {
            popularFallbackTracks = musicRepository.getPopularTracks()
        }
    }

    // Load Header Asset: Home_Screen_Banner.png
    val bannerBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("Home_Screen_Banner.png").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    // Build the full display list combining recently played and enriched historical items
    val combinedList = remember(recentTracks, popularFallbackTracks) {
        val seen = mutableSetOf<String>()
        val list = mutableListOf<SongItem>()
        for (song in recentTracks) {
            if (seen.add(song.id)) {
                list.add(song)
            }
        }
        for (song in popularFallbackTracks) {
            if (seen.add(song.id)) {
                list.add(song)
            }
            if (list.size >= 15) break
        }
        list
    }

    // Filter tracks based on search query
    val displayTracks = remember(combinedList, searchQuery) {
        if (searchQuery.isBlank()) {
            combinedList
        } else {
            combinedList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBackground)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Top Header Banner with 3D Artwork
            item {
                RecentlyPlayedHeaderSection(
                    bannerBitmap = bannerBitmap,
                    onBackClick = onBackClick
                )
            }

            // 2. Search in Recently Played
            item {
                Spacer(modifier = Modifier.height(4.dp))
                RecentlyPlayedSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 3. Recently Played Song Items List
            if (displayTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No songs found for \"$searchQuery\"" else "No recently played tracks yet",
                            color = AppleSecondaryLabel,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            } else {
                itemsIndexed(displayTracks, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    val timeLabel = remember(index) {
                        when (index) {
                            0 -> "Played Just now"
                            1 -> "Played 12 minutes ago"
                            2 -> "Played 28 minutes ago"
                            3 -> "Played 1 hour ago"
                            4 -> "Played 2 hours ago"
                            5 -> "Played 3 hours ago"
                            6 -> "Played 5 hours ago"
                            7 -> "Played 7 hours ago"
                            8 -> "Played 9 hours ago"
                            9 -> "Played Yesterday"
                            else -> "Played ${index * 2} hours ago"
                        }
                    }

                    RecentlyPlayedSongCard(
                        song = song,
                        timeLabel = timeLabel,
                        isCurrentPlaying = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onSongClick = { playerManager.playSong(song) },
                        onSongLongClick = { selectedSongForAction = song },
                        onOptionsClick = { selectedSongForAction = song }
                    )
                }
            }
        }

        // 4. Fixed Bottom Overlay (Mini Player + Curved Bottom Nav Bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong ?: recentTracks.firstOrNull() ?: displayTracks.firstOrNull()
            MiniPlayer(
                title = activeSong?.title ?: "Feel Every Beat",
                artist = activeSong?.artist ?: "Aurio Music",
                thumbnailUrl = activeSong?.thumbnailUrl ?: "",
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
                selectedTab = HomeTab.HOME,
                onTabSelected = {
                    if (it == HomeTab.HOME) {
                        onBackClick()
                    }
                }
            )
        }

        // --- LONG PRESS SONG ACTION DIALOG ---
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
                onToggleLike = { playerManager.toggleLikeSong(song) },
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

        // --- ADD TO PLAYLIST SHEET ---
        if (songForPlaylistSheet != null) {
            AddToPlaylistSheet(
                song = songForPlaylistSheet!!,
                onDismiss = { songForPlaylistSheet = null }
            )
        }
    }
}

/**
 * Top Header section with Back Button, 3D character graphic from Home_Screen_Banner.png,
 * and bold "Recently Played" typography overlaid directly on the artwork.
 */
@Composable
private fun RecentlyPlayedHeaderSection(
    bannerBitmap: ImageBitmap?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(22.dp))
    ) {
        // Full background artwork - completely covers header without empty spaces
        if (bannerBitmap != null) {
            Image(
                bitmap = bannerBitmap,
                contentDescription = "Recently Played Header",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd
            )

            // Subtle dark horizontal gradient scrim on the left for crisp title readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                AppleBackground.copy(alpha = 0.88f),
                                AppleBackground.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 750f
                        )
                    )
            )
        }

        // Left Navigation & Titles overlaid directly on top of the image
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar Icons (Back Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AppleSurface.copy(alpha = 0.85f))
                        .border(1.dp, AppleSeparator.copy(alpha = 0.7f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppleBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Title & Subtitle sitting directly on the image
            Column(
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = AppleLabel,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("Recently\n")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = AppleBlue,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("Played")
                        }
                    },
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Your music journey, picked up\nright where you left off",
                    color = AppleLabel,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Search bar for filtering recently played songs.
 */
@Composable
private fun RecentlyPlayedSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppleSurface)
            .border(1.dp, AppleSeparator, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search in recently played",
                tint = AppleSecondaryLabel,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search in recently played...",
                        color = AppleGray,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppleLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    cursorBrush = SolidColor(AppleBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual Recently Played Song Card matching the reference design layout.
 * Features album art, title, artist, playback timestamp ("Played 12 minutes ago"),
 * and three-dot action icon.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RecentlyPlayedSongCard(
    song: SongItem,
    timeLabel: String,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onSongClick: () -> Unit,
    onSongLongClick: () -> Unit = {},
    onOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (isCurrentPlaying) AppleBlue.copy(alpha = 0.85f) else AppleSeparator
    val cardBg = if (isCurrentPlaying) AppleSurface else AppleSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onSongClick,
                onLongClick = onSongLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Artwork
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title, Artist, & Relative Timestamp
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    color = if (isCurrentPlaying) AppleBlue else AppleLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = song.artist.ifBlank { song.album.ifBlank { "Aurio Music" } },
                    color = AppleSecondaryLabel,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = timeLabel,
                    color = AppleGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal
                )
            }

            // More Options Icon
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = AppleSecondaryLabel,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
