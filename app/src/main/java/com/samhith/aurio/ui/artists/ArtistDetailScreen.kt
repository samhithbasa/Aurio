package com.samhith.aurio.ui.artists

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.rememberCoroutineScope
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.home.ArtistProfile
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.library.AddToPlaylistSheet
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayPrimaryGradient

/**
 * Dedicated Artist Detail Screen displaying artist profile, monthly listeners,
 * action buttons ("Play All", "Shuffle"), in-list search, and full list of tracks.
 */
@Composable
fun ArtistDetailScreen(
    artist: ArtistDetailItem,
    onBackClick: () -> Unit,
    onOpenFullPlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val musicRepository = remember { MusicRepository.getInstance().apply { initialize(context) } }
    val libraryRepository = remember { LibraryRepository.getInstance().apply { initialize(context) } }

    val followedArtistNames by libraryRepository.followedArtistNames.collectAsState()
    val isFollowing = followedArtistNames.contains(artist.name)

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

    var artistSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    // Fetch strictly verified artist songs on load
    LaunchedEffect(artist.name) {
        isLoading = true
        try {
            artistSongs = musicRepository.getArtistTopTracks(artist.name)
        } catch (_: Exception) {
            artistSongs = emptyList()
        } finally {
            isLoading = false
        }
    }

    val displaySongs = remember(artistSongs, searchQuery) {
        if (searchQuery.isBlank()) {
            artistSongs
        } else {
            artistSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Artist Hero Header
            item {
                ArtistDetailHeader(
                    artist = artist,
                    isFollowing = isFollowing,
                    onToggleFollow = {
                        libraryRepository.toggleFollowArtist(
                            ArtistProfile(name = artist.name, imageUrl = artist.imageUrl)
                        )
                    },
                    onBackClick = onBackClick,
                    songsCount = artistSongs.size,
                    onPlayAll = {
                        if (artistSongs.isNotEmpty()) {
                            playerManager.playQueue(artistSongs)
                        }
                    },
                    onShuffle = {
                        if (artistSongs.isNotEmpty()) {
                            val shuffled = artistSongs.shuffled()
                            playerManager.playQueue(shuffled)
                        }
                    }
                )
            }

            // 2. Search in Artist's Songs
            item {
                Spacer(modifier = Modifier.height(2.dp))
                ArtistSongSearchBar(
                    artistName = artist.name,
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 3. Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Search Results" else "Top Tracks",
                        color = ClayLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    if (artistSongs.isNotEmpty()) {
                        Text(
                            text = "${displaySongs.size} songs",
                            color = ClaySecondaryLabel,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // 4. Track List
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ClayPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else if (displaySongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No songs matching \"$searchQuery\"" else "No songs found for ${artist.name}",
                            color = ClaySecondaryLabel,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            } else {
                itemsIndexed(displaySongs, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    val isSongPlaying = isCurrent && isPlaying
                    val rank = index + 1

                    ArtistSongCard(
                        rank = rank,
                        song = song,
                        isCurrentPlaying = isCurrent,
                        isPlaying = isSongPlaying,
                        onSongClick = {
                            if (isCurrent) {
                                playerManager.togglePlayPause()
                            } else {
                                playerManager.playQueue(displaySongs, startIndex = index)
                            }
                        },
                        onSongLongClick = { selectedSongForAction = song },
                        onOptionsClick = { selectedSongForAction = song }
                    )
                }
            }
        }

        // 5. Fixed Bottom Overlay (Mini Player + Curved Bottom Nav Bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong ?: recentTracks.firstOrNull() ?: artistSongs.firstOrNull()
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
                onToggleLike = { playerManager.toggleLikeSong(song) },
                onAddToPlaylist = { songForPlaylistSheet = song },
                onShare = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Listening to ${song.title} by ${song.artist} on Aurio Music \uD83C\uDFB5"
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                },
                onPlayNow = { playerManager.playSong(song) }
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
 * Artist Profile Header with avatar, glow, metadata, back button, and Play/Shuffle actions.
 */
@Composable
private fun ArtistDetailHeader(
    artist: ArtistDetailItem,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onBackClick: () -> Unit,
    songsCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ClaySurface,
                        ClaySurface,
                        ClayBackground
                    )
                )
            )
            .border(1.dp, ClayPrimary.copy(alpha = 0.27f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ClaySurface.copy(alpha = 0.85f))
                        .border(1.dp, ClayInset, CircleShape)
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
                        tint = ClayPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Artist Profile",
                    color = ClaySecondaryLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large Artist Circular Avatar with Glowing Edge
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .aurioGlow(
                        color = ClayPrimary,
                        alpha = 0.35f,
                        blurRadius = 24.dp
                    )
                    .clip(CircleShape)
                    .background(ClaySurface)
                    .border(2.dp, ClayPrimary.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (artist.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ClayPrimary,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Artist Name + Verified Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = artist.name,
                    color = ClayLabel,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verified Artist",
                    tint = ClayPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Genre & Monthly Listeners Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = artist.genre,
                    color = ClayPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )

                Text(
                    text = "•",
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = ClaySecondaryLabel,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${artist.monthlyListeners} monthly listeners",
                        color = ClaySecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Follow / Following Pill Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isFollowing) {
                            Modifier
                                .background(ClayInset)
                                .border(1.dp, ClayPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        } else {
                            Modifier.background(ClayPrimaryGradient)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleFollow
                    )
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFollowing) "Following" else "+ Follow",
                    color = if (isFollowing) ClayPrimary else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Play All & Shuffle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play All Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ClayPrimaryGradient)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayAll
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Play All",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                // Shuffle Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ClaySurface)
                        .border(1.dp, ClayInset, RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onShuffle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = ClayPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Shuffle",
                            color = ClayLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search bar for filtering the artist's tracks.
 */
@Composable
private fun ArtistSongSearchBar(
    artistName: String,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(ClaySurface)
            .border(1.dp, ClayInset, RoundedCornerShape(23.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = ClaySecondaryLabel,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search in $artistName songs...",
                        color = ClaySecondaryLabel,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ClayLabel,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    cursorBrush = SolidColor(ClayPrimary),
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
                        contentDescription = "Clear",
                        tint = ClaySecondaryLabel,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual track card in the artist's discography list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistSongCard(
    rank: Int,
    song: SongItem,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onSongClick: () -> Unit,
    onSongLongClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ClaySurface)
            .border(
                1.dp,
                if (isCurrentPlaying) ClayPrimary.copy(alpha = 0.8f) else ClayInset,
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSongClick,
                onLongClick = onSongLongClick
            )
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Number / Playing indicator
            if (isCurrentPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = ClayPrimary,
                    modifier = Modifier
                        .width(26.dp)
                        .size(18.dp)
                )
            } else {
                Text(
                    text = "$rank",
                    color = if (rank <= 3) ClayPrimary else ClaySecondaryLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.width(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Thumbnail
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ClaySurface),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
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
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isCurrentPlaying && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x77000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = ClayPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song Info (Title, Artist, Duration)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    color = if (isCurrentPlaying) ClayPrimary else ClayLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${song.artist} ${if (song.durationText.isNotBlank()) "• " + song.durationText else ""}",
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Options 3-dots
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = ClaySecondaryLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
