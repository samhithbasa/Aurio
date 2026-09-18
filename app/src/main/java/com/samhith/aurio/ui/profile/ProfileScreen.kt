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
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
            .background(AppleBackground)
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
                                color = AppleLabel,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "Profile",
                                color = AppleBlue,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Music tells my story",
                            color = AppleSecondaryLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Top-Right Settings Gear Icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppleSurface)
                            .border(1.dp, AppleSeparator, CircleShape)
                            .clickable { showAppSettingsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AppleBlue,
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
                        modifier = Modifier.size(108.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .aurioGlow(
                                    color = AppleBlue,
                                    alpha = 0.45f,
                                    blurRadius = 24.dp
                                )
                                .clip(CircleShape)
                                .background(AppleSurface)
                                .border(2.5.dp, AppleBlue, CircleShape),
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
                                    color = AppleLabel,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        // Camera Button to Replace Image
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(ApplePrimaryGradient)
                                .border(2.dp, AppleSeparator, CircleShape)
                                .clickable { photoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Replace Image",
                                tint = AppleOnAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Username with Edit Pencil Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showEditProfileDialog = true }
                    ) {
                        Text(
                            text = currentUser?.displayName?.ifBlank { "Aurio Listener" } ?: "Aurio Listener",
                            color = AppleLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = AppleBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Username Handle
                    val handle = currentUser?.username?.ifBlank {
                        "@${currentUser?.email?.substringBefore("@") ?: "auriolistener"}"
                    } ?: "@auriolistener"
                    Text(
                        text = if (handle.startsWith("@")) handle else "@$handle",
                        color = AppleBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bio / Description
                    Text(
                        text = currentUser?.bio ?: "Good Music • Better Moods • Always 🎧",
                        color = AppleSecondaryLabel,
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
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppleSurface)
                        .border(1.dp, AppleSeparator, RoundedCornerShape(20.dp))
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
                            iconTint = AppleBlue,
                            onClick = onLikedSongsClick
                        )

                        StatDivider()

                        // Stat 2: Playlists
                        StatItem(
                            count = playlists.size.toString(),
                            label = "Playlists",
                            icon = Icons.Default.QueueMusic,
                            iconTint = AppleBlue,
                            onClick = onPlaylistsClick
                        )

                        StatDivider()

                        // Stat 3: Following (Artists)
                        StatItem(
                            count = followedArtists.size.toString(),
                            label = "Following",
                            icon = Icons.Default.Person,
                            iconTint = AppleBlue,
                            onClick = onFollowingArtistsClick
                        )

                        StatDivider()

                        // Stat 4: Downloaded (offline songs)
                        StatItem(
                            count = downloadedSongs.size.toString(),
                            label = "Downloaded",
                            icon = Icons.Default.CloudDownload,
                            iconTint = AppleBlue,
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppleSurface)
                        .border(1.dp, AppleSeparator, RoundedCornerShape(18.dp))
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppleSurface)
                        .border(1.dp, AppleSeparator, RoundedCornerShape(18.dp))
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleSurface)
                        .border(1.2.dp, AppleBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable { showLogoutConfirmDialog = true }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Log Out",
                            tint = AppleBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Log Out",
                            color = AppleBlue,
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
                containerColor = AppleSurface,
                title = {
                    Text(
                        text = "Log Out of Aurio?",
                        color = AppleLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to log out? Your downloaded tracks and liked songs will remain securely saved on this device.",
                        color = AppleSecondaryLabel,
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
                        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                    ) {
                        Text("Log Out", color = AppleOnAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmDialog = false }) {
                        Text("Cancel", color = AppleSecondaryLabel)
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
        color = AppleLabel,
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = count,
            color = AppleLabel,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = AppleSecondaryLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(AppleFill)
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
                    .clip(CircleShape)
                    .background(AppleSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = AppleBlue,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = AppleLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AppleSecondaryLabel,
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
                checkedThumbColor = AppleOnAccent,
                checkedTrackColor = AppleBlue,
                uncheckedThumbColor = AppleFill,
                uncheckedTrackColor = AppleSurface
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
                    .clip(CircleShape)
                    .background(AppleSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = AppleBlue,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = AppleLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AppleSecondaryLabel,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = AppleGray,
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
            .height(0.8.dp)
            .background(AppleSurface)
    )
}
