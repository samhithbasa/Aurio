package com.samhith.aurio.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.ai.AurioWakeWordManager
import com.samhith.aurio.data.auth.AuthRepository
import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.data.download.DownloadManager
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.player.AudioPlayerManager
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

@Composable
fun ProfileScreen(
    user: UserAccount?,
    onUserUpdated: (UserAccount) -> Unit = {},
    onLikedSongsClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    onFollowingArtistsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onListenTogetherClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onOpenFullPlayer: () -> Unit = {},
    onSignOutClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository.getInstance(context) }
    val libraryRepository = remember { LibraryRepository.getInstance() }
    val downloadManager = remember { DownloadManager.getInstance() }
    val playerManager = remember { AudioPlayerManager.getInstance(context) }
    val wakeWordManager = remember { AurioWakeWordManager.getInstance(context) }
    val assistantName by wakeWordManager.customAssistantName.collectAsState()
    var isHandsFreeEnabled by remember { mutableStateOf(wakeWordManager.isHandsFreeHotwordEnabled()) }

    // Live Data & Player States
    val likedSongs by libraryRepository.likedSongs.collectAsState()
    val playlists by libraryRepository.playlists.collectAsState()
    val followedArtists by libraryRepository.followedArtists.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val playbackPositionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val progress = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) playbackPositionMs.toFloat() / durationMs.toFloat() else 0f
    }

    var currentUser by remember(user) { mutableStateOf(user ?: authRepository.getCurrentUser()) }

    // Dialog States
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAccountInfoDialog by remember { mutableStateOf(false) }
    var showPrivacySocialDialog by remember { mutableStateOf(false) }
    var showAppFeaturesDialog by remember { mutableStateOf(false) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showHelpSupportDialog by remember { mutableStateOf(false) }
    var showTermsPrivacyDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // Image Picker Launcher for Immediate Avatar Replacement
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val updated = authRepository.updateUserAvatar(uri.toString())
            currentUser = updated
            onUserUpdated(updated)
            Toast.makeText(context, "Profile picture updated! 📸", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // ─── 1. TOP HEADER ──────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "My ",
                                color = ClayLabel,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "Profile",
                                color = ClayPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Music tells my story",
                            color = ClaySecondaryLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Top-Right Settings Gear Icon with 3D Clay Circle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clayCircle(elevation = 4.dp, backgroundColor = ClaySurface)
                            .clickable { showAppSettingsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = ClayPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ─── 2. AVATAR & USER PROFILE INFO ──────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Avatar with Camera Replace Button
                    Box(
                        modifier = Modifier.size(112.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clayCircle(elevation = 6.dp, backgroundColor = ClaySurface)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val photo = currentUser?.photoUrl
                            if (!photo.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(photo)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.size(100.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initial = currentUser?.displayName?.take(1)?.ifBlank { "A" }?.uppercase() ?: "A"
                                Text(
                                    text = initial,
                                    color = ClayLabel,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        // Camera Button to Replace Image (Clay Circle)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .align(Alignment.BottomEnd)
                                .clayCircle(elevation = 4.dp, gradient = ClayPrimaryGradient)
                                .clickable { photoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Replace Image",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Username with Edit Pencil Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showEditProfileDialog = true }
                    ) {
                        Text(
                            text = currentUser?.displayName?.ifBlank { "Aurio Listener" } ?: "Aurio Listener",
                            color = ClayLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = ClayPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Username Handle
                    val handle = currentUser?.username?.ifBlank {
                        "@${currentUser?.email?.substringBefore("@") ?: "auriolistener"}"
                    } ?: "@auriolistener"
                    Text(
                        text = if (handle.startsWith("@")) handle else "@$handle",
                        color = ClayPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bio / Description
                    Text(
                        text = currentUser?.bio ?: "Good Music • Better Moods • Always 🎧",
                        color = ClaySecondaryLabel,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            // ─── 3. STATS SECTION (4 CARDS: LIKED, PLAYLISTS, FOLLOWING, DOWNLOADED) ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clayCard(cornerRadius = 24.dp, elevation = 6.dp, backgroundColor = ClayCard)
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stat 1: Liked Songs
                        StatItem(
                            count = likedSongs.size.toString(),
                            label = "Liked Songs",
                            icon = Icons.Default.Favorite,
                            iconTint = ClayPeach,
                            onClick = onLikedSongsClick
                        )

                        StatDivider()

                        // Stat 2: Playlists
                        StatItem(
                            count = playlists.size.toString(),
                            label = "Playlists",
                            icon = Icons.Default.QueueMusic,
                            iconTint = ClayLilac,
                            onClick = onPlaylistsClick
                        )

                        StatDivider()

                        // Stat 3: Following (Artists)
                        StatItem(
                            count = followedArtists.size.toString(),
                            label = "Following",
                            icon = Icons.Default.Person,
                            iconTint = ClayPrimary,
                            onClick = onFollowingArtistsClick
                        )

                        StatDivider()

                        // Stat 4: Downloaded (offline songs)
                        StatItem(
                            count = downloadedSongs.size.toString(),
                            label = "Downloaded",
                            icon = Icons.Default.CloudDownload,
                            iconTint = ClayMint,
                            onClick = onDownloadsClick
                        )
                    }
                }
            }

            // ─── 4. ACCOUNT SECTION ─────────────────────────────────────
            item {
                SectionTitle(title = "Account")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clayCard(cornerRadius = 22.dp, elevation = 5.dp, backgroundColor = ClayCard)
                ) {
                    ProfileMenuRow(
                        icon = Icons.Default.Person,
                        title = "Edit Profile",
                        subtitle = "Change your photo, username, bio and more",
                        onClick = { showEditProfileDialog = true }
                    )

                    MenuDivider()

                    ProfileMenuRow(
                        icon = Icons.Default.Info,
                        title = "Account Information",
                        subtitle = "Name, email, phone and other details",
                        onClick = { showAccountInfoDialog = true }
                    )

                    MenuDivider()

                    ProfileMenuRow(
                        icon = Icons.Default.Shield,
                        title = "Privacy & Social",
                        subtitle = "Manage your privacy and visibility",
                        onClick = { showPrivacySocialDialog = true }
                    )

                    MenuDivider()

                    ProfileMenuRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications & Features",
                        subtitle = "Music updates, new features vs other music apps",
                        onClick = { showAppFeaturesDialog = true }
                    )

                    MenuDivider()

                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        title = "App Settings",
                        subtitle = "Playback, downloads, language and more",
                        onClick = { showAppSettingsDialog = true }
                    )

                    MenuDivider()

                    ProfileToggleRow(
                        icon = Icons.Default.Mic,
                        title = "Hands-free \"Hey $assistantName\"",
                        subtitle = if (isHandsFreeEnabled) {
                            "On - keeps the microphone open to hear the wake word"
                        } else {
                            "Off - tap the assistant bubble to talk"
                        },
                        checked = isHandsFreeEnabled,
                        onCheckedChange = { enabled ->
                            isHandsFreeEnabled = enabled
                            wakeWordManager.setHandsFreeHotword(enabled)
                        }
                    )
                }
            }

            // ─── 5. SUPPORT SECTION ─────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle(title = "Support")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clayCard(cornerRadius = 22.dp, elevation = 5.dp, backgroundColor = ClayCard)
                ) {
                    ProfileMenuRow(
                        icon = Icons.Default.HelpOutline,
                        title = "Help & Support",
                        subtitle = "Get help, contact abhisamhith07@gmail.com",
                        onClick = { showHelpSupportDialog = true }
                    )

                    MenuDivider()

                    ProfileMenuRow(
                        icon = Icons.Default.Security,
                        title = "Terms & Privacy",
                        subtitle = "Terms of Service, Privacy Policy",
                        onClick = { showTermsPrivacyDialog = true }
                    )
                }
            }

            // ─── 6. LOG OUT BUTTON ──────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(54.dp)
                        .clayButton(cornerRadius = 20.dp, elevation = 4.dp, backgroundColor = ClaySurface)
                        .clickable { showLogoutConfirmDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Log Out",
                            tint = ClayPeach,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Log Out",
                            color = ClayPeach,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ─── 7. BOTTOM MINI PLAYER & CURVED NAVIGATION BAR ──────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val activeSong = currentSong ?: likedSongs.firstOrNull() ?: downloadedSongs.firstOrNull()
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
                selectedTab = HomeTab.PROFILE,
                onTabSelected = { tab ->
                    when (tab) {
                        HomeTab.HOME -> onHomeClick()
                        HomeTab.LISTEN_TOGETHER -> onListenTogetherClick()
                        HomeTab.LIBRARY -> onLibraryClick()
                        HomeTab.PROFILE -> { /* Already on profile */ }
                    }
                }
            )
        }

        // ─── SUB-DIALOGS ─────────────────────────────────────────────
        if (showEditProfileDialog) {
            EditProfileDialog(
                user = currentUser,
                onDismiss = { showEditProfileDialog = false },
                onProfileUpdated = { updated ->
                    currentUser = updated
                    onUserUpdated(updated)
                }
            )
        }

        if (showAccountInfoDialog) {
            AccountInfoDialog(
                user = currentUser,
                downloadsCount = downloadedSongs.size,
                onDismiss = { showAccountInfoDialog = false }
            )
        }

        if (showPrivacySocialDialog) {
            PrivacySocialDialog(
                onDismiss = { showPrivacySocialDialog = false }
            )
        }

        if (showAppFeaturesDialog) {
            AppFeaturesDialog(
                onDismiss = { showAppFeaturesDialog = false }
            )
        }

        if (showAppSettingsDialog) {
            AppSettingsDialog(
                onDismiss = { showAppSettingsDialog = false }
            )
        }

        if (showHelpSupportDialog) {
            HelpSupportDialog(
                onDismiss = { showHelpSupportDialog = false }
            )
        }

        if (showTermsPrivacyDialog) {
            TermsPrivacyDialog(
                onDismiss = { showTermsPrivacyDialog = false }
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmDialog = false },
                containerColor = ClayCard,
                title = {
                    Text(
                        text = "Log Out of Aurio?",
                        color = ClayLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to log out? Your downloaded tracks and liked songs will remain securely saved on this device.",
                        color = ClaySecondaryLabel,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmDialog = false
                            authRepository.logout()
                            onSignOutClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClayPeach)
                    ) {
                        Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmDialog = false }) {
                        Text("Cancel", color = ClaySecondaryLabel)
                    }
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPER UI COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = ClayLabel,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun StatItem(
    count: String,
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = count,
            color = ClayLabel,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = ClaySecondaryLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(ClayInset)
    )
}

/**
 * Same shape as [ProfileMenuRow], but the trailing chevron is a switch. Used for settings the user
 * flips in place - like hands-free listening, where the subtitle spells out the privacy trade-off.
 */
@Composable
private fun ProfileToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clayCircle(elevation = 2.dp, backgroundColor = ClayInset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ClayPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = ClayLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = ClaySecondaryLabel,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ClayPrimary,
                uncheckedThumbColor = ClaySecondaryLabel,
                uncheckedTrackColor = ClayInset
            )
        )
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clayCircle(elevation = 2.dp, backgroundColor = ClayInset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ClayPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = ClayLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = ClaySecondaryLabel,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = ClaySecondaryLabel,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(ClayInset.copy(alpha = 0.6f))
    )
}
