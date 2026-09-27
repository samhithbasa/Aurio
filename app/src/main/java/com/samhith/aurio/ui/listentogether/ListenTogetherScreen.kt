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
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayCard
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClayLilac
import com.samhith.aurio.ui.theme.ClayMint
import com.samhith.aurio.ui.theme.ClayPeach
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayPrimaryGradient
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.clayButton
import com.samhith.aurio.ui.theme.clayCard
import com.samhith.aurio.ui.theme.clayCircle
import com.samhith.aurio.ui.theme.clayPill

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
            .background(ClayBackground)
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clayCircle(elevation = 4.dp, backgroundColor = ClaySurface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = ClayLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = ClayLabel)) { append("Listen ") }
                            withStyle(SpanStyle(color = ClayPrimary)) { append("Together") }
                        },
                        fontSize = 22.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Music sounds better together",
                        color = ClaySecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(42.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Hero Banner ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
                    .clayCard(cornerRadius = 24.dp, elevation = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
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
                                    listOf(ClayPrimary.copy(alpha = 0.2f), ClayBackground, ClayLilac.copy(alpha = 0.35f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = ClayPrimary.copy(alpha = 0.7f),
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
                                listOf(Color.Transparent, ClayBackground.copy(alpha = 0.85f))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Feature Icons Row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeatureIcon(
                    icon = Icons.Default.Groups,
                    iconTint = ClayPrimary,
                    label = "Listen\nwith Friends"
                )
                FeatureIcon(
                    icon = Icons.Default.Language,
                    iconTint = ClayPeach,
                    label = "Near or\nFar"
                )
                FeatureIcon(
                    icon = Icons.Default.MusicNote,
                    iconTint = ClayMint,
                    label = "Real-time\nPlayback"
                )
                FeatureIcon(
                    icon = Icons.Default.Favorite,
                    iconTint = ClayLilac,
                    label = "Share\nYour Vibe"
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

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
                    color = ClayLabel,
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
                            .clayPill(isSelected = false, elevation = 3.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showJoinRoomDialog = true }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Join Room",
                            tint = ClayLabel,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Join Room",
                            color = ClayLabel,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Create Room button
                    Row(
                        modifier = Modifier
                            .clayPill(isSelected = true, elevation = 4.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showCreateRoomDialog = true }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Room",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Create",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (userRooms.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clayCard(cornerRadius = 22.dp, elevation = 5.dp, backgroundColor = ClayCard)
                        .padding(vertical = 30.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clayCircle(elevation = 4.dp, backgroundColor = ClayInset),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SentimentSatisfied,
                            contentDescription = null,
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "You haven't created a room yet",
                        color = ClayLabel,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create a room or join with a code\nto listen with friends in real-time.",
                        color = ClaySecondaryLabel,
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
                                .clayButton(cornerRadius = 24.dp, elevation = 5.dp, gradient = ClayPrimaryGradient)
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
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Create Room",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Join with Code CTA
                        Row(
                            modifier = Modifier
                                .clayButton(cornerRadius = 24.dp, elevation = 4.dp, backgroundColor = ClaySurface)
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
                                tint = ClayPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Join with Code",
                                color = ClayPrimary,
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                    color = ClayLabel,
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
                        color = ClayPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ClayPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Popular Room Cards (show first 4)
            popularRooms.take(4).forEach { room ->
                PopularRoomCard(
                    room = room,
                    onJoinClick = { onRoomClick(room) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
 * Feature icon with circular 3D clay background and label below.
 */
@Composable
private fun FeatureIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = ClayPrimary,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clayCircle(elevation = 5.dp, backgroundColor = ClaySurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = ClayLabel,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
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
            .clayCard(cornerRadius = 20.dp, elevation = 5.dp, backgroundColor = ClayCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clayCircle(elevation = 3.dp, backgroundColor = ClayInset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = ClayPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = room.name,
                color = ClayLabel,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (room.isPublic) "Public" else "Private",
                color = ClaySecondaryLabel,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * Popular room card matching the claymorphic reference design:
 * - Room cover icon with soft lilac tint, name, listener count, tags, and Join CTA button.
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
            .clayCard(cornerRadius = 20.dp, elevation = 5.dp, backgroundColor = ClayCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Room Cover Image placeholder with soft pastel lilac
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ClayLilac.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = ClayPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Room Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.name,
                color = ClayLabel,
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = ClaySecondaryLabel,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${room.listenerCount} listening",
                    color = ClaySecondaryLabel,
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
                        color = ClayLabel,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClayInset)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Join Button
        Row(
            modifier = Modifier
                .clayPill(isSelected = true, elevation = 4.dp)
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
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Join",
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
