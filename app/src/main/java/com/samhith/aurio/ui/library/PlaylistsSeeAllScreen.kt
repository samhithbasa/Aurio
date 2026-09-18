package com.samhith.aurio.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.library.PlaylistData
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.samhith.aurio.ui.dialogs.SongActionDialog
import com.samhith.aurio.ui.library.AddToPlaylistSheet
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsSeeAllScreen(
    libraryRepository: LibraryRepository,
    playerManager: AudioPlayerManager,
    onBack: () -> Unit,
    onNavigateTab: (HomeTab) -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenSpotifyImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playlists by libraryRepository.playlists.collectAsState()
    val likedSongIds by playerManager.likedSongIds.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPlaylistForDetail by remember { mutableStateOf<PlaylistData?>(null) }
    var selectedSongForAction by remember { mutableStateOf<SongItem?>(null) }
    var songForPlaylistSheet by remember { mutableStateOf<SongItem?>(null) }

    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else playlists.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(AppleSurface, CircleShape)
                        .border(1.dp, AppleSeparator, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppleLabel
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playlists",
                        color = AppleLabel,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "${playlists.size} playlists",
                        color = AppleSecondaryLabel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Add Playlist Button
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            ApplePrimaryGradient,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = AppleOnAccent
                    )
                }
            }

            // Search Bar & Import Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(AppleFill, RoundedCornerShape(12.dp))
                        .border(1.dp, AppleSeparator, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
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
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search playlists...",
                                    color = AppleSecondaryLabel,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = AppleLabel,
                                unfocusedTextColor = AppleLabel,
                                cursorColor = AppleBlue
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Spotify Import Shortcut
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1DB954).copy(alpha = 0.2f), Color(0xFF1DB954).copy(alpha = 0.1f))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, Color(0xFF1DB954).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onOpenSpotifyImport() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Spotify",
                        color = Color(0xFF1DB954),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Grid of Playlists
            if (filteredPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = AppleGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No playlists yet" else "No matching playlists",
                            color = AppleSecondaryLabel,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredPlaylists, key = { it.id }) { playlist ->
                        PlaylistGridCard(
                            playlist = playlist,
                            onClick = { selectedPlaylistForDetail = playlist },
                            onPlayAll = {
                                if (playlist.songs.isNotEmpty()) {
                                    playerManager.playQueue(playlist.songs)
                                }
                            },
                            onDelete = {
                                libraryRepository.deletePlaylist(playlist.id)
                            }
                        )
                    }
                }
            }
        }

        // Floating MiniPlayer + Bottom Navigation Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            val currentSong by playerManager.currentSong.collectAsState()
            val isPlaying by playerManager.isPlaying.collectAsState()
            val isBuffering by playerManager.isBuffering.collectAsState()
            val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
            val durationMs by playerManager.durationMs.collectAsState()
            val progress = if (durationMs > 0) (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

            if (currentSong != null) {
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
            }
            CurvedBottomNavBar(
                selectedTab = HomeTab.LIBRARY,
                onTabSelected = onNavigateTab
            )
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        var playlistDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = AppleSurface,
            title = {
                Text(
                    "New Playlist",
                    color = AppleLabel,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist Name", color = AppleSecondaryLabel) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppleLabel,
                            unfocusedTextColor = AppleLabel,
                            focusedBorderColor = AppleBlue,
                            unfocusedBorderColor = AppleSeparator,
                            cursorColor = AppleBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = playlistDesc,
                        onValueChange = { playlistDesc = it },
                        label = { Text("Description (Optional)", color = AppleSecondaryLabel) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppleLabel,
                            unfocusedTextColor = AppleLabel,
                            focusedBorderColor = AppleBlue,
                            unfocusedBorderColor = AppleSeparator,
                            cursorColor = AppleBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            libraryRepository.createPlaylist(playlistName.trim(), playlistDesc.trim())
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create", color = AppleBlue, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = AppleSecondaryLabel, fontFamily = FontFamily.SansSerif)
                }
            }
        )
    }

    // Playlist Details Modal Bottom Sheet
    selectedPlaylistForDetail?.let { playlist ->
        val currentPlaylists by libraryRepository.playlists.collectAsState()
        val livePlaylist = currentPlaylists.find { it.id == playlist.id } ?: playlist

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedPlaylistForDetail = null },
            sheetState = sheetState,
            containerColor = AppleSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppleFill)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = livePlaylist.name,
                            color = AppleLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        if (livePlaylist.description.isNotBlank()) {
                            Text(
                                text = livePlaylist.description,
                                color = AppleSecondaryLabel,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Text(
                            text = "${livePlaylist.songs.size} tracks",
                            color = AppleBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    IconButton(onClick = { selectedPlaylistForDetail = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppleSecondaryLabel
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Play All Button
                if (livePlaylist.songs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                ApplePrimaryGradient,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                playerManager.playQueue(livePlaylist.songs)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = AppleOnAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Play All (${livePlaylist.songs.size} songs)",
                                color = AppleOnAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Songs List
                if (livePlaylist.songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs in this playlist yet.\nAdd songs from any track's context menu!",
                            color = AppleSecondaryLabel,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(livePlaylist.songs, key = { it.id }) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppleFill, RoundedCornerShape(12.dp))
                                    .border(1.dp, AppleSeparator, RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = {
                                            val idx = livePlaylist.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                            playerManager.playQueue(livePlaylist.songs, idx)
                                        },
                                        onLongClick = {
                                            selectedSongForAction = song
                                        }
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleFill),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (song.thumbnailUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = AppleBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = AppleLabel,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.SansSerif,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        color = AppleSecondaryLabel,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        libraryRepository.removeSongFromPlaylist(livePlaylist.id, song.id)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = AppleBlue.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- SONG ACTION DIALOG (LONG PRESS) ---
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

@Composable
private fun PlaylistGridCard(
    playlist: PlaylistData,
    onClick: () -> Unit,
    onPlayAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppleSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppleSeparator)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Artwork / Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AppleBlue.copy(alpha = 0.35f),
                                AppleBlue.copy(alpha = 0.2f),
                                AppleSurface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (playlist.songs.isNotEmpty() && playlist.songs.first().thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.songs.first().thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Spotify badge
                if (playlist.isSpotifyImport) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xFF1DB954), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SPOTIFY",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title & Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = AppleLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.songs.size} tracks",
                        color = AppleSecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = AppleSecondaryLabel,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(AppleSurface)
                            .border(1.dp, AppleSeparator, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play All", color = AppleLabel, fontFamily = FontFamily.SansSerif) },
                            onClick = {
                                showMenu = false
                                onPlayAll()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Playlist", color = AppleBlue, fontFamily = FontFamily.SansSerif) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
