package com.samhith.aurio.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.room.RoomSessionManager
import com.samhith.aurio.data.room.SongRequestType
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayPeach

/**
 * Dark frosted glass context menu dialog triggered by long-pressing any song.
 * Displays: Play Now, Add to Queue, Play Next, Add to Playlist, Like/Favorite Song, and Share.
 */
@Composable
fun SongActionDialog(
    song: SongItem,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLike: () -> Unit,
    onShare: () -> Unit,
    onPlayNow: () -> Unit,
    onAddToPlaylist: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { RoomSessionManager.getInstance() }
    val activeRoom by sessionManager.activeRoom.collectAsState()
    val isHost by sessionManager.isHost.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ClaySurface,
                            ClayBackground
                        )
                    )
                )
                .border(1.2.dp, ClayInset, RoundedCornerShape(24.dp))
                .aurioGlow(ClayPrimary, alpha = 0.25f, blurRadius = 24.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Song Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClaySurface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
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
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = ClayLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist.ifBlank { song.album.ifBlank { "Aurio Music" } },
                            color = ClaySecondaryLabel,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ClayInset, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: Play Now
                ActionRowItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Play Now",
                    subtitle = "Start playing this song immediately",
                    accentColor = ClayPrimary,
                    onClick = {
                        onPlayNow()
                        onDismiss()
                    }
                )

                // Option 2: Add to Queue (with Queue symbol)
                ActionRowItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = if (activeRoom != null && !isHost) "Request Add to Queue" else "Add to Queue",
                    subtitle = if (activeRoom != null && !isHost) "Send request to room host" else "Add to the end of your playback queue",
                    accentColor = ClayPrimary,
                    onClick = {
                        if (activeRoom != null && !isHost) {
                            sessionManager.submitSongRequest(song, SongRequestType.ADD_TO_QUEUE)
                            Toast.makeText(context, "Request sent to host: ${song.title}", Toast.LENGTH_SHORT).show()
                        } else {
                            onAddToQueue()
                            Toast.makeText(context, "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                )

                // Option 3: Play Next
                ActionRowItem(
                    icon = Icons.Default.QueuePlayNext,
                    title = if (activeRoom != null && !isHost) "Request Play Next" else "Play Next",
                    subtitle = if (activeRoom != null && !isHost) "Send request to room host" else "Play immediately after current song",
                    accentColor = ClayPrimary,
                    onClick = {
                        if (activeRoom != null && !isHost) {
                            sessionManager.submitSongRequest(song, SongRequestType.PLAY_NEXT)
                            Toast.makeText(context, "Request sent to host: ${song.title}", Toast.LENGTH_SHORT).show()
                        } else {
                            onPlayNext()
                            Toast.makeText(context, "Playing next: ${song.title}", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                )

                // Option 4: Add to Playlist
                ActionRowItem(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    title = "Add to Playlist",
                    subtitle = "Save to your playlists or create new",
                    accentColor = ClayPrimary,
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )

                // Option 5: Like / Save to Favorites
                ActionRowItem(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    title = if (isLiked) "Remove from Liked Songs" else "Save to Liked Songs",
                    subtitle = if (isLiked) "Remove from your favorites collection" else "Add to your favorites collection",
                    accentColor = if (isLiked) ClayPeach else ClaySecondaryLabel,
                    onClick = {
                        onToggleLike()
                        onDismiss()
                    }
                )

                // Option 6: Share
                ActionRowItem(
                    icon = Icons.Default.Share,
                    title = "Share Song",
                    subtitle = "Share song link with friends",
                    accentColor = ClaySecondaryLabel,
                    onClick = {
                        onShare()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ClayInset),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ClayLabel,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = subtitle,
                color = ClaySecondaryLabel,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
