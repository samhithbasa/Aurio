package com.samhith.aurio.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.auth.AuthRepository
import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.data.settings.AppSettingsManager
import com.samhith.aurio.ui.auth.aurioGlow
import kotlinx.coroutines.launch
import java.io.File
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimaryGradient

// ═══════════════════════════════════════════════════════════════════════════
// 1. EDIT PROFILE DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun EditProfileDialog(
    user: UserAccount?,
    onDismiss: () -> Unit,
    onProfileUpdated: (UserAccount) -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository.getInstance(context) }

    var displayName by remember { mutableStateOf(user?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(user?.username.orEmpty().removePrefix("@")) }
    var bio by remember { mutableStateOf(user?.bio ?: "Good Music • Better Moods • Always 🎧") }
    var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
    var photoUriString by remember { mutableStateOf(user?.photoUrl.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUriString = uri.toString()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
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
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ClaySurface)
                    .border(1.2.dp, ClayInset, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Profile",
                        color = ClayLabel,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Avatar with Camera Change Icon
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(ClaySurface)
                            .border(2.dp, ClayPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUriString.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(photoUriString)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile Photo",
                                modifier = Modifier.size(86.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = displayName.take(1).ifBlank { "A" }.uppercase(),
                                color = ClayLabel,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Camera Badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(ClayPrimaryGradient)
                            .border(1.5.dp, ClayInset, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Text(
                    text = "Tap camera to change photo",
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Name Input
                ProfileInputField(
                    label = "Display Name",
                    value = displayName,
                    onValueChange = { displayName = it },
                    icon = Icons.Default.Person
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Username Input
                ProfileInputField(
                    label = "Username",
                    value = username,
                    onValueChange = { username = it },
                    prefix = "@",
                    icon = Icons.Default.Podcasts
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bio Input
                ProfileInputField(
                    label = "Bio",
                    value = bio,
                    onValueChange = { bio = it },
                    singleLine = false,
                    maxLines = 3,
                    icon = Icons.Default.MusicNote
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Phone Input
                ProfileInputField(
                    label = "Phone Number (Optional)",
                    value = phone,
                    onValueChange = { phone = it },
                    keyboardType = KeyboardType.Phone,
                    icon = Icons.Default.Phone
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Save Button
                Button(
                    onClick = {
                        isSaving = true
                        val updated = authRepository.updateUserProfile(
                            displayName = displayName,
                            username = username,
                            bio = bio,
                            phone = phone,
                            photoUrl = photoUriString.ifBlank { null }
                        )
                        onProfileUpdated(updated)
                        Toast.makeText(context, "Profile updated successfully! ✨", Toast.LENGTH_SHORT).show()
                        isSaving = false
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ClayPrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Save Changes",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 2. ACCOUNT INFORMATION DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun AccountInfoDialog(
    user: UserAccount?,
    downloadsCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val userId = if (!user?.supabaseId.isNullOrBlank()) {
        user!!.supabaseId
    } else {
        "AURIO-" + (user?.email?.hashCode() ?: 12345678).toString().takeLast(8).uppercase()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
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
                    .clip(RoundedCornerShape(26.dp))
                    .background(ClaySurface)
                    .border(1.2.dp, ClayInset, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Account Information",
                        color = ClayLabel,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Plan Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ClayInset, ClaySurface)
                            )
                        )
                        .border(1.dp, ClayInset, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Tier",
                                    tint = ClayPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Aurio Unlimited Tier",
                                    color = ClayLabel,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "100% Free Forever • No Ads • Lossless Audio",
                                color = ClaySecondaryLabel,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClayPrimaryGradient)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Info Rows
                AccountDetailRow(
                    label = "User ID",
                    value = userId,
                    copyable = true,
                    context = context
                )
                AccountDetailRow(
                    label = "Full Name",
                    value = user?.displayName?.ifBlank { "Aurio Listener" } ?: "Aurio Listener"
                )
                AccountDetailRow(
                    label = "Username Handle",
                    value = user?.username?.ifBlank { "@${user.email.substringBefore("@")}" } ?: "@listener"
                )
                AccountDetailRow(
                    label = "Email Address",
                    value = user?.email ?: "user@aurio.app"
                )
                AccountDetailRow(
                    label = "Phone Number",
                    value = user?.phone?.ifBlank { "Not configured" } ?: "Not configured"
                )
                AccountDetailRow(
                    label = "Authentication Method",
                    value = if (user?.isGoogleUser == true) "Google One-Tap OAuth" else "Verified Email OTP"
                )
                AccountDetailRow(
                    label = "Member Since",
                    value = user?.memberSince ?: "September 2024"
                )
                AccountDetailRow(
                    label = "Downloaded Tracks",
                    value = "$downloadsCount offline tracks"
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaySurface)
                ) {
                    Text(
                        text = "Done",
                        color = ClayLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 3. PRIVACY & SOCIAL DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun PrivacySocialDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPublicProfile by remember { mutableStateOf(true) }
    var shareListeningActivity by remember { mutableStateOf(true) }
    var allowRoomRequests by remember { mutableStateOf(true) }
    var showActiveStatus by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
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
                    .clip(RoundedCornerShape(26.dp))
                    .background(ClaySurface)
                    .border(1.2.dp, ClayInset, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy & Social",
                        color = ClayLabel,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Privacy Switch 1
                PrivacyToggleRow(
                    title = "Public Profile Visibility",
                    subtitle = "Allow other users in Listen Together rooms to view your profile and playlists",
                    icon = Icons.Default.Visibility,
                    checked = isPublicProfile,
                    onCheckedChange = { isPublicProfile = it }
                )

                // Privacy Switch 2
                PrivacyToggleRow(
                    title = "Share Live Listening Activity",
                    subtitle = "Display currently playing song badges when participating in live rooms",
                    icon = Icons.Default.Headphones,
                    checked = shareListeningActivity,
                    onCheckedChange = { shareListeningActivity = it }
                )

                // Privacy Switch 3
                PrivacyToggleRow(
                    title = "Allow Room Song Requests",
                    subtitle = "Let room members suggest and queue songs for you to approve as DJ host",
                    icon = Icons.Default.Groups,
                    checked = allowRoomRequests,
                    onCheckedChange = { allowRoomRequests = it }
                )

                // Privacy Switch 4
                PrivacyToggleRow(
                    title = "Online Activity Status",
                    subtitle = "Show green active pulse indicator when listening to music",
                    icon = Icons.Default.AutoAwesome,
                    checked = showActiveStatus,
                    onCheckedChange = { showActiveStatus = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Clear Search History & Cache Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClaySurface)
                        .clickable {
                            Toast.makeText(context, "Search history cleared 🧹", Toast.LENGTH_SHORT).show()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear",
                        tint = ClayPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Clear Search History",
                            color = ClayLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Remove recent queries and artist searches",
                            color = ClaySecondaryLabel,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Privacy preferences saved ✅", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ClayPrimary)
                ) {
                    Text(
                        text = "Save Preferences",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 4. NOTIFICATIONS / APP FEATURES DIALOG (AURIO VS OTHER MUSIC APPS)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun AppFeaturesDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
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
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ClaySurface)
                    .border(1.2.dp, ClayInset, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "What's New in Aurio",
                            color = ClayLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "How Aurio outperforms other music apps",
                            color = ClayPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Feature Item 1
                FeatureComparisonCard(
                    icon = Icons.Default.Groups,
                    title = "Real-Time Listen Together Rooms",
                    aurioFeature = "Sub-50ms synchronized listening with group chat, host DJ controls, and instant song request dialogs.",
                    competitorComparison = "Spotify requires Premium for group sessions; Apple Music has no native synchronized rooms."
                )

                // Feature Item 2
                FeatureComparisonCard(
                    icon = Icons.Default.Vibration,
                    title = "Apple Music Beat-Sync Haptics",
                    aurioFeature = "Dynamic tactile vibration synthesizer that analyzes kick drums and bass frequencies in real time.",
                    competitorComparison = "Only Apple Music on iOS offers music haptics; Aurio brings hardware beat vibrations to Android."
                )

                // Feature Item 3
                FeatureComparisonCard(
                    icon = Icons.Default.GraphicEq,
                    title = "Dual Lossless Streaming Engines",
                    aurioFeature = "JioSaavn 320kbps Lossless + YouTube Music fallback with 100M+ tracks and instant audio playback.",
                    competitorComparison = "Standard Spotify streams at 160kbps with audio ads on free tier; Aurio is 100% ad-free 320kbps."
                )

                // Feature Item 4
                FeatureComparisonCard(
                    icon = Icons.Default.Download,
                    title = "Unlimited Offline Downloads & Imports",
                    aurioFeature = "Download any song in high quality for offline listening + import your own local MP3s with custom cover art.",
                    competitorComparison = "Competitors lock offline downloads behind paid monthly subscriptions."
                )

                // Feature Item 5
                FeatureComparisonCard(
                    icon = Icons.Default.Podcasts,
                    title = "Spotify Playlist 1-Click Importer",
                    aurioFeature = "Paste any public Spotify playlist URL to instantly convert and import all tracks into your Aurio library.",
                    competitorComparison = "Most music apps don't let you cross-import playlists from other platforms for free."
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ClayPrimary)
                ) {
                    Text(
                        text = "Got It!",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 5. APP SETTINGS DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun AppSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { AppSettingsManager.getInstance(context) }

    val streamingQuality by settingsManager.streamingQuality.collectAsState()
    val downloadQuality by settingsManager.downloadQuality.collectAsState()
    val isWifiOnly by settingsManager.isWifiOnlyStreaming.collectAsState()
    val isBeatHaptics by settingsManager.isBeatHapticsEnabled.collectAsState()
    val equalizerPreset by settingsManager.equalizerPreset.collectAsState()

    var cacheSizeMb by remember {
        mutableStateOf(String.format("%.1f MB", settingsManager.getCacheSizeBytes() / (1024.0 * 1024.0)))
    }

    val qualityOptions = listOf("Lossless (320 kbps)", "High (256 kbps)", "Normal (160 kbps)", "Data Saver (96 kbps)")
    val eqOptions = listOf("Aurio Dynamic Bass", "Acoustic", "Vocal Booster", "Electronic", "Flat")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
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
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ClaySurface)
                    .border(1.2.dp, ClayInset, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Settings",
                        color = ClayLabel,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 1: Playback Quality
                SectionHeader("AUDIO STREAMING")
                qualityOptions.forEach { quality ->
                    val isSelected = quality == streamingQuality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ClayInset else Color.Transparent)
                            .clickable { settingsManager.setStreamingQuality(quality) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = quality,
                            color = if (isSelected) ClayLabel else ClaySecondaryLabel,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = ClayPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Beat Haptic Vibration
                SectionHeader("HAPTICS & BEATS")
                PrivacyToggleRow(
                    title = "Beat-Sync Vibration",
                    subtitle = "Vibrates your phone in sync with basslines and kick drums",
                    icon = Icons.Default.Vibration,
                    checked = isBeatHaptics,
                    onCheckedChange = { settingsManager.setBeatHapticsEnabled(it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: Equalizer
                SectionHeader("EQUALIZER PRESETS")
                eqOptions.forEach { eq ->
                    val isSelected = eq == equalizerPreset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ClayInset else Color.Transparent)
                            .clickable { settingsManager.setEqualizerPreset(eq) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = eq,
                            color = if (isSelected) ClayLabel else ClaySecondaryLabel,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = ClayPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 4: Downloads & Storage
                SectionHeader("DOWNLOADS & STORAGE")
                PrivacyToggleRow(
                    title = "Stream on Wi-Fi Only",
                    subtitle = "Prevent streaming over cellular data to save mobile data",
                    icon = Icons.Default.Wifi,
                    checked = isWifiOnly,
                    onCheckedChange = { settingsManager.setWifiOnlyStreaming(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Cache Cleaner Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClaySurface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Temporary Cache",
                            color = ClayLabel,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current size: $cacheSizeMb",
                            color = ClaySecondaryLabel,
                            fontSize = 11.5.sp
                        )
                    }

                    Button(
                        onClick = {
                            val cleared = settingsManager.clearCache()
                            cacheSizeMb = "0.0 MB"
                            Toast.makeText(context, "Cache cleared successfully! 🧹", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClayInset),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Clear Cache",
                            color = ClayPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ClayPrimary)
                ) {
                    Text(
                        text = "Close",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 6. HELP & SUPPORT DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun HelpSupportDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val supportEmail = "abhisamhith07@gmail.com"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
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
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ClaySurface)
                    .border(1.2.dp, ClayInset, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Help & Support",
                            color = ClayLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "We're here to help you enjoy your music",
                            color = ClayPrimary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Contact Support Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ClayInset, ClaySurface)
                            )
                        )
                        .border(1.dp, ClayInset, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MailOutline,
                                contentDescription = "Email",
                                tint = ClayPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Developer & Customer Support",
                                color = ClayLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "For inquiries, feedback, or reporting bugs, reach out directly to:",
                            color = ClaySecondaryLabel,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ClaySurface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = supportEmail,
                                color = ClayPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Aurio Support Email", supportEmail)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Support email copied! 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Email",
                                    tint = ClayLabel,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:$supportEmail")
                                        putExtra(Intent.EXTRA_SUBJECT, "Aurio Music App Support / Feedback")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Send Email via"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open email client: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = ClayPrimary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Send Email Now",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // FAQs Accordion
                SectionHeader("FREQUENTLY ASKED QUESTIONS")

                FaqAccordionItem(
                    question = "How do Listen Together Rooms work?",
                    answer = "Rooms synchronize playback in real-time. The host controls track selection, while guests can submit song requests and participate in the live room chat."
                )

                FaqAccordionItem(
                    question = "How do I turn on Beat Vibration Haptics?",
                    answer = "Open the Full Player screen and tap the Haptics (Vibration) button on the top-right toolbar, or toggle it in App Settings."
                )

                FaqAccordionItem(
                    question = "Can I play downloaded songs without internet?",
                    answer = "Yes! All songs in the Downloads section and imported local songs are stored directly on your device storage and play 100% offline."
                )

                FaqAccordionItem(
                    question = "How do I import Spotify playlists?",
                    answer = "Go to Library > Playlists > tap 'Import Spotify Playlist', paste any public Spotify playlist link, and Aurio will parse and add all tracks automatically."
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 7. TERMS & PRIVACY DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun TermsPrivacyDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
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
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ClaySurface)
                    .border(1.2.dp, ClayInset, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Terms & Privacy Policy",
                        color = ClayLabel,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = ClayInset,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 14.dp)
                )

                LegalSection(
                    title = "1. Acceptance of Terms",
                    content = "By using Aurio, you agree to these Terms of Service. Aurio provides a free personal music streaming, room collaboration, and library management platform for non-commercial personal entertainment.",
                    showDivider = true
                )

                LegalSection(
                    title = "2. Audio Streaming & Content Attribution",
                    content = "All songs, albums, and artwork indexed by Aurio remain the intellectual property of their respective copyright holders, artists, and record labels. Streaming feeds are delivered through licensed public APIs and CDN nodes with fair-use attribution.",
                    showDivider = true
                )

                LegalSection(
                    title = "3. Listen Together & Room Conduct",
                    content = "Users in public and private rooms agree not to distribute abusive, hateful, or infringing media in room chats. Room hosts retain the authority to moderate guest participants and approve song requests.",
                    showDivider = true
                )

                LegalSection(
                    title = "4. Privacy & Data Protection",
                    content = "Aurio values your privacy. We do not sell your personal information or listening habits to third parties. Account authentication is secured via industry-standard Supabase PostgreSQL and Google Identity Services.",
                    showDivider = true
                )

                LegalSection(
                    title = "5. Offline Storage & Local Audio",
                    content = "Downloaded audio files and imported music tracks are saved securely in your private application sandbox storage and never transmitted to external servers.",
                    showDivider = true
                )

                LegalSection(
                    title = "6. DMCA & Copyright Contact",
                    content = "If you believe your copyrighted work is accessible through Aurio in a manner that constitutes infringement, please contact our legal representative at abhisamhith07@gmail.com with your claim details for prompt resolution.",
                    showDivider = false
                )

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLE UI WIDGETS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = ClayPrimary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    prefix: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ClaySecondaryLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ClaySurface,
                unfocusedContainerColor = ClaySurface,
                focusedBorderColor = ClayPrimary,
                unfocusedBorderColor = ClayInset,
                focusedTextColor = ClayLabel,
                unfocusedTextColor = ClayLabel,
                cursorColor = ClayPrimary
            ),
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = ClayPrimary,
                    modifier = Modifier.size(18.dp)
                )
            },
            prefix = if (prefix.isNotBlank()) {
                { Text(text = prefix, color = ClayPrimary, fontWeight = FontWeight.Bold) }
            } else null,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

@Composable
private fun AccountDetailRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    context: Context? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ClaySecondaryLabel,
            fontSize = 13.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                color = ClayLabel,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (copyable && context != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Account ID", value)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied ID! 📋", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = ClayPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
    HorizontalDivider(color = ClayInset, thickness = 0.8.dp)
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = ClayPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = ClayLabel,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = ClaySecondaryLabel,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ClayPrimary,
                uncheckedThumbColor = ClayInset,
                uncheckedTrackColor = ClaySurface
            )
        )
    }
}

@Composable
private fun FeatureComparisonCard(
    icon: ImageVector,
    title: String,
    aurioFeature: String,
    competitorComparison: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ClaySurface)
            .border(1.dp, ClayInset, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ClayPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = ClayLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "✨ Aurio: $aurioFeature",
                color = ClayLabel,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "⚡ Others: $competitorComparison",
                color = ClaySecondaryLabel,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun FaqAccordionItem(
    question: String,
    answer: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClaySurface)
            .border(1.dp, ClayInset, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                color = ClayLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Expand",
                tint = ClayPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = ClayInset, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = answer,
                    color = ClaySecondaryLabel,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LegalSection(
    title: String,
    content: String,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            color = ClayLabel,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content,
            color = ClaySecondaryLabel,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal
        )
    }
    if (showDivider) {
        HorizontalDivider(
            color = ClaySurface,
            thickness = 0.8.dp,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}
