package com.samhith.aurio.ui.artists

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.home.ArtistProfile
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import kotlinx.coroutines.launch
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

data class ArtistDetailItem(
    val name: String,
    val imageUrl: String,
    val genre: String,
    val monthlyListeners: String
)

enum class ArtistGenreCategory(val label: String) {
    ALL("All"),
    POP("Pop"),
    BOLLYWOOD("Bollywood"),
    HIPHOP("Hip-Hop"),
    RNB("R&B"),
    ROCK("Rock"),
    INDIE("Indie")
}

/**
 * High-fidelity "Popular Artists" See All screen matching the user's reference design.
 * Features Home_Screen_Banner.png header artwork, in-list search, category filter pills,
 * ranked artist cards with verified badges and monthly listeners metrics,
 * and bottom MiniPlayer + CurvedBottomNavBar.
 */
@Composable
fun PopularArtistsSeeAllScreen(
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
    val libraryRepository = remember { LibraryRepository.getInstance().apply { initialize(context) } }

    val followedArtistNames by libraryRepository.followedArtistNames.collectAsState()

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val recentTracks by musicRepository.recentlyPlayed.collectAsState()

    val progress = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) playbackPositionMs.toFloat() / durationMs.toFloat() else 0f
    }

    var selectedCategory by remember { mutableStateOf(ArtistGenreCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    // Curated Global & Regional Artists with Verified High-Res CDN Photos
    val allArtists = remember {
        listOf(
            ArtistDetailItem("The Weeknd", "https://c.saavncdn.com/artists/The_Weeknd_500x500.jpg", "Pop • R&B", "108.2M"),
            ArtistDetailItem("Taylor Swift", "https://c.saavncdn.com/artists/Taylor_Swift_500x500.jpg", "Pop • Country", "102.5M"),
            ArtistDetailItem("Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_002_20230323062147_500x500.jpg", "Bollywood • Soul", "89.6M"),
            ArtistDetailItem("Bruno Mars", "https://c.saavncdn.com/artists/Bruno_Mars_500x500.jpg", "Pop • Funk", "85.1M"),
            ArtistDetailItem("Drake", "https://c.saavncdn.com/artists/Drake_500x500.jpg", "Hip-Hop • Rap", "84.3M"),
            ArtistDetailItem("Billie Eilish", "https://c.saavncdn.com/707/HIT-ME-HARD-AND-SOFT-English-2024-20240517043818-500x500.jpg", "Pop • Indie", "81.7M"),
            ArtistDetailItem("Ed Sheeran", "https://c.saavncdn.com/artists/Ed_Sheeran_500x500.jpg", "Pop • Acoustic", "79.4M"),
            ArtistDetailItem("Diljit Dosanjh", "https://c.saavncdn.com/artists/Diljit_Dosanjh_500x500.jpg", "Bollywood • Punjabi", "68.2M"),
            ArtistDetailItem("Justin Bieber", "https://c.saavncdn.com/artists/Justin_Bieber_500x500.jpg", "Pop • R&B", "67.5M"),
            ArtistDetailItem("Selena Gomez", "https://c.saavncdn.com/artists/Selena_Gomez_-_The_Scene_20200218135350_500x500.jpg", "Pop • Dance", "64.9M"),
            ArtistDetailItem("Atif Aslam", "https://c.saavncdn.com/artists/Atif_Aslam_500x500.jpg", "Bollywood • Pop", "61.3M"),
            ArtistDetailItem("Badshah", "https://c.saavncdn.com/artists/Badshah_500x500.jpg", "Hip-Hop • Rap", "58.7M"),
            ArtistDetailItem("Sid Sriram", "https://c.saavncdn.com/artists/Sid_Sriram_500x500.jpg", "Bollywood • Indie", "54.2M"),
            ArtistDetailItem("Armaan Malik", "https://c.saavncdn.com/artists/Armaan_Malik_500x500.jpg", "Bollywood • Pop", "51.8M"),
            ArtistDetailItem("Shawn Mendes", "https://c.saavncdn.com/artists/Shawn_Mendes_500x500.jpg", "Pop • Acoustic", "49.6M"),
            ArtistDetailItem("Eminem", "https://c.saavncdn.com/artists/Eminem_500x500.jpg", "Hip-Hop • Rap", "48.3M"),
            ArtistDetailItem("Lady Gaga", "https://c.saavncdn.com/060/Die-With-A-Smile-English-2024-20240816103634-500x500.jpg", "Pop • Dance", "47.9M"),
            ArtistDetailItem("Dua Lipa", "https://c.saavncdn.com/artists/Dua_Lipa_500x500.jpg", "Pop • Disco", "46.5M"),
            ArtistDetailItem("Post Malone", "https://c.saavncdn.com/artists/Post_Malone_500x500.jpg", "Hip-Hop • Rock", "45.2M"),
            ArtistDetailItem("Shreya Ghoshal", "https://c.saavncdn.com/artists/Shreya_Ghoshal_500x500.jpg", "Bollywood • Classical", "43.8M"),
            ArtistDetailItem("Kendrick Lamar", "https://c.saavncdn.com/artists/Kendrick_Lamar_500x500.jpg", "Hip-Hop • Rap", "42.1M"),
            ArtistDetailItem("Olivia Rodrigo", "https://c.saavncdn.com/artists/Olivia_Rodrigo_500x500.jpg", "Pop • Rock", "40.5M")
        )
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

    // Filter artists based on category & search query
    val displayArtists = remember(allArtists, searchQuery, selectedCategory) {
        allArtists.filter { artist ->
            val matchesCategory = when (selectedCategory) {
                ArtistGenreCategory.ALL -> true
                ArtistGenreCategory.POP -> artist.genre.contains("Pop", true)
                ArtistGenreCategory.BOLLYWOOD -> artist.genre.contains("Bollywood", true) || artist.genre.contains("Punjabi", true)
                ArtistGenreCategory.HIPHOP -> artist.genre.contains("Hip-Hop", true) || artist.genre.contains("Rap", true)
                ArtistGenreCategory.RNB -> artist.genre.contains("R&B", true) || artist.genre.contains("Soul", true)
                ArtistGenreCategory.ROCK -> artist.genre.contains("Rock", true)
                ArtistGenreCategory.INDIE -> artist.genre.contains("Indie", true) || artist.genre.contains("Acoustic", true)
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                artist.name.contains(searchQuery, ignoreCase = true) || artist.genre.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }
    }

    fun playArtistTopTrack(artistName: String) {
        scope.launch {
            try {
                val tracks = musicRepository.search("$artistName popular hits")
                if (tracks.isNotEmpty()) {
                    playerManager.playSong(tracks.first())
                }
            } catch (_: Exception) {}
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
                PopularArtistsHeaderSection(
                    bannerBitmap = bannerBitmap,
                    onBackClick = onBackClick
                )
            }

            // 2. Search Bar
            item {
                Spacer(modifier = Modifier.height(4.dp))
                PopularArtistsSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }

            // 3. Category Filter Pills
            item {
                Spacer(modifier = Modifier.height(4.dp))
                PopularArtistsCategoryPills(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 4. Ranked Artist Items List
            if (displayArtists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No artists found for \"$searchQuery\"" else "No artists in this category",
                            color = AppleSecondaryLabel,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            } else {
                itemsIndexed(displayArtists, key = { _, artist -> artist.name }) { index, artist ->
                    val rank = index + 1
                    val isFollowing = followedArtistNames.contains(artist.name)
                    PopularArtistRankedCard(
                        rank = rank,
                        artist = artist,
                        isFollowing = isFollowing,
                        onToggleFollow = {
                            libraryRepository.toggleFollowArtist(
                                ArtistProfile(name = artist.name, imageUrl = artist.imageUrl)
                            )
                        },
                        onArtistClick = { onArtistClick(artist) },
                        onPlayClick = { playArtistTopTrack(artist.name) }
                    )
                }
            }
        }

        // 5. Fixed Bottom Overlay (Mini Player on Top of Curved Bottom Nav Bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong ?: recentTracks.firstOrNull()
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
    }
}

/**
 * Top Header section with Back Button, 3D character graphic from Home_Screen_Banner.png,
 * and bold "Popular Artists" typography overlaid directly on the artwork.
 */
@Composable
private fun PopularArtistsHeaderSection(
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
                contentDescription = "Popular Artists Header",
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
                            append("Popular\n")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = AppleBlue,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        ) {
                            append("Artists")
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
                    text = "The world's most streamed\nmusic creators",
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
 * Search bar for filtering artists.
 */
@Composable
private fun PopularArtistsSearchBar(
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
                contentDescription = "Search artists",
                tint = AppleSecondaryLabel,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search artists, genres, creators...",
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
 * Category Filter Pills for Artists (All, Pop, Bollywood, Hip-Hop, R&B, Rock, Indie).
 */
@Composable
private fun PopularArtistsCategoryPills(
    selectedCategory: ArtistGenreCategory,
    onCategorySelected: (ArtistGenreCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ArtistGenreCategory.values()) { category ->
            val isSelected = category == selectedCategory
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
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    color = if (isSelected) AppleOnAccent else AppleSecondaryLabel,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Individual Ranked Artist Card matching the reference design aesthetic.
 * Features rank number, high-res circular photo, artist name, verified icon,
 * monthly listeners count, genre, circular play button, and options icon.
 */
@Composable
private fun PopularArtistRankedCard(
    rank: Int,
    artist: ArtistDetailItem,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onArtistClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppleSurface)
            .border(1.dp, AppleSeparator, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onArtistClick
            )
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Large Bold Rank Number
            Text(
                text = "$rank",
                color = if (rank <= 3) AppleBlue else AppleBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(28.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 2. Artist Circular Avatar
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(AppleSurface)
                    .border(1.2.dp, AppleSeparator, CircleShape),
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
                        tint = AppleBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 3. Artist Details (Name + Verified Badge, Genre, Monthly Listeners)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = artist.name,
                        color = AppleLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified Artist",
                        tint = AppleBlue,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = artist.genre,
                    color = AppleSecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Monthly Listeners Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${artist.monthlyListeners} monthly listeners",
                        color = AppleSecondaryLabel,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // 4. Follow Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isFollowing) {
                            Modifier
                                .background(AppleFill)
                                .border(1.dp, AppleBlue.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        } else {
                            Modifier.background(ApplePrimaryGradient)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleFollow
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFollowing) "Following" else "+ Follow",
                    color = if (isFollowing) AppleBlue else AppleOnAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 5. Circular Play Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AppleBlue)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Artist Hits",
                    tint = AppleOnAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
