package com.samhith.aurio.ui.listentogether

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.auth.AuthRepository
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.data.room.MockRoomData
import com.samhith.aurio.data.room.Room
import com.samhith.aurio.data.room.RoomMessage
import com.samhith.aurio.data.room.RoomParticipant
import com.samhith.aurio.data.room.RoomSessionManager
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import kotlin.math.roundToInt
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayMintDark
import com.samhith.aurio.ui.theme.ClayPrimaryGradient

/**
 * Room Screen — the inside-room experience matching the reference design:
 * - Header: back arrow, settings gear, more menu
 * - Room banner image from Listen_Together.png
 * - Room name, type, listener count, tags, tagline
 * - Action buttons: Invite Friends, Share Room, Listeners, Leave Room
 * - Now Playing Together card
 * - In the Room participants row
 * - Chat panel with messages
 * - Draggable floating chat icon (snaps to edges)
 * - Bottom mini player for the room's currently playing song
 */
@Composable
fun RoomScreen(
    room: Room,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onLibraryClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onOpenFullPlayer: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val progress = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) playbackPositionMs.toFloat() / durationMs.toFloat() else 0f
    }

    val authRepository = remember { AuthRepository.getInstance(context) }
    val currentUser = remember { authRepository.getCurrentUser() }
    val sessionManager = remember { RoomSessionManager.getInstance() }

    LaunchedEffect(room.id) {
        sessionManager.enterRoom(room, currentUser, playerManager)
    }

    val liveRoom by sessionManager.activeRoom.collectAsState()
    val currentRoom = liveRoom ?: room
    val participants by sessionManager.participants.collectAsState()
    val chatMessages by sessionManager.messages.collectAsState()
    val isHost by sessionManager.isHost.collectAsState()
    val pendingRequests by sessionManager.pendingRequests.collectAsState()
    val requestStatuses by sessionManager.requestStatuses.collectAsState()
    val queue by playerManager.queue.collectAsState()
    val currentIndex by playerManager.currentIndex.collectAsState()
    var showChatDialog by remember { mutableStateOf(false) }

    // Load banner
    val bannerBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("Listen_Together.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }

    // Draggable chat icon state
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var chatIconOffsetX by remember { mutableFloatStateOf(screenWidth - with(density) { 76.dp.toPx() }) }
    var chatIconOffsetY by remember { mutableFloatStateOf(screenHeight * 0.55f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 160.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                Row {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = ClayLabel.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { /* More options */ }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = ClayLabel.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── Room Banner ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bannerBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        Image(
                            bitmap = bannerBitmap,
                            contentDescription = "Room Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(ClayPrimary.copy(alpha = 0.12f), ClayBackground)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = ClayPrimary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // ── Room Info ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentRoom.name,
                    color = ClayLabel,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${if (currentRoom.isPublic) "Public Room" else "Private Room"} · Code: ${currentRoom.code} · ${currentRoom.listenerCount} Listening",
                    color = ClaySecondaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    currentRoom.tags.forEach { tag ->
                        Text(
                            text = tag,
                            color = ClayLabel.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, ClayInset, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    // + button for adding tags
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, ClayInset, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Tag",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${currentRoom.tagline} ❤\uFE0F",
                    color = ClayPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Action Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RoomActionButton(
                    icon = Icons.Default.PersonAdd,
                    label = "Invite\nFriends",
                    onClick = { /* TODO: invite */ }
                )
                RoomActionButton(
                    icon = Icons.Default.Share,
                    label = "Share\nRoom",
                    onClick = { /* TODO: share */ }
                )
                RoomActionButton(
                    icon = Icons.Default.Groups,
                    label = "Listeners\n(${currentRoom.listenerCount})",
                    onClick = { /* TODO: show listeners */ }
                )
                RoomActionButton(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = if (isHost) "End\nRoom" else "Leave\nRoom",
                    onClick = {
                        sessionManager.leaveRoom()
                        onBackClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Now Playing Together ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Now Playing Together",
                    color = ClayLabel,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ClaySurface)
                        .border(0.5.dp, ClayInset, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Song cover
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(ClayPrimary.copy(alpha = 0.12f), ClayPrimary.copy(alpha = 0.12f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = ClayPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong?.title ?: "Malne Royaan",
                            color = ClayLabel,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentSong?.artist ?: "Tanveer Evan",
                            color = ClaySecondaryLabel,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Like",
                        tint = ClayPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* toggle like */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── In the Room ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "In the Room",
                    color = ClayLabel,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* See all */ },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See All",
                        color = ClayPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ClayPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Participants horizontal row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(participants) { participant ->
                    ParticipantAvatar(participant = participant)
                }
                item {
                    // Add button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, ClayPrimary.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = ClayPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add",
                            color = ClaySecondaryLabel,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Spacer(modifier = Modifier.height(24.dp))

            // ── Room Queue (Host Queue) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Room Queue",
                        color = ClayLabel,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ClaySurface)
                            .border(0.8.dp, ClayInset, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isHost) "Host Queue (${queue.size})" else "Live Queue (${queue.size})",
                            color = ClayPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ClaySurface)
                        .border(1.dp, ClayInset, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty. Play or request songs to listen together!",
                        color = ClaySecondaryLabel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    queue.forEachIndexed { idx, song ->
                        val isCurrent = idx == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrent) ClaySurface else ClaySurface)
                                .border(
                                    1.dp,
                                    if (isCurrent) ClayPrimary.copy(alpha = 0.6f) else ClaySurface,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (isHost) {
                                        playerManager.jumpToQueueIndex(idx)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Track Number / Playing indicator
                            Box(
                                modifier = Modifier.width(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCurrent && isPlaying) {
                                    Text(
                                        text = "▶",
                                        color = ClayPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "${idx + 1}",
                                        color = if (isCurrent) ClayPrimary else ClaySecondaryLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClaySurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (song.thumbnailUrl.isNotBlank()) {
                                    coil3.compose.AsyncImage(
                                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
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
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Title & Artist
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = if (isCurrent) ClayPrimary else ClayLabel,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.artist.ifBlank { "Aurio Music" },
                                    color = ClaySecondaryLabel,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Bottom: MiniPlayer + NavBar ──
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
                        HomeTab.PROFILE -> onProfileClick()
                        else -> {}
                    }
                }
            )
        }

        // ── Draggable Floating Chat Icon ──
        Box(
            modifier = Modifier
                .offset { IntOffset(chatIconOffsetX.roundToInt(), chatIconOffsetY.roundToInt()) }
                .size(56.dp)
                .aurioGlow(
                    color = ClayPrimary,
                    alpha = 0.5f,
                    blurRadius = 14.dp,
                    offsetY = 2.dp
                )
                .clip(CircleShape)
                .background(ClayPrimaryGradient)
                .border(2.dp, ClayPrimary.copy(alpha = 0.6f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Snap to nearest edge
                            val iconSize = 56.dp.toPx()
                            val midX = screenWidth / 2
                            chatIconOffsetX = if (chatIconOffsetX + iconSize / 2 < midX) {
                                12.dp.toPx() // snap left
                            } else {
                                screenWidth - iconSize - 12.dp.toPx() // snap right
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val iconSize = 56.dp.toPx()
                        chatIconOffsetX = (chatIconOffsetX + dragAmount.x).coerceIn(
                            0f, screenWidth - iconSize
                        )
                        chatIconOffsetY = (chatIconOffsetY + dragAmount.y).coerceIn(
                            0f, screenHeight - iconSize - 100.dp.toPx()
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showChatDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Chat",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // ── Room Chat Dialog ──
        if (showChatDialog) {
            RoomChatDialog(
                roomName = currentRoom.name,
                messages = chatMessages,
                currentUserId = currentUser?.supabaseId ?: currentUser?.email ?: "local_user",
                isHost = isHost,
                requestStatuses = requestStatuses,
                onAcceptRequest = { request -> sessionManager.respondToSongRequest(request, accept = true) },
                onDeclineRequest = { request -> sessionManager.respondToSongRequest(request, accept = false) },
                onSendMessage = { sessionManager.sendMessage(it) },
                onDismiss = { showChatDialog = false }
            )
        }

        // ── Host Song Request Dialog ──
        if (isHost && pendingRequests.isNotEmpty()) {
            val currentRequest = pendingRequests.first()
            SongRequestDialog(
                request = currentRequest,
                onAccept = { sessionManager.respondToSongRequest(currentRequest, accept = true) },
                onDecline = { sessionManager.respondToSongRequest(currentRequest, accept = false) },
                onDismiss = { sessionManager.respondToSongRequest(currentRequest, accept = false) }
            )
        }
    }
}

/**
 * Circular action button used in the room (Invite Friends, Share Room, etc.).
 */
@Composable
private fun RoomActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ClaySurface)
                .border(1.dp, ClayInset, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ClayPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = ClayLabel,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2
        )
    }
}

/**
 * Participant avatar with online indicator, name, and Google avatar image support.
 */
@Composable
private fun ParticipantAvatar(participant: RoomParticipant) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(ClayPrimary.copy(alpha = 0.12f), ClayPrimary.copy(alpha = 0.12f))
                        )
                    )
                    .then(
                        if (participant.isHost) {
                            Modifier.border(2.dp, ClayPrimary, CircleShape)
                        } else {
                            Modifier.border(1.dp, ClayInset, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (participant.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(participant.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = participant.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = participant.name.firstOrNull()?.toString() ?: "?",
                        color = ClayLabel,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Online indicator
            if (participant.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(ClayMintDark)
                        .border(2.dp, ClayBackground, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
            // Crown for host
            if (participant.isHost) {
                Text(
                    text = "👑",
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = participant.name,
            color = ClayLabel,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Chat message bubble matching the reference design with Google avatar support.
 */
@Composable
private fun ChatMessageBubble(message: RoomMessage) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(ClayPrimary.copy(alpha = 0.12f), ClayPrimary.copy(alpha = 0.12f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (message.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(message.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = message.senderName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = message.senderName.firstOrNull()?.toString() ?: "?",
                    color = ClayLabel,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.senderName,
                    color = ClayPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                if (message.timestamp.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.timestamp,
                        color = ClaySecondaryLabel.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            Text(
                text = message.text,
                color = ClayLabel.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 18.sp
            )
        }
    }
}
