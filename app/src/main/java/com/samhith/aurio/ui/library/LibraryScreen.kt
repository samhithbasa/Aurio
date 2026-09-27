package com.samhith.aurio.ui.library

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.auth.AuthRepository
import com.samhith.aurio.data.download.DownloadManager
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.library.PlaylistData
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.home.ArtistProfile
import com.samhith.aurio.ui.theme.*

/**
 * High-fidelity Library Screen matching the reference design.
 * Layout:
 *  - Header: "Your Library" gradient title + subtitle + Profile avatar (matching HomeScreen)
 *  - Liked Songs Card
 *  - Downloads Card (Aurio theme)
 *  - Import Playlist from Spotify banner
 *  - Playlists horizontal carousel
 *  - Import Your Own Songs banner
 *  - Followed Artists carousel
 *  - MiniPlayer + CurvedBottomNavBar
 */
@Composable
fun LibraryScreen(
    onLikedSongsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onPlaylistsSeeAllClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onListenTogetherClick: () -> Unit = {},
    onOpenFullPlayer: () -> Unit = {},
    onArtistClick: (ArtistProfile) -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository.getInstance(context) }
    val user = remember { authRepository.getCurrentUser() }
    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val libraryRepository = remember { LibraryRepository.getInstance().apply { initialize(context) } }
    val downloadManager = remember { DownloadManager.getInstance().apply { initialize(context) } }

    // Player state
    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()

    // Library state
    val likedSongs by libraryRepository.likedSongs.collectAsState()
    val playlists by libraryRepository.playlists.collectAsState()
    val followedArtists by libraryRepository.followedArtists.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()

    // Spotify & Local import dialog states
    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var showImportOwnSongDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val progress = if (durationMs > 0) (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "library_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_pulse"
    )

    Box(modifier = modifier.fillMaxSize().background(ClayBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(bottom = 160.dp)
        ) {
            // ═══ Header ═══════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = ClayLabel)) {
                                append("Your ")
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = ClayPrimary)) {
                                append("Library")
                            }
                        },
                        fontSize = 28.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "All your music in one place",
                        fontSize = 13.sp,
                        color = ClaySecondaryLabel,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Profile Icon
                // Profile Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clayCircle(elevation = 5.dp)
                        .clickable { onProfileClick() },
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

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ Liked Songs & Downloads Cards ════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Liked Songs Card (Puffy Pastel Peach Clay)
                LibraryFeatureCard(
                    title = "Liked Songs",
                    count = likedSongs.size,
                    icon = Icons.Default.Favorite,
                    iconColor = ClayPeachDark,
                    cardGradient = ClayPeachGradient,
                    badgeGradient = ClayCardGradient,
                    badgeElevation = 5.dp,
                    onClick = onLikedSongsClick,
                    modifier = Modifier.weight(1f)
                )

                // Downloads Card (Puffy Pastel Lilac Clay)
                LibraryFeatureCard(
                    title = "Downloads",
                    count = downloadedSongs.size,
                    icon = Icons.Default.CloudDownload,
                    iconColor = ClayLilacDark,
                    cardGradient = ClayLilacGradient,
                    badgeGradient = ClayCardGradient,
                    badgeElevation = 5.dp,
                    onClick = onDownloadsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══ Import Playlist from Spotify ═════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clayCard(cornerRadius = 20.dp, elevation = 6.dp, gradient = ClayMintGradient)
                    .clickable { showSpotifyImportDialog = true }
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clayCircle(elevation = 4.dp, gradient = ClayCardGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Spotify",
                            tint = ClayMintDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Import Playlist from Spotify",
                            color = ClayLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            "Bring your playlists to Aurio",
                            color = ClayLabel.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clayButton(gradient = ClayCardGradient, cornerRadius = 20.dp, elevation = 4.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Import",
                            color = ClayMintDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ═══ Playlists Section ════════════════════════════════
            SectionHeader(
                title = "Playlists",
                count = playlists.size,
                onSeeAllClick = onPlaylistsSeeAllClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(playlists) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = {
                            if (playlist.songs.isNotEmpty()) {
                                playerManager.playQueue(playlist.songs, 0, playlist.name)
                            } else {
                                onPlaylistsSeeAllClick()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ═══ Import Your Own Songs ════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clayCard(cornerRadius = 20.dp, elevation = 6.dp)
                    .clickable { showImportOwnSongDialog = true }
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clayCircle(elevation = 4.dp, gradient = ClayPrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Import",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Import Your Own Songs",
                            color = ClayLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            "Add music files from your device",
                            color = ClaySecondaryLabel,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clayButton(gradient = ClayPrimaryGradient, cornerRadius = 20.dp, elevation = 4.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Import",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ═══ Followed Artists Section ══════════════════════════
            if (followedArtists.isNotEmpty()) {
                SectionHeader(
                    title = "Followed Artists",
                    count = followedArtists.size,
                    onSeeAllClick = {}
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(followedArtists) { artist ->
                        FollowedArtistChip(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ═══ Bottom: MiniPlayer + NavBar ══════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong
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
                selectedTab = HomeTab.LIBRARY,
                onTabSelected = {
                    when (it) {
                        HomeTab.HOME -> onHomeClick()
                        HomeTab.LISTEN_TOGETHER -> onListenTogetherClick()
                        HomeTab.LIBRARY -> { /* Already on Library */ }
                        HomeTab.PROFILE -> onProfileClick()
                    }
                }
            )
        }

        // ═══ Spotify Import Dialog ════════════════════════════════
        if (showSpotifyImportDialog) {
            SpotifyImportDialog(
                onDismiss = { showSpotifyImportDialog = false },
                onImport = { name, url ->
                    libraryRepository.importSpotifyPlaylist(name, url) {
                        showSpotifyImportDialog = false
                    }
                }
            )
        }

        // ═══ Import Own Song Dialog ═══════════════════════════════
        if (showImportOwnSongDialog) {
            ImportOwnSongDialog(
                onDismiss = { showImportOwnSongDialog = false }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ─── Reusable Components ──────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LibraryFeatureCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    cardGradient: Brush = ClayCardGradient,
    badgeGradient: Brush = ClayCardGradient,
    badgeElevation: androidx.compose.ui.unit.Dp = 4.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(118.dp)
            .clayCard(cornerRadius = 20.dp, elevation = 6.dp, gradient = cardGradient)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clayCircle(elevation = badgeElevation, gradient = badgeGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        title,
                        color = ClayLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        "$count songs",
                        color = ClayLabel.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open",
                    tint = ClayLabel.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int = 0,
    onSeeAllClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = ClayLabel,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clayPill(isSelected = true)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        "$count",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSeeAllClick
            )
        ) {
            Text(
                "See All",
                color = ClayPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "See All",
                tint = ClayPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistData,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(130.dp)
            .clayCard(cornerRadius = 18.dp, elevation = 5.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ClayInset),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(playlist.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    if (playlist.isSpotifyImport) Icons.AutoMirrored.Filled.PlaylistAdd else Icons.Default.LibraryMusic,
                    contentDescription = playlist.name,
                    tint = ClayPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            playlist.name,
            color = ClayLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            "${playlist.songs.size} tracks",
            color = ClaySecondaryLabel,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun FollowedArtistChip(
    artist: ArtistProfile,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clayCircle(elevation = 5.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            artist.name,
            color = ClayLabel,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
