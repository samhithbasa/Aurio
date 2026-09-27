package com.samhith.aurio.ui.popular

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
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

enum class PopularCategory(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PODCASTS("Podcasts")
}

/**
 * High-fidelity "Popular Right Now" See All screen matching the user's reference design.
 * Features Home_Screen_Banner.png header artwork, in-list search, category filter pills,
 * ranked chart list (#1, #2, #3...), plays & likes metrics, trending badges, direct play buttons,
 * and bottom mini-player + curved nav bar.
 */
@Composable
fun PopularSeeAllScreen(
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

    val progress = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) playbackPositionMs.toFloat() / durationMs.toFloat() else 0f
    }

    var selectedCategory by remember { mutableStateOf(PopularCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var popularTracks by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    // Load Cached Popular Songs (does not generate new songs on entering/leaving)
    LaunchedEffect(Unit) {
        popularTracks = musicRepository.getPopularTracks(forceRefresh = false)
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

    val displayTracks = remember(popularTracks, searchResults, searchQuery, selectedCategory) {
        val baseList = if (searchQuery.isNotBlank()) searchResults else popularTracks
        when (selectedCategory) {
            PopularCategory.ALL -> baseList
            PopularCategory.SONGS -> baseList.filter { !it.album.contains("Playlist", true) }
            PopularCategory.PLAYLISTS -> baseList.filter { it.album.contains("Playlist", true) || it.title.contains("Hits", true) }
            PopularCategory.ALBUMS -> baseList.filter { it.album.isNotBlank() }
            PopularCategory.ARTISTS -> baseList.filter { it.artist.isNotBlank() }
            PopularCategory.PODCASTS -> baseList.filter { it.title.contains("Podcast", true) || it.album.contains("Podcast", true) }
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
            // 1. Top Header Banner with 3D Artwork
            item {
                PopularHeaderSection(
                    bannerBitmap = bannerBitmap,
                    onBackClick = onBackClick
                )
            }

            // 2. Search in Popular Right Now
            item {
                Spacer(modifier = Modifier.height(4.dp))
                PopularSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }

            // 3. Category Filter Pills
            item {
                Spacer(modifier = Modifier.height(4.dp))
                PopularCategoryPills(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 4. Ranked Song Items List
            if (isLoading || isSearching) {
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
            } else if (displayTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No songs found for \"$searchQuery\"" else "No popular tracks available",
                            color = ClaySecondaryLabel,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            } else {
                itemsIndexed(displayTracks, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    val isSongPlaying = isCurrent && isPlaying

                    val rank = index + 1
                    val playsCount = remember(rank) {
                        val base = (14.2 - (rank * 0.95)).coerceAtLeast(1.4)
                        String.format("%.1fM", base)
                    }
                    val likesCount = remember(rank) {
                        val base = (580 - (rank * 36)).coerceAtLeast(65)
                        "${base}K"
                    }

                    PopularRankedSongCard(
                        rank = rank,
                        song = song,
                        playsCount = playsCount,
                        likesCount = likesCount,
                        isCurrentPlaying = isCurrent,
                        isPlaying = isSongPlaying,
                        onSongClick = { playerManager.playSong(song) },
                        onSongLongClick = { selectedSongForAction = song },
                        onOptionsClick = { selectedSongForAction = song },
                        onPlayToggleClick = {
                            if (isCurrent) {
                                playerManager.togglePlayPause()
                            } else {
                                playerManager.playSong(song)
                            }
                        }
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
            val activeSong = currentSong ?: displayTracks.firstOrNull()
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
 * and bold "Popular Right Now" typography overlaid directly on the artwork.
 */
@Composable
private fun PopularHeaderSection(
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
                contentDescription = "Popular Right Now Header",
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
                                ClayBackground.copy(alpha = 0.88f),
                                ClayBackground.copy(alpha = 0.45f),
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
                            append("Popular\n")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = ClayPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("Right Now")
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
                    text = "Trending songs everyone\nis listening to",
                    color = ClayLabel,
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
 * Search bar for filtering popular songs.
 */
@Composable
private fun PopularSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ClaySurface)
            .border(1.dp, ClayInset, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search popular songs",
                tint = ClaySecondaryLabel,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search songs, artists, playlists...",
                        color = ClaySecondaryLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ClayLabel,
                        fontSize = 14.sp,
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
                        contentDescription = "Clear search",
                        tint = ClaySecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Category Filter Pills (All, Songs, Playlists, Albums, Artists, Podcasts).
 */
@Composable
private fun PopularCategoryPills(
    selectedCategory: PopularCategory,
    onCategorySelected: (PopularCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(PopularCategory.values()) { category ->
            val isSelected = category == selectedCategory
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(ClayPrimaryGradient)
                        } else {
                            Modifier
                                .background(ClaySurface)
                                .border(1.dp, ClayInset, RoundedCornerShape(20.dp))
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onCategorySelected(category) }
                    )
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    color = if (isSelected) Color.White else ClaySecondaryLabel,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Individual Ranked Popular Song Card matching the reference design.
 * Features rank number (#1, #2...), album art, title, artist, plays count, likes,
 * Trending badge, circular play button, and three-dot action icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PopularRankedSongCard(
    rank: Int,
    song: SongItem,
    playsCount: String,
    likesCount: String,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onSongClick: () -> Unit,
    onSongLongClick: () -> Unit = {},
    onOptionsClick: () -> Unit = {},
    onPlayToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isCurrentPlaying) ClayPrimary.copy(alpha = 0.85f) else ClayInset
    val cardBg = if (isCurrentPlaying) ClaySurface else ClaySurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onSongClick,
                onLongClick = onSongLongClick
            )
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Large Bold Rank Number (#1, #2...)
            Text(
                text = "$rank",
                color = if (rank <= 3) ClayPrimary else ClayPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(28.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 2. Album Artwork
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 3. Title, Artist, & Metrics Badges (Plays, Likes, Trending)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    color = if (isCurrentPlaying) ClayPrimary else ClayLabel,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
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

                Spacer(modifier = Modifier.height(4.dp))

                // Stats Row: Plays, Likes, Trending Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Plays
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = playsCount,
                            color = ClaySecondaryLabel,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Likes
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = likesCount,
                            color = ClaySecondaryLabel,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Trending Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClaySurface)
                            .border(0.7.dp, ClayPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = ClayPrimary,
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                text = "Trending",
                                color = ClayPrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }

            // 4. Circular Play Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ClayPrimary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayToggleClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 5. More Options Icon
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = ClaySecondaryLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
