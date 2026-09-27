package com.samhith.aurio.ui.foryou

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.library.AddToPlaylistSheet
import kotlinx.coroutines.delay
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayPrimaryGradient

enum class ForYouCategory(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PODCASTS("Podcasts")
}

/**
 * Dedicated "For You" See All screen layout matching the user's high-fidelity reference design.
 */
@Composable
fun ForYouSeeAllScreen(
    onBackClick: () -> Unit,
    onLikedSongsClick: () -> Unit = {},
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

    var selectedCategory by remember { mutableStateOf(ForYouCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var forYouTracks by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    val likedSongsMap = remember { mutableStateMapOf<String, Boolean>() }

    // Load Top Artwork
    val forYouHeaderBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("For_you.png").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    // Initial Load based on taste (cached from home)
    LaunchedEffect(Unit) {
        isLoading = true
        forYouTracks = musicRepository.getForYouTracks(forceRefresh = false)
        isLoading = false
    }

    // Debounced Search
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            isSearching = true
            delay(350)
            searchResults = musicRepository.search(searchQuery.trim())
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    val displayTracks = remember(forYouTracks, searchResults, searchQuery, selectedCategory) {
        val baseList = if (searchQuery.isNotBlank()) {
            searchResults
        } else {
            forYouTracks
        }

        when (selectedCategory) {
            ForYouCategory.ALL -> baseList
            ForYouCategory.SONGS -> baseList.filter { !it.album.contains("Playlist", ignoreCase = true) }
            ForYouCategory.PLAYLISTS -> baseList.filter { it.album.contains("Playlist", ignoreCase = true) || it.title.contains("Vibes", true) || it.title.contains("Hits", true) }
            ForYouCategory.ARTISTS -> baseList.filter { it.artist.isNotBlank() }
            ForYouCategory.ALBUMS -> baseList.filter { it.album.isNotBlank() }
            ForYouCategory.PODCASTS -> baseList.filter { it.title.contains("Podcast", true) || it.album.contains("Podcast", true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Header Section (Spans across both columns)
            item(span = { GridItemSpan(2) }) {
                ForYouHeaderSection(
                    forYouHeaderBitmap = forYouHeaderBitmap,
                    onBackClick = onBackClick
                )
            }

            // 2. Search Bar (Spans across both columns)
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(6.dp))
                ForYouSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }

            // 3. Category Filter Pills (Spans across both columns)
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(6.dp))
                ForYouCategoryPills(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. Liked Songs Card as the first prominent item (when on All or Playlists)
            if (searchQuery.isBlank() && (selectedCategory == ForYouCategory.ALL || selectedCategory == ForYouCategory.PLAYLISTS)) {
                item {
                    ForYouLikedSongsGridCard(
                        tracksCount = likedSongIds.size.coerceAtLeast(forYouTracks.size),
                        isPlaying = isPlaying,
                        onClick = onLikedSongsClick
                    )
                }
            }

            // 5. Grid Song Cards
            if (isLoading || isSearching) {
                item(span = { GridItemSpan(2) }) {
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
            } else if (displayTracks.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No results found for \"$searchQuery\"" else "No tracks available in this category",
                            color = ClaySecondaryLabel,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            } else {
                items(displayTracks, key = { it.id }) { song ->
                    val isCurrent = currentSong?.id == song.id
                    val isLiked = likedSongIds.contains(song.id)

                    ForYouSongGridCard(
                        song = song,
                        isCurrentPlaying = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isLiked = isLiked,
                        onPlayClick = { playerManager.playSong(song) },
                        onLikeClick = {
                            playerManager.toggleLikeSong(song)
                        },
                        onLongClick = {
                            selectedSongForAction = song
                        }
                    )
                }
            }
        }

        // 6. Fixed Bottom Overlay (Mini Player + Curved Bottom Nav Bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong ?: recentTracks.firstOrNull() ?: forYouTracks.firstOrNull()
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
                        putExtra(Intent.EXTRA_TEXT, "Listening to ${song.title} by ${song.artist} on Aurio Music 🎵")
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
 * Top Header section with Back Button, Title, Subtitle, and prominent top-right 3D character graphic.
 */
@Composable
private fun ForYouHeaderSection(
    forYouHeaderBitmap: ImageBitmap?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(22.dp))
    ) {
        // Full background artwork - completely covers header width & height without black gaps
        if (forYouHeaderBitmap != null) {
            Image(
                bitmap = forYouHeaderBitmap,
                contentDescription = "For You Graphic",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd
            )

            // Subtle dark gradient scrim on the left for crisp title legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ClayBackground.copy(alpha = 0.85f),
                                ClayBackground.copy(alpha = 0.40f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 700f
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
            // Top Bar Icons
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
                        .background(ClaySurface.copy(alpha = 0.85f))
                        .border(1.dp, ClayInset.copy(alpha = 0.7f), CircleShape)
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

                // Search Action Icon (with translucent glass background)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ClaySurface.copy(alpha = 0.85f))
                        .border(1.dp, ClayInset.copy(alpha = 0.7f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* Focus Search */ }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ClayLabel,
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
                                color = ClayLabel,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("For ")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = ClayPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("You")
                        }
                    },
                    fontSize = 34.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Special for you, based on what you listened to",
                    color = ClayLabel,
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Rounded Search Bar for filtering For You contents.
 */
@Composable
private fun ForYouSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ClaySurface)
            .border(1.dp, ClayInset, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (searchQuery.isNotEmpty()) ClayPrimary else ClaySecondaryLabel,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search songs, artists, playlists...",
                        color = ClaySecondaryLabel,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = ClayLabel,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    cursorBrush = SolidColor(ClayPrimary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(26.dp)
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
 * Category Filter Pills (All, Songs, Playlists, Artists, Albums, Podcasts).
 */
@Composable
private fun ForYouCategoryPills(
    selectedCategory: ForYouCategory,
    onCategorySelected: (ForYouCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ForYouCategory.values()) { category ->
            val isSelected = category == selectedCategory
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) ClayPrimaryGradient else SolidColor(ClaySurface)
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else ClayInset,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onCategorySelected(category) }
                    )
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else ClaySecondaryLabel
                )
            }
        }
    }
}

/**
 * Liked Songs Grid Card with glowing Heart artwork.
 */
@Composable
private fun ForYouLikedSongsGridCard(
    tracksCount: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ClaySurface)
            .border(1.dp, ClayInset, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Heart Glowing Box
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(ClayPrimary.copy(alpha = 0.12f), ClayPrimary.copy(alpha = 0.12f), ClayBackground)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .aurioGlow(
                            color = ClayPrimary,
                            alpha = 0.5f,
                            blurRadius = 12.dp
                        )
                        .clip(CircleShape)
                        .background(ClayPrimaryGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = ClayLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Playlist • $tracksCount tracks",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = ClaySecondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Red Play Button
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ClayPrimaryGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 2-Column Song Card with Album Art, Title, Subtitle, Heart button, and Play button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForYouSongGridCard(
    song: SongItem,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    onPlayClick: () -> Unit,
    onLikeClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ClaySurface)
            .border(
                1.dp,
                if (isCurrentPlaying) ClayPrimary.copy(alpha = 0.8f) else ClayInset,
                RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPlayClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ClayPrimaryGradient),
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
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title and Subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentPlaying) ClayPrimary else ClayLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = ClaySecondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right side actions: Heart + Play Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(64.dp)
            ) {
                // Heart Toggle
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isLiked) ClayPrimary else ClaySecondaryLabel,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLikeClick
                        )
                )

                // Circular Play Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ClayPrimaryGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentPlaying && isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
