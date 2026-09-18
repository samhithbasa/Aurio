package com.samhith.aurio.ui.listentogether

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.samhith.aurio.data.auth.AuthRepository
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.data.room.MockRoomData
import com.samhith.aurio.data.room.Room
import com.samhith.aurio.data.room.RoomRepository
import com.samhith.aurio.data.room.RoomSessionManager
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.components.CurvedBottomNavBar
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.components.MiniPlayer
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.ui.theme.ApplePrimaryGradient

/**
 * Main Listen Together screen matching the reference design:
 * - Header with back arrow and title
 * - Hero banner from Listen_Together.png
 * - Feature icons row (Listen with Friends, Near or Far, Real-time Playback, Share Your Vibe)
 * - Your Rooms section with empty state and Create Room CTA
 * - Popular Rooms section with room cards and Join buttons
 * - MiniPlayer + CurvedBottomNavBar at bottom
 */
@Composable
fun ListenTogetherScreen(
    onBackClick: () -> Unit,
    onRoomClick: (Room) -> Unit,
    onCreateRoom: (Room) -> Unit,
    onSeeAllPopularRooms: () -> Unit,
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

    // Load Listen_Together.png from assets
    val bannerBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("Listen_Together.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }

    val authRepository = remember { AuthRepository.getInstance(context) }
    val currentUser = remember { authRepository.getCurrentUser() }
    val roomRepository = remember { RoomRepository.getInstance() }

    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showJoinRoomDialog by remember { mutableStateOf(false) }
    val persistedUserRooms by roomRepository.userCreatedRooms.collectAsState()
    var fetchedUserRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    val userRooms = remember(persistedUserRooms, fetchedUserRooms) {
        (persistedUserRooms + fetchedUserRooms).distinctBy { it.id }
    }
    var popularRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var isLoadingRooms by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val active = RoomSessionManager.getInstance().activeRoom.value
        if (active != null) {
            onRoomClick(active)
            return@LaunchedEffect
        }
        val userId = currentUser?.supabaseId?.ifBlank { currentUser.email } ?: ""
        val publicList = roomRepository.getPublicRooms()
        val userList = if (userId.isNotBlank()) roomRepository.getUserRooms(userId) else emptyList()
        popularRooms = if (publicList.isNotEmpty()) publicList else MockRoomData.popularRooms
        fetchedUserRooms = userList
        isLoadingRooms = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 140.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ──
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
                        tint = AppleLabel,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = AppleLabel)) { append("Listen ") }
                            withStyle(SpanStyle(color = AppleBlue)) { append("Together") }
                        },
                        fontSize = 22.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Music sounds better together",
                        color = AppleSecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Placeholder for symmetry
                Spacer(modifier = Modifier.size(48.dp))
            }

            // ── Hero Banner ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                if (bannerBitmap != null) {
                    Image(
                        bitmap = bannerBitmap,
                        contentDescription = "Listen Together Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(AppleBlue.copy(alpha = 0.12f), AppleBackground, AppleBlue.copy(alpha = 0.3f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = AppleBlue.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, AppleBackground.copy(alpha = 0.8f))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Feature Icons Row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeatureIcon(
                    icon = Icons.Default.Groups,
                    label = "Listen\nwith Friends"
                )
                FeatureIcon(
                    icon = Icons.Default.Language,
                    label = "Near or Far"
                )
                FeatureIcon(
                    icon = Icons.Default.MusicNote,
                    label = "Real-time\nPlayback"
                )
                FeatureIcon(
                    icon = Icons.Default.Favorite,
                    label = "Share\nYour Vibe"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Your Rooms Section ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Rooms",
                    color = AppleLabel,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Join Room button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppleSurface)
                            .border(1.dp, AppleSeparator, RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showJoinRoomDialog = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Join Room",
                            tint = AppleLabel.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Join Room",
                            color = AppleLabel,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Create Room button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppleBlue.copy(alpha = 0.15f))
                            .border(1.dp, AppleBlue.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showCreateRoomDialog = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Room",
                            tint = AppleBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Create",
                            color = AppleBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (userRooms.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleSurface)
                        .padding(vertical = 32.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SentimentSatisfied,
                        contentDescription = null,
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You haven't created a room yet",
                        color = AppleLabel,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create a room or join with a code\nto listen with friends in real-time.",
                        color = AppleSecondaryLabel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Dual Action Buttons: Create Your Room & Join with Code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Create Your Room CTA
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(ApplePrimaryGradient)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showCreateRoomDialog = true }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = AppleOnAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Create Room",
                                color = AppleOnAccent,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Join with Code CTA
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(AppleSurface)
                                .border(1.dp, AppleBlue.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showJoinRoomDialog = true }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Join with Code",
                                color = AppleBlue,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // User's rooms horizontal list
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(userRooms) { room ->
                        UserRoomCard(room = room, onClick = { onRoomClick(room) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Popular Rooms Section ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Popular Rooms",
                    color = AppleLabel,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSeeAllPopularRooms
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See All",
                        color = AppleBlue,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Popular Room Cards (show first 4)
            popularRooms.take(4).forEach { room ->
                PopularRoomCard(
                    room = room,
                    onJoinClick = { onRoomClick(room) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
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

        // ── Create Room Dialog ──
        if (showCreateRoomDialog) {
            CreateRoomDialog(
                user = currentUser,
                onDismiss = { showCreateRoomDialog = false },
                onCreate = { room ->
                    showCreateRoomDialog = false
                    fetchedUserRooms = listOf(room) + fetchedUserRooms.filter { it.id != room.id }
                    onCreateRoom(room)
                }
            )
        }

        // ── Join Room Dialog ──
        if (showJoinRoomDialog) {
            JoinRoomDialog(
                onDismiss = { showJoinRoomDialog = false },
                onJoinRoom = { room ->
                    showJoinRoomDialog = false
                    onRoomClick(room)
                }
            )
        }
    }
}

/**
 * Feature icon with circular background and label below.
 */
@Composable
private fun FeatureIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AppleSurface)
                .border(1.dp, AppleSeparator, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AppleBlue,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = AppleLabel,
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
 * Card for a user's own room.
 */
@Composable
private fun UserRoomCard(
    room: Room,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppleSurface)
            .border(1.dp, AppleSeparator, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = AppleBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = room.name,
                color = AppleLabel,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (room.isPublic) "Public" else "Private",
                color = AppleSecondaryLabel,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * Popular room card matching the reference design:
 * - Room cover image, name, listener count, genre tags, and Join button.
 */
@Composable
fun PopularRoomCard(
    room: Room,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppleSurface)
            .border(0.5.dp, AppleSeparator, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Room Cover Image placeholder
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AppleBlue.copy(alpha = 0.12f), AppleBlue.copy(alpha = 0.12f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = AppleBlue.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Room Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.name,
                color = AppleLabel,
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = AppleSecondaryLabel,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${room.listenerCount} listening",
                    color = AppleSecondaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Tags
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                room.tags.take(3).forEach { tag ->
                    Text(
                        text = tag,
                        color = AppleLabel.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppleFill)
                            .border(0.5.dp, AppleSeparator, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Join Button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(ApplePrimaryGradient)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onJoinClick
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = AppleOnAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Join",
                color = AppleOnAccent,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
