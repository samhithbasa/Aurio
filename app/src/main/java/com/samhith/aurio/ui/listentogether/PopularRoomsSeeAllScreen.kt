package com.samhith.aurio.ui.listentogether

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.data.room.MockRoomData
import com.samhith.aurio.data.room.Room
import com.samhith.aurio.data.room.RoomRepository
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel

/**
 * Full-page view of all popular rooms.
 */
@Composable
fun PopularRoomsSeeAllScreen(
    onBackClick: () -> Unit,
    onRoomClick: (Room) -> Unit,
    onHomeClick: () -> Unit,
    onLibraryClick: () -> Unit = {},
    onOpenFullPlayer: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val roomRepository = remember { RoomRepository.getInstance() }
    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val progress = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) playbackPositionMs.toFloat() / durationMs.toFloat() else 0f
    }

    var popularRooms by remember { mutableStateOf<List<Room>>(emptyList()) }

    LaunchedEffect(Unit) {
        val list = roomRepository.getPublicRooms()
        popularRooms = if (list.isNotEmpty()) list else MockRoomData.popularRooms
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 140.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = ClayLabel,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Popular Rooms",
                    color = ClayLabel,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // All rooms
            popularRooms.forEach { room ->
                PopularRoomCard(
                    room = room,
                    onJoinClick = { onRoomClick(room) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom: MiniPlayer + NavBar
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
                selectedTab = HomeTab.LISTEN_TOGETHER,
                onTabSelected = { tab ->
                    when (tab) {
                        HomeTab.HOME -> onHomeClick()
                        HomeTab.LIBRARY -> onLibraryClick()
                        else -> {}
                    }
                }
            )
        }
    }
}
