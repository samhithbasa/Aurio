package com.samhith.aurio.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samhith.aurio.data.music.PlaylistItem
import com.samhith.aurio.data.music.SearchCategory
import com.samhith.aurio.ui.artists.ArtistDetailItem
import com.samhith.aurio.ui.search.SearchCategoryFilterRow
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.auth.UserAccount
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
import kotlinx.coroutines.launch
import java.util.Calendar
import com.samhith.aurio.ui.theme.*


data class ArtistProfile(
    val name: String,
    val imageUrl: String
)

/**
 * Modern High-Fidelity Aurio Home Screen layout with modular high-performance composables,
 * Pull-To-Refresh Blur Animation, Long-Press Song Actions Dialog, and Taste-Based Recommendations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: UserAccount?,
    logoBitmap: ImageBitmap?,
    onSignOutClick: () -> Unit,
    onSeeAllClick: (section: String) -> Unit = {},
    onArtistClick: (ArtistProfile) -> Unit = {},
    onOpenFullPlayer: () -> Unit = {},
    onListenTogetherClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableStateOf(HomeTab.HOME) }
    var searchQuery by remember { mutableStateOf("") }

    // Audio Player & Music Repository Instances
    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val musicRepository = remember { MusicRepository.getInstance().apply { initialize(context) } }

    // Reactive Player State
    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val likedSongIds by playerManager.likedSongIds.collectAsState()

    // Dynamic Recently Played State
    val recentTracks by musicRepository.recentlyPlayed.collectAsState()

    val progress = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) playbackPositionMs.toFloat() / durationMs.toFloat() else 0f
    }

    // Dynamic Track Shelves State
    var popularTracks by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var forYouTracks by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var selectedSearchCategory by remember { mutableStateOf(SearchCategory.ALL) }
    var searchResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var artistSearchResults by remember { mutableStateOf<List<ArtistDetailItem>>(emptyList()) }
    var playlistSearchResults by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Long-Press Song Context Dialog state
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    // Top-Right Header Dialogs (Notifications / Updates & Profile)
    var showUpdatesDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    // Pull to Refresh State
    var isRefreshing by remember { mutableStateOf(false) }

    // Initial Catalog Ingestion (cached, does not reload on screen return)
    LaunchedEffect(Unit) {
        popularTracks = musicRepository.getPopularTracks(forceRefresh = false)
        forYouTracks = musicRepository.getForYouTracks(forceRefresh = false)
    }

    // Refresh Trigger Execution (pull-to-refresh explicitly triggers fresh personalized recommendations)
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            scope.launch {
                val freshPopular = musicRepository.getPopularTracks(forceRefresh = true)
                val freshForYou = musicRepository.getForYouTracks(forceRefresh = true)
                delay(1500)
                popularTracks = freshPopular
                forYouTracks = freshForYou
                isRefreshing = false
            }
        }
    }

    // Debounced Multi-Category Live Search Ingestion
    LaunchedEffect(searchQuery, selectedSearchCategory) {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 2) {
            isSearching = true
            delay(350)
            try {
                when (selectedSearchCategory) {
                    SearchCategory.ALL, SearchCategory.SONGS -> {
                        searchResults = musicRepository.search(trimmed)
                    }
                    SearchCategory.ARTISTS -> {
                        artistSearchResults = musicRepository.searchArtists(trimmed)
                    }
                    SearchCategory.PLAYLISTS -> {
                        playlistSearchResults = musicRepository.searchPlaylists(trimmed)
                    }
                    SearchCategory.VIDEOS -> {
                        searchResults = musicRepository.searchVideos(trimmed)
                    }
                }
            } catch (_: Exception) {
                searchResults = emptyList()
                artistSearchResults = emptyList()
                playlistSearchResults = emptyList()
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            artistSearchResults = emptyList()
            playlistSearchResults = emptyList()
            isSearching = false
        }
    }

    // Load Hero Banner Asset
    val heroBannerBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("Home_Screen_Banner.png").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    // Load Refresh Screen Asset
    val refreshScreenBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("Refresh_Screen.png").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    // Curated Popular Artists with Verified High-Res CDN Photos
    val popularArtists = remember {
        listOf(
            ArtistProfile("The Weeknd", "https://c.saavncdn.com/artists/The_Weeknd_500x500.jpg"),
            ArtistProfile("Taylor Swift", "https://c.saavncdn.com/artists/Taylor_Swift_500x500.jpg"),
            ArtistProfile("Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_002_20230323062147_500x500.jpg"),
            ArtistProfile("Bruno Mars", "https://c.saavncdn.com/artists/Bruno_Mars_500x500.jpg"),
            ArtistProfile("Drake", "https://c.saavncdn.com/artists/Drake_500x500.jpg"),
            ArtistProfile("Ed Sheeran", "https://c.saavncdn.com/artists/Ed_Sheeran_500x500.jpg"),
            ArtistProfile("Diljit Dosanjh", "https://c.saavncdn.com/artists/Diljit_Dosanjh_500x500.jpg"),
            ArtistProfile("Justin Bieber", "https://c.saavncdn.com/artists/Justin_Bieber_500x500.jpg"),
            ArtistProfile("Selena Gomez", "https://c.saavncdn.com/artists/Selena_Gomez_-_The_Scene_20200218135350_500x500.jpg"),
            ArtistProfile("Atif Aslam", "https://c.saavncdn.com/artists/Atif_Aslam_500x500.jpg"),
            ArtistProfile("Badshah", "https://c.saavncdn.com/artists/Badshah_500x500.jpg"),
            ArtistProfile("Sid Sriram", "https://c.saavncdn.com/artists/Sid_Sriram_500x500.jpg"),
            ArtistProfile("Armaan Malik", "https://c.saavncdn.com/artists/Armaan_Malik_500x500.jpg"),
            ArtistProfile("Shawn Mendes", "https://c.saavncdn.com/artists/Shawn_Mendes_500x500.jpg"),
            ArtistProfile("Eminem", "https://c.saavncdn.com/artists/Eminem_500x500.jpg"),
            ArtistProfile("Lady Gaga", "https://c.saavncdn.com/060/Die-With-A-Smile-English-2024-20240816103634-500x500.jpg")
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        // Main Scrollable Home Content (blurred when refreshing)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            indicator = {}, // Using custom RefreshScreenOverlay instead
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .blur(if (isRefreshing) 20.dp else 0.dp)
                .verticalScroll(scrollState)
                .padding(bottom = 160.dp) // Space for floating MiniPlayer & Curved Nav Bar
        ) {
            // 1. Header Section
            HomeScreenHeader(
                user = user,
                logoBitmap = logoBitmap,
                onRefreshClick = { isRefreshing = true },
                onNotificationClick = { showUpdatesDialog = true },
                onProfileClick = onProfileClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Search Bar
            HomeScreenSearchBar(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2b. Category Filter Chips
            SearchCategoryFilterRow(
                selectedCategory = selectedSearchCategory,
                onCategorySelected = { category ->
                    selectedSearchCategory = category
                    if (searchQuery.isBlank()) {
                        if (category == SearchCategory.ARTISTS) onSeeAllClick("artists")
                        else if (category == SearchCategory.PLAYLISTS) onSeeAllClick("for_you")
                        else if (category == SearchCategory.SONGS) onSeeAllClick("popular")
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Search Results or Regular Home Sections
            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                HomeScreenSearchResults(
                    searchQuery = searchQuery,
                    selectedCategory = selectedSearchCategory,
                    isSearching = isSearching,
                    searchResults = searchResults,
                    artistResults = artistSearchResults,
                    playlistResults = playlistSearchResults,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onSongClick = { playerManager.playSong(it) },
                    onSongLongClick = { selectedSongForAction = it },
                    onArtistClick = { artistItem ->
                        onArtistClick(
                            ArtistProfile(
                                name = artistItem.name,
                                imageUrl = artistItem.imageUrl
                            )
                        )
                    },
                    onPlaylistClick = { pl ->
                        playerManager.playSong(
                            SongItem(
                                id = pl.id,
                                title = pl.title,
                                artist = pl.subtitle,
                                thumbnailUrl = pl.thumbnailUrl
                            )
                        )
                    },
                    onSeeAllClick = { onSeeAllClick("search:${selectedSearchCategory.name}:$searchQuery") }
                )
            } else {
                Spacer(modifier = Modifier.height(20.dp))

                // 3. Hero Banner
                HomeScreenHeroBanner(
                    heroBannerBitmap = heroBannerBitmap,
                    onBannerClick = {
                        if (popularTracks.isNotEmpty()) {
                            playerManager.playSong(popularTracks.first())
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 4. "For You" Section
                HomeForYouSection(
                    forYouTracks = forYouTracks,
                    popularTracks = popularTracks,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onLikedSongsClick = { onSeeAllClick("liked_songs") },
                    onSongClick = { playerManager.playSong(it) },
                    onSongLongClick = { selectedSongForAction = it },
                    onSeeAllClick = { onSeeAllClick("for_you") }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 5. "Recently Played" Section
                HomeRecentlyPlayedSection(
                    recentTracks = recentTracks,
                    fallbackTracks = popularTracks,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onSongClick = { playerManager.playSong(it) },
                    onSongLongClick = { selectedSongForAction = it },
                    onSeeAllClick = { onSeeAllClick("recently_played") }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 6. "Popular Right Now" Section
                HomePopularTracksSection(
                    popularTracks = popularTracks,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onSongClick = { playerManager.playSong(it) },
                    onSongLongClick = { selectedSongForAction = it },
                    onSeeAllClick = { onSeeAllClick("popular") }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 7. "Popular Artists" Section
                HomePopularArtistsSection(
                    artists = popularArtists,
                    onArtistClick = onArtistClick,
                    onSeeAllClick = { onSeeAllClick("artists") }
                )
            }
        }
        }

        // --- FULL-SCREEN BLUR REFRESH OVERLAY ---
        AnimatedVisibility(
            visible = isRefreshing,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            RefreshScreenOverlay(
                refreshScreenBitmap = refreshScreenBitmap
            )
        }

        // 8. Fixed Bottom Overlay (Mini Player + Curved Bottom Nav Bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong ?: recentTracks.firstOrNull() ?: popularTracks.firstOrNull()
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
                selectedTab = selectedTab,
                onTabSelected = {
                    when (it) {
                        HomeTab.LIBRARY -> onLibraryClick()
                        HomeTab.LISTEN_TOGETHER -> onListenTogetherClick()
                        HomeTab.PROFILE -> onProfileClick()
                        else -> selectedTab = it
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

        // --- APP UPDATES & NEW FEATURES DIALOG ---
        if (showUpdatesDialog) {
            AppUpdatesDialog(
                onDismiss = { showUpdatesDialog = false }
            )
        }

        // --- USER PROFILE PREVIEW DIALOG ---
        if (showProfileDialog) {
            UserProfileDialog(
                user = user,
                recentTracksCount = recentTracks.size,
                likedSongsCount = likedSongIds.size,
                onDismiss = { showProfileDialog = false }
            )
        }
    }
}

/**
 * Animated Frosted Blur Refresh Overlay with Refresh_Screen.png.
 */
@Composable
private fun RefreshScreenOverlay(
    refreshScreenBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                if (refreshScreenBitmap != null) {
                    Image(
                        bitmap = refreshScreenBitmap,
                        contentDescription = "Refreshing Aurio",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(
                        color = ClayPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Curating Your Vibe... 🎵",
                color = ClayLabel,
                fontSize = 16.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Refreshing music recommendations based on your taste",
                color = ClaySecondaryLabel,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Header with Logo, Greeting + User Name, Notification Bell (App Updates), and Profile Avatar.
 */
@Composable
private fun HomeScreenHeader(
    user: UserAccount?,
    logoBitmap: ImageBitmap?,
    onRefreshClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }

    val firstName = remember(user) {
        user?.displayName?.trim()?.split(" ")?.firstOrNull()
            ?: user?.email?.split("@")?.firstOrNull()
            ?: ""
    }

    val greetingText = remember(greeting, firstName) {
        if (firstName.isNotBlank()) {
            "$greeting ${firstName.replaceFirstChar { it.uppercase() }}"
        } else {
            greeting
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRefreshClick
            )
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "Aurio Logo",
                    modifier = Modifier
                        .height(34.dp)
                        .width(90.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = greetingText,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                color = ClaySecondaryLabel
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Let Aurio Set the Mood 🎵",
                fontSize = 22.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = ClayLabel,
                letterSpacing = 0.2.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Notification Bell (App Updates & New Features)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clayCircle(elevation = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNotificationClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Latest Updates & Features",
                    tint = ClayLabel,
                    modifier = Modifier.size(20.dp)
                )

                // Glowing periwinkle indicator dot for latest announcements
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(ClayPrimary)
                )
            }

            // Profile Picture (Opens User Profile Dialog)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clayCircle(elevation = 5.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onProfileClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!user?.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(user!!.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (!user?.displayName.isNullOrBlank()) {
                    Text(
                        text = user?.displayName!!.take(1).uppercase(),
                        color = ClayPrimary,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = ClayPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 20px debossed Inset Clay Search Bar.
 */
@Composable
private fun HomeScreenSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(52.dp)
            .clayInset(cornerRadius = 20.dp)
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
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search songs, artists, playlists...",
                        color = ClaySecondaryLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal
                    )
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = ClayLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(ClayPrimary),
                    singleLine = true,
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Search results presentation overlay.
 */
@Composable
private fun HomeScreenSearchResults(
    searchQuery: String,
    selectedCategory: SearchCategory,
    isSearching: Boolean,
    searchResults: List<SongItem>,
    artistResults: List<ArtistDetailItem>,
    playlistResults: List<PlaylistItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onArtistClick: (ArtistDetailItem) -> Unit,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = when (selectedCategory) {
                SearchCategory.ALL -> "Search Results"
                SearchCategory.SONGS -> "Songs"
                SearchCategory.ARTISTS -> "Artists"
                SearchCategory.PLAYLISTS -> "Playlists & Albums"
                SearchCategory.VIDEOS -> "Music Videos"
            },
            onSeeAllClick = onSeeAllClick
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ClayPrimary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (selectedCategory == SearchCategory.ARTISTS) {
            if (artistResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No artists found for \"$searchQuery\"",
                        color = ClaySecondaryLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(artistResults) { artist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(88.dp)
                                .clickable { onArtistClick(artist) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(ClayInset)
                                    .border(1.5.dp, ClayPrimary.copy(alpha = 0.5f), CircleShape)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
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
                                color = ClayLabel,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else if (selectedCategory == SearchCategory.PLAYLISTS) {
            if (playlistResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No playlists found for \"$searchQuery\"",
                        color = ClaySecondaryLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(playlistResults) { pl ->
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clickable { onPlaylistClick(pl) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ClayInset)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(pl.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = pl.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pl.title,
                                color = ClayLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks found for \"$searchQuery\"",
                        color = ClaySecondaryLabel,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    searchResults.take(6).forEach { song ->
                        SongSearchItemRow(
                            song = song,
                            isCurrentPlaying = currentSongId == song.id,
                            isPlaying = isPlaying && currentSongId == song.id,
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Featured Hero Banner with custom script text.
 */
@Composable
private fun HomeScreenHeroBanner(
    heroBannerBitmap: ImageBitmap?,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(210.dp)
            .clayCard(cornerRadius = 24.dp, elevation = 8.dp)
            .clickable(onClick = onBannerClick)
    ) {
        if (heroBannerBitmap != null) {
            Image(
                bitmap = heroBannerBitmap,
                contentDescription = "Featured Banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ClayBackground.copy(alpha = 0.93f),
                            ClayBackground.copy(alpha = 0.60f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 600f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, top = 20.dp, bottom = 20.dp, end = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "MUSIC\nALWAYS WITH YOU",
                    color = ClayLabel,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    lineHeight = 13.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = ClayLabel,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("Feel Every\n")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = ClayPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("Moment")
                        }
                    },
                    fontSize = 26.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Play. Share. Connect.",
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Text(
                text = "Good Music\nBrighter Days",
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Normal,
                color = ClayPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.End,
                lineHeight = 18.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .rotate(-6f)
                    .padding(end = 8.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * "For You" Section with Exact 140dp x 120dp "Liked Songs" Card + Dynamic Recommendations.
 */
@Composable
private fun HomeForYouSection(
    forYouTracks: List<SongItem>,
    popularTracks: List<SongItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onLikedSongsClick: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "For You",
            onSeeAllClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Persistent "Liked Songs" Card (Puffy 3D Clay styling)
            item {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .clayCard(cornerRadius = 20.dp, elevation = 6.dp)
                        .clickable(onClick = onLikedSongsClick)
                        .padding(10.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ClayPeachGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clayCircle(elevation = 5.dp, gradient = ClayCardGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Liked Songs",
                                    tint = ClayPeachDark,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Liked Songs",
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            color = ClayLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Playlist • ${popularTracks.size} tracks",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = ClaySecondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Dynamic For You Songs
            items(forYouTracks) { song ->
                SongCardItem(
                    song = song,
                    isCurrentPlaying = currentSongId == song.id,
                    isPlaying = isPlaying && currentSongId == song.id,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) }
                )
            }
        }
    }
}

/**
 * "Recently Played" Section Updating Reactively from History.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRecentlyPlayedSection(
    recentTracks: List<SongItem>,
    fallbackTracks: List<SongItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lastPlayedSong = recentTracks.firstOrNull() ?: fallbackTracks.firstOrNull()

    Column(modifier = modifier) {
        SectionHeader(
            title = "Recently Played",
            onSeeAllClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick-Play "Pick Up Where You Left" Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .clayCard(cornerRadius = 20.dp, elevation = 6.dp, gradient = ClayPrimaryGradient)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (lastPlayedSong != null) {
                                onSongClick(lastPlayedSong)
                            }
                        },
                        onLongClick = {
                            if (lastPlayedSong != null) {
                                onSongLongClick(lastPlayedSong)
                            }
                        }
                    )
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clayCircle(elevation = 4.dp, gradient = ClayCardGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying && currentSongId == lastPlayedSong?.id) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = ClayPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Pick Up\nWhere You Left",
                            fontSize = 15.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lastPlayedSong?.title ?: "Continue Listening",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Recent Mini Track Cards
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val displayRecent = if (recentTracks.isNotEmpty()) recentTracks.take(2) else fallbackTracks.take(2)
                displayRecent.forEach { song ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .clayCard(
                                cornerRadius = 18.dp,
                                elevation = if (currentSongId == song.id) 7.dp else 4.dp,
                                gradient = if (currentSongId == song.id) ClayPrimaryGradient else ClayCardGradient
                            )
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSongClick(song) },
                                onLongClick = { onSongLongClick(song) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ClayInset),
                                contentAlignment = Alignment.Center
                            ) {
                                if (song.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.thumbnailUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        modifier = Modifier.size(44.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (currentSongId == song.id) Color.White else ClayPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = if (currentSongId == song.id) Color.White else ClayLabel,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = if (currentSongId == song.id) Color.White.copy(alpha = 0.8f) else ClaySecondaryLabel,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
 * "Popular Right Now" Horizontal Shelf.
 */
@Composable
private fun HomePopularTracksSection(
    popularTracks: List<SongItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "Popular Right Now",
            onSeeAllClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(popularTracks) { song ->
                SongCardItem(
                    song = song,
                    isCurrentPlaying = currentSongId == song.id,
                    isPlaying = isPlaying && currentSongId == song.id,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) }
                )
            }
        }
    }
}

/**
 * "Popular Artists" Section showing two artists stacked vertically per column.
 */
@Composable
private fun HomePopularArtistsSection(
    artists: List<ArtistProfile>,
    onArtistClick: (ArtistProfile) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "Popular Artists",
            onSeeAllClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val artistPairs = artists.chunked(2)
            items(artistPairs) { pair ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { artist ->
                        ArtistBubbleItem(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Artist item card showing high-res circular photo and artist name.
 */
@Composable
private fun ArtistBubbleItem(
    artist: ArtistProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .width(152.dp)
            .clayCard(cornerRadius = 18.dp, elevation = 4.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clayCircle(elevation = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (artist.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artist.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = artist.name.take(1),
                    color = ClayPrimary,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = ClayLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Artist",
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                color = ClaySecondaryLabel
            )
        }
    }
}

/**
 * High-Fidelity Song Card for Horizontal Shelves with combined click & long press support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongCardItem(
    song: SongItem,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .width(140.dp)
            .clayCard(
                cornerRadius = 20.dp,
                elevation = if (isCurrentPlaying) 8.dp else 5.dp,
                gradient = if (isCurrentPlaying) ClayPrimaryGradient else ClayCardGradient
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ClayInset),
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
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = if (isCurrentPlaying) Color.White else ClayPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.dp, end = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentPlaying && isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isCurrentPlaying) Color.White else ClayPrimaryLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = song.title,
                fontSize = 13.5.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = if (isCurrentPlaying) Color.White else ClayLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = song.artist,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.SansSerif,
                color = if (isCurrentPlaying) Color.White.copy(alpha = 0.8f) else ClaySecondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Row layout for displaying live search query results with long press support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongSearchItemRow(
    song: SongItem,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(
                cornerRadius = 18.dp,
                elevation = if (isCurrentPlaying) 7.dp else 4.dp,
                gradient = if (isCurrentPlaying) ClayPrimaryGradient else ClayCardGradient
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClayInset),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(song.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrentPlaying) Color.White else ClayPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrentPlaying) Color.White else ClayLabel,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} ${if (song.durationText.isNotBlank()) "• " + song.durationText else ""}",
                    color = if (isCurrentPlaying) Color.White.copy(alpha = 0.85f) else ClaySecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isCurrentPlaying && isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (isCurrentPlaying) Color.White else ClayPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Reusable section title header with "See All >" link.
 */
@Composable
private fun SectionHeader(
    title: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            color = ClayLabel
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSeeAllClick
            )
        ) {
            Text(
                text = "See All",
                fontSize = 12.5.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = ClayPrimary
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ClayPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * High-Fidelity Dialog showcasing latest app updates and announcements about new features.
 */
@Composable
fun AppUpdatesDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ClaySurface)
                    .border(1.dp, ClayInset, RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume inner click
                    )
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(ClayPrimary, ClayPrimary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "What's New in Aurio",
                                color = ClayLabel,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "Latest Updates & New Features • v2.4",
                                color = ClaySecondaryLabel,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Feature Highlights List
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UpdateFeatureItem(
                        icon = Icons.Default.Group,
                        title = "Listen Together Rooms",
                        description = "Sync music in real-time with friends, share host queues, and chat live in dedicated room dialogs."
                    )
                    UpdateFeatureItem(
                        icon = Icons.Default.Reorder,
                        title = "Drag & Drop Queue Reordering",
                        description = "Long-press any song in the queue and drag it to any position to customize your playback order."
                    )
                    UpdateFeatureItem(
                        icon = Icons.Default.Radio,
                        title = "Smart Mood & Vibe Radio",
                        description = "Seamless queue auto-fills matching the exact mood, genre, and tempo of your seed tracks."
                    )
                    UpdateFeatureItem(
                        icon = Icons.Default.Speed,
                        title = "3-Song Ahead Prefetch Engine",
                        description = "Background stream pre-buffering ensures zero lag and smooth transitions between songs."
                    )
                    UpdateFeatureItem(
                        icon = Icons.Default.Headphones,
                        title = "Pure Artist Discography",
                        description = "Explore dedicated artist pages showing strictly that artist's top tracks and discography."
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClayPrimaryGradient)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Awesome, Let's Listen! 🎶",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

/**
 * Feature card item inside AppUpdatesDialog.
 */
@Composable
private fun UpdateFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ClaySurface)
            .border(1.dp, ClayInset, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ClayInset),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ClayPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ClayLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = ClaySecondaryLabel,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * User Profile Preview Dialog (dedicated for showing user profile).
 */
@Composable
fun UserProfileDialog(
    user: UserAccount?,
    recentTracksCount: Int,
    likedSongsCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ClaySurface)
                    .border(1.dp, ClayInset, RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ClayPrimary.copy(alpha = 0.12f), ClaySurface)
                            )
                        )
                        .border(2.dp, ClayPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(user!!.photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Avatar",
                            modifier = Modifier.size(76.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!user?.displayName.isNullOrBlank()) {
                        Text(
                            text = user?.displayName!!.take(1).uppercase(),
                            color = ClayLabel,
                            fontSize = 30.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = ClayPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Name & Email
                Text(
                    text = user?.displayName?.ifBlank { null } ?: "Aurio Music Listener",
                    color = ClayLabel,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = user?.email?.ifBlank { null } ?: "listener@aurio.app",
                    color = ClaySecondaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Membership Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ClayInset)
                        .border(1.dp, ClayPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ClayPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Aurio VIP Member",
                            color = ClayPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stats Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClaySurface)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$recentTracksCount",
                            color = ClayLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Recent",
                            color = ClaySecondaryLabel,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(ClayInset)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$likedSongsCount",
                            color = ClayLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Favorites",
                            color = ClaySecondaryLabel,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Full profile dashboard, custom avatars, and account settings will be available in the upcoming profile design update.",
                    color = ClaySecondaryLabel,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClayInset)
                        .border(1.dp, ClayInset, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        color = ClayLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
