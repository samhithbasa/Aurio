package com.samhith.aurio.ui.search

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.PlaylistItem
import com.samhith.aurio.data.music.SearchCategory
import com.samhith.aurio.data.music.SearchResultsGroup
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.artists.ArtistDetailItem
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.library.AddToPlaylistSheet
import kotlinx.coroutines.delay
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.ui.theme.ApplePrimaryGradient

/**
 * Dedicated Full-Page Multi-Category Search Screen.
 * Includes interactive category filter options (All, Songs, Playlists, Artists, Videos),
 * live search box, comprehensive results renderer, and playback integration.
 */
@Composable
fun SearchSeeAllScreen(
    initialQuery: String,
    initialCategory: SearchCategory = SearchCategory.ALL,
    onBackClick: () -> Unit,
    onArtistClick: (ArtistDetailItem) -> Unit = {},
    onOpenFullPlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    var searchResultsGroup by remember { mutableStateOf(SearchResultsGroup()) }
    var songsList by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var artistsList by remember { mutableStateOf<List<ArtistDetailItem>>(emptyList()) }
    var playlistsList by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var videosList by remember { mutableStateOf<List<SongItem>>(emptyList()) }

    var isSearching by remember { mutableStateOf(true) }
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    // Live debounced multi-category search execution
    LaunchedEffect(searchQuery, selectedCategory) {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 2) {
            isSearching = true
            delay(350)
            try {
                when (selectedCategory) {
                    SearchCategory.ALL -> {
                        val group = musicRepository.searchAll(trimmed)
                        searchResultsGroup = group
                        songsList = group.songs
                        artistsList = group.artists
                        playlistsList = group.playlists
                        videosList = group.videos
                    }
                    SearchCategory.SONGS -> {
                        songsList = musicRepository.search(trimmed)
                    }
                    SearchCategory.ARTISTS -> {
                        artistsList = musicRepository.searchArtists(trimmed)
                    }
                    SearchCategory.PLAYLISTS -> {
                        playlistsList = musicRepository.searchPlaylists(trimmed)
                    }
                    SearchCategory.VIDEOS -> {
                        videosList = musicRepository.searchVideos(trimmed)
                    }
                }
            } catch (_: Exception) {
                searchResultsGroup = SearchResultsGroup()
                songsList = emptyList()
                artistsList = emptyList()
                playlistsList = emptyList()
                videosList = emptyList()
            } finally {
                isSearching = false
            }
        } else {
            searchResultsGroup = SearchResultsGroup()
            songsList = emptyList()
            artistsList = emptyList()
            playlistsList = emptyList()
            videosList = emptyList()
            isSearching = false
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
            // 1. Header Bar with Back Button and Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppleSurface.copy(alpha = 0.85f))
                            .border(1.dp, AppleSeparator, CircleShape)
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

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Search Aurio",
                            color = AppleLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = "Results for \"$searchQuery\"",
                                color = AppleSecondaryLabel,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2. In-Page Search Bar
            item {
                Spacer(modifier = Modifier.height(2.dp))
                SearchSeeAllInputBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }

            // 3. Category Filter Chips (All, Songs, Playlists, Artists, Videos)
            item {
                SearchCategoryFilterRow(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }

            // 4. Loading Indicator
            if (isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AppleBlue,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else if (searchQuery.trim().length < 2) {
                // Empty prompt
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = AppleGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Find any song, artist, playlist, or video",
                                color = AppleSecondaryLabel,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            } else {
                // RENDER CONTENT BY SELECTED CATEGORY
                when (selectedCategory) {
                    SearchCategory.ALL -> {
                        renderAllCategorySection(
                            group = searchResultsGroup,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onSongClick = { song, index, queue ->
                                if (currentSong?.id == song.id) {
                                    playerManager.togglePlayPause()
                                } else {
                                    playerManager.playQueue(queue, startIndex = index, source = "Search: $searchQuery")
                                }
                            },
                            onSongLongClick = { selectedSongForAction = it },
                            onArtistClick = onArtistClick,
                            onPlaylistClick = { pl ->
                                // Play playlist songs by query
                                playerManager.playSong(
                                    SongItem(
                                        id = pl.id,
                                        title = pl.title,
                                        artist = pl.subtitle,
                                        thumbnailUrl = pl.thumbnailUrl
                                    )
                                )
                            },
                            onVideoClick = { video ->
                                playerManager.playSong(video)
                            },
                            onViewCategory = { selectedCategory = it }
                        )
                    }

                    SearchCategory.SONGS -> {
                        renderSongsCategorySection(
                            songs = songsList,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onSongClick = { song, index ->
                                if (currentSong?.id == song.id) {
                                    playerManager.togglePlayPause()
                                } else {
                                    playerManager.playQueue(songsList, startIndex = index, source = "Search: $searchQuery")
                                }
                            },
                            onSongLongClick = { selectedSongForAction = it }
                        )
                    }

                    SearchCategory.ARTISTS -> {
                        renderArtistsCategorySection(
                            artists = artistsList,
                            onArtistClick = onArtistClick
                        )
                    }

                    SearchCategory.PLAYLISTS -> {
                        renderPlaylistsCategorySection(
                            playlists = playlistsList,
                            onPlaylistClick = { pl ->
                                playerManager.playSong(
                                    SongItem(
                                        id = pl.id,
                                        title = pl.title,
                                        artist = pl.subtitle,
                                        thumbnailUrl = pl.thumbnailUrl
                                    )
                                )
                            }
                        )
                    }

                    SearchCategory.VIDEOS -> {
                        renderVideosCategorySection(
                            videos = videosList,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onVideoClick = { video, index ->
                                if (currentSong?.id == video.id) {
                                    playerManager.togglePlayPause()
                                } else {
                                    playerManager.playQueue(videosList, startIndex = index, source = "Videos: $searchQuery")
                                }
                            },
                            onVideoLongClick = { selectedSongForAction = it }
                        )
                    }
                }
            }
        }

        // 5. Fixed Bottom Overlay (Mini Player + Curved Bottom Nav Bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong ?: recentTracks.firstOrNull() ?: songsList.firstOrNull()
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
        if (songForPlaylistSheet != null) {
            AddToPlaylistSheet(
                song = songForPlaylistSheet!!,
                onDismiss = { songForPlaylistSheet = null }
            )
        }
    }
}

/**
 * Top Search Input Bar for Search screen.
 */
@Composable
private fun SearchSeeAllInputBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppleSurface)
            .border(1.dp, AppleSeparator, RoundedCornerShape(24.dp))
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
                tint = AppleSecondaryLabel,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search any song, artist, playlist, or video...",
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
                        contentDescription = "Clear",
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Category Filter Chips row (All, Songs, Playlists, Artists, Videos).
 */
@Composable
fun SearchCategoryFilterRow(
    selectedCategory: SearchCategory,
    onCategorySelected: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(SearchCategory.values()) { category ->
            val isSelected = selectedCategory == category

            val icon: ImageVector = when (category) {
                SearchCategory.ALL -> Icons.Default.GraphicEq
                SearchCategory.SONGS -> Icons.Default.MusicNote
                SearchCategory.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                SearchCategory.ARTISTS -> Icons.Default.Person
                SearchCategory.VIDEOS -> Icons.Default.Videocam
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(ApplePrimaryGradient)
                        } else {
                            Modifier
                                .background(AppleSurface)
                                .border(1.dp, AppleSeparator, RoundedCornerShape(20.dp))
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onCategorySelected(category) }
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) AppleOnAccent else AppleSecondaryLabel,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = category.title,
                        color = if (isSelected) AppleOnAccent else AppleSecondaryLabel,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

// ==========================================
// RENDERERS FOR EACH CATEGORY VIEW
// ==========================================

private fun androidx.compose.foundation.lazy.LazyListScope.renderAllCategorySection(
    group: SearchResultsGroup,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongItem, Int, List<SongItem>) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onArtistClick: (ArtistDetailItem) -> Unit,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onVideoClick: (SongItem) -> Unit,
    onViewCategory: (SearchCategory) -> Unit
) {
    val topSong = group.songs.firstOrNull()

    // 1. Top Hit Result Card
    if (topSong != null) {
        item {
            Column {
                Text(
                    text = "Top Match",
                    color = AppleBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                TopHitResultCard(
                    song = topSong,
                    isCurrentPlaying = currentSongId == topSong.id,
                    isPlaying = currentSongId == topSong.id && isPlaying,
                    onPlayClick = { onSongClick(topSong, 0, group.songs) }
                )
            }
        }
    }

    // 2. Songs Section
    if (group.songs.isNotEmpty()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Songs",
                    color = AppleLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "See all",
                    color = AppleBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.clickable { onViewCategory(SearchCategory.SONGS) }
                )
            }
        }

        itemsIndexed(group.songs.take(4), key = { _, song -> "all_song_${song.id}" }) { index, song ->
            val isCurrent = currentSongId == song.id
            SearchTrackCard(
                rank = index + 1,
                song = song,
                isCurrentPlaying = isCurrent,
                isPlaying = isCurrent && isPlaying,
                onSongClick = { onSongClick(song, index, group.songs) },
                onSongLongClick = { onSongLongClick(song) },
                onOptionsClick = { onSongLongClick(song) }
            )
        }
    }

    // 3. Artists Section
    if (group.artists.isNotEmpty()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Artists",
                    color = AppleLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "See all",
                    color = AppleBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.clickable { onViewCategory(SearchCategory.ARTISTS) }
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(group.artists) { artist ->
                    SearchArtistCircleCard(
                        artist = artist,
                        onClick = { onArtistClick(artist) }
                    )
                }
            }
        }
    }

    // 4. Playlists & Albums Section
    if (group.playlists.isNotEmpty()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playlists & Albums",
                    color = AppleLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "See all",
                    color = AppleBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.clickable { onViewCategory(SearchCategory.PLAYLISTS) }
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(group.playlists) { playlist ->
                    SearchPlaylistSquareCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }
        }
    }

    // 5. Videos Section
    if (group.videos.isNotEmpty()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Videos",
                    color = AppleLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "See all",
                    color = AppleBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.clickable { onViewCategory(SearchCategory.VIDEOS) }
                )
            }
        }

        itemsIndexed(group.videos.take(3), key = { _, video -> "all_vid_${video.id}" }) { _, video ->
            SearchVideoCard(
                video = video,
                isCurrentPlaying = currentSongId == video.id,
                isPlaying = currentSongId == video.id && isPlaying,
                onVideoClick = { onVideoClick(video) },
                onVideoLongClick = { onSongLongClick(video) }
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderSongsCategorySection(
    songs: List<SongItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongItem, Int) -> Unit,
    onSongLongClick: (SongItem) -> Unit
) {
    if (songs.isEmpty()) {
        item {
            EmptyCategoryPlaceholder("No songs found for this query")
        }
    } else {
        item {
            Text(
                text = "${songs.size} Songs found",
                color = AppleBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            val isCurrent = currentSongId == song.id
            SearchTrackCard(
                rank = index + 1,
                song = song,
                isCurrentPlaying = isCurrent,
                isPlaying = isCurrent && isPlaying,
                onSongClick = { onSongClick(song, index) },
                onSongLongClick = { onSongLongClick(song) },
                onOptionsClick = { onSongLongClick(song) }
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderArtistsCategorySection(
    artists: List<ArtistDetailItem>,
    onArtistClick: (ArtistDetailItem) -> Unit
) {
    if (artists.isEmpty()) {
        item {
            EmptyCategoryPlaceholder("No artists found for this query")
        }
    } else {
        item {
            Text(
                text = "${artists.size} Artists found",
                color = AppleBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        items(artists, key = { it.name }) { artist ->
            SearchArtistRowCard(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderPlaylistsCategorySection(
    playlists: List<PlaylistItem>,
    onPlaylistClick: (PlaylistItem) -> Unit
) {
    if (playlists.isEmpty()) {
        item {
            EmptyCategoryPlaceholder("No playlists or albums found for this query")
        }
    } else {
        item {
            Text(
                text = "${playlists.size} Playlists & Albums found",
                color = AppleBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        items(playlists, key = { it.id }) { playlist ->
            SearchPlaylistRowCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderVideosCategorySection(
    videos: List<SongItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onVideoClick: (SongItem, Int) -> Unit,
    onVideoLongClick: (SongItem) -> Unit
) {
    if (videos.isEmpty()) {
        item {
            EmptyCategoryPlaceholder("No music videos found for this query")
        }
    } else {
        item {
            Text(
                text = "${videos.size} Music Videos found",
                color = AppleBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        itemsIndexed(videos, key = { _, vid -> vid.id }) { index, video ->
            val isCurrent = currentSongId == video.id
            SearchVideoCard(
                video = video,
                isCurrentPlaying = isCurrent,
                isPlaying = isCurrent && isPlaying,
                onVideoClick = { onVideoClick(video, index) },
                onVideoLongClick = { onVideoLongClick(video) }
            )
        }
    }
}

// ==========================================
// INDIVIDUAL COMPONENT CARDS
// ==========================================

/**
 * Top Hit featured result banner card.
 */
@Composable
private fun TopHitResultCard(
    song: SongItem,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(AppleFill, AppleSurface)
                )
            )
            .border(1.dp, AppleBlue.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPlayClick
            )
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleFill)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.thumbnailUrl.ifBlank { "https://c.saavncdn.com/artists/Arijit_Singh_002_20230323062147_500x500.jpg" })
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isCurrentPlaying && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = AppleLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = song.artist,
                    color = AppleSecondaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppleBlue.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Song",
                            color = AppleBlue,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (song.durationText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = song.durationText,
                            color = AppleGray,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Play / Pause Circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ApplePrimaryGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrentPlaying && isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = AppleOnAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Individual ranked track card in Search results list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchTrackCard(
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
            .background(AppleSurface)
            .border(
                1.dp,
                if (isCurrentPlaying) AppleBlue.copy(alpha = 0.8f) else AppleFill,
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSongClick,
                onLongClick = onSongLongClick
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Rank
            Text(
                text = String.format("%02d", rank),
                color = if (isCurrentPlaying) AppleBlue else AppleGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(26.dp)
            )

            // Artwork
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppleFill)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.thumbnailUrl.ifBlank { "https://c.saavncdn.com/artists/Arijit_Singh_002_20230323062147_500x500.jpg" })
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isCurrentPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    color = if (isCurrentPlaying) AppleBlue else AppleLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${song.artist} ${if (song.durationText.isNotBlank()) "• " + song.durationText else ""}",
                    color = AppleSecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = AppleSecondaryLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Circular artist card for horizontal list in "All" view.
 */
@Composable
private fun SearchArtistCircleCard(
    artist: ArtistDetailItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(88.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(AppleFill)
                .border(1.5.dp, AppleBlue.copy(alpha = 0.5f), CircleShape)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artist.imageUrl.ifBlank { "https://c.saavncdn.com/artists/The_Weeknd_500x500.jpg" })
                    .crossfade(true)
                    .build(),
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            color = AppleLabel,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Artist",
            color = AppleSecondaryLabel,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * Full row card for Artists tab.
 */
@Composable
private fun SearchArtistRowCard(
    artist: ArtistDetailItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppleSurface)
            .border(1.dp, AppleSeparator, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AppleFill)
                    .border(1.dp, AppleBlue.copy(alpha = 0.5f), CircleShape)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artist.imageUrl.ifBlank { "https://c.saavncdn.com/artists/The_Weeknd_500x500.jpg" })
                        .crossfade(true)
                        .build(),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = artist.name,
                        color = AppleLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = AppleBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${artist.genre} • ${artist.monthlyListeners} listeners",
                    color = AppleSecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleFill)
                    .border(1.dp, AppleSeparator, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "View",
                    color = AppleBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Square playlist/album card for horizontal row.
 */
@Composable
private fun SearchPlaylistSquareCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppleFill)
                .border(1.dp, AppleSeparator, RoundedCornerShape(14.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(playlist.thumbnailUrl.ifBlank { "https://c.saavncdn.com/artists/The_Weeknd_500x500.jpg" })
                    .crossfade(true)
                    .build(),
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Type badge overlay
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = playlist.type,
                    color = AppleBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = playlist.title,
            color = AppleLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = playlist.subtitle,
            color = AppleSecondaryLabel,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Full row card for Playlists tab.
 */
@Composable
private fun SearchPlaylistRowCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppleSurface)
            .border(1.dp, AppleSeparator, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleFill)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(playlist.thumbnailUrl.ifBlank { "https://c.saavncdn.com/artists/The_Weeknd_500x500.jpg" })
                        .crossfade(true)
                        .build(),
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    color = AppleLabel,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${playlist.type} • ${playlist.subtitle}",
                    color = AppleSecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = AppleBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 16:9 Video card for Videos section.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchVideoCard(
    video: SongItem,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onVideoClick: () -> Unit,
    onVideoLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppleSurface)
            .border(
                1.dp,
                if (isCurrentPlaying) AppleBlue.copy(alpha = 0.8f) else AppleFill,
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onVideoClick,
                onLongClick = onVideoLongClick
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 16:9 Video Thumbnail
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppleFill)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(video.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play / Video icon overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentPlaying && isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = AppleOnAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Duration badge
                if (video.durationText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = video.durationText,
                            color = AppleOnAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = if (isCurrentPlaying) AppleBlue else AppleLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.artist,
                    color = AppleSecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Placeholder when a category returns 0 results.
 */
@Composable
private fun EmptyCategoryPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = AppleSecondaryLabel,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}
