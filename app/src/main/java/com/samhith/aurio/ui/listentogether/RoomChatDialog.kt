package com.samhith.aurio.ui.listentogether

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.samhith.aurio.data.room.RoomMessage
import com.samhith.aurio.data.room.SongRequest
import com.samhith.aurio.data.room.SongRequestStatus
import com.samhith.aurio.data.room.SongRequestType
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayPrimaryGradient

/**
 * Center Modal Dialog for In-Room Chat messaging and live interactions.
 * Now renders song request messages as rich interactive cards with Accept/Reject buttons for the host.
 */
@Composable
fun RoomChatDialog(
    roomName: String,
    messages: List<RoomMessage>,
    currentUserId: String,
    isHost: Boolean = false,
    requestStatuses: Map<String, SongRequestStatus> = emptyMap(),
    onAcceptRequest: ((SongRequest) -> Unit)? = null,
    onDeclineRequest: ((SongRequest) -> Unit)? = null,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ClaySurface,
                            ClayBackground
                        )
                    )
                )
                .border(1.2.dp, ClayInset, RoundedCornerShape(28.dp))
                .aurioGlow(ClayPrimary, alpha = 0.35f, blurRadius = 24.dp)
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header Row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Room Chat",
                            color = ClayLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = roomName,
                            color = ClayPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ClaySurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Chat Messages List ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ClaySurface)
                        .border(0.8.dp, ClayInset, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "💬 No messages yet",
                                color = ClaySecondaryLabel,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Say hi or share your music vibe!",
                                color = ClaySecondaryLabel,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages) { msg ->
                                val isSelf = msg.senderId == currentUserId || msg.senderName == "You"
                                val isSystem = msg.senderId == "system" || msg.senderName == "Aurio"

                                // Check if this message contains a song request
                                val songRequest = SongRequest.fromMessageText(msg.text)
                                // Check if this message has a status update (accept/decline announcement)
                                val statusUpdate = SongRequest.parseStatusUpdate(msg.text)

                                if (songRequest != null) {
                                    // Render as a rich Song Request Card
                                    val resolvedStatus = requestStatuses[songRequest.id] ?: songRequest.status
                                    SongRequestCardBubble(
                                        songRequest = songRequest,
                                        resolvedStatus = resolvedStatus,
                                        senderName = msg.senderName,
                                        isHost = isHost,
                                        isSelf = isSelf,
                                        onAccept = { onAcceptRequest?.invoke(songRequest) },
                                        onDecline = { onDeclineRequest?.invoke(songRequest) }
                                    )
                                } else if (statusUpdate != null) {
                                    // Render status update as a system-style notification
                                    val cleanText = SongRequest.cleanDisplayText(msg.text)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cleanText,
                                            color = ClayPrimary.copy(alpha = 0.9f),
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else if (isSystem) {
                                    // System notification bubble
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = ClayPrimary.copy(alpha = 0.9f),
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    // User chat message bubble
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
                                    ) {
                                        if (!isSelf) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(ClayInset),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = msg.senderName.firstOrNull()?.uppercase() ?: "?",
                                                    color = ClayPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        Column(
                                            horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
                                        ) {
                                            if (!isSelf) {
                                                Text(
                                                    text = msg.senderName,
                                                    color = ClaySecondaryLabel,
                                                    fontSize = 10.5.sp,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(
                                                        RoundedCornerShape(
                                                            topStart = 14.dp,
                                                            topEnd = 14.dp,
                                                            bottomStart = if (isSelf) 14.dp else 2.dp,
                                                            bottomEnd = if (isSelf) 2.dp else 14.dp
                                                        )
                                                    )
                                                    .background(
                                                        if (isSelf) ClayPrimaryGradient else SolidColor(ClayInset)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = msg.text,
                                                    color = if (isSelf) Color.White else ClayLabel,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Quick Emojis Row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("🔥", "❤️", "🎵", "🙌", "✨", "🚀").forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClaySurface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSendMessage(emoji) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Message Input Bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(ClaySurface)
                        .border(1.dp, ClayInset, RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = null,
                        tint = ClayPrimary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = ClayLabel,
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        cursorBrush = SolidColor(ClayPrimary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (messageInput.isBlank()) {
                                Text(
                                    text = "Send a message...",
                                    color = ClaySecondaryLabel,
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank()) {
                                onSendMessage(messageInput.trim())
                                messageInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ClayPrimaryGradient)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Rich card bubble for song request messages in chat.
 * Shows song artwork, title, artist, requester, request type badge,
 * and Accept/Reject action buttons for the host.
 */
@Composable
private fun SongRequestCardBubble(
    songRequest: SongRequest,
    resolvedStatus: SongRequestStatus,
    senderName: String,
    isHost: Boolean,
    isSelf: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
    ) {
        // Sender name
        if (!isSelf) {
            Text(
                text = senderName,
                color = ClaySecondaryLabel,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 36.dp, bottom = 2.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
        ) {
            if (!isSelf) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ClayInset),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = senderName.firstOrNull()?.uppercase() ?: "?",
                        color = ClayPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Request Card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ClaySurface)
                    .border(1.dp, ClayInset, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column {
                    // Request type badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClayInset)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (songRequest.requestType == SongRequestType.PLAY_NEXT)
                                Icons.Default.QueuePlayNext
                            else
                                Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = ClayPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (songRequest.requestType == SongRequestType.PLAY_NEXT)
                                "Play Next" else "Add to Queue",
                            color = ClayPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Song info row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClaySurface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (songRequest.song.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(songRequest.song.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = songRequest.song.title,
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

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = songRequest.song.title,
                                color = ClayLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = songRequest.song.artist.ifBlank { "Aurio Music" },
                                color = ClaySecondaryLabel,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons or status badge
                    when (resolvedStatus) {
                        SongRequestStatus.PENDING -> {
                            if (isHost) {
                                // Host gets Accept / Decline buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Decline button
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(17.dp))
                                            .background(ClaySurface)
                                            .border(1.dp, ClayInset, RoundedCornerShape(17.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = onDecline
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Decline",
                                            tint = ClaySecondaryLabel,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Decline",
                                            color = ClaySecondaryLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }

                                    // Accept button
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(17.dp))
                                            .background(ClayPrimaryGradient)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = onAccept
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Accept",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Accept",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            } else {
                                // Non-host sees pending status
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ClayInset)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "⏳ Waiting for host...",
                                        color = Color(0xFFD4A574),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                        SongRequestStatus.ACCEPTED -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ClayInset)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✅ Accepted",
                                    color = ClaySecondaryLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                        SongRequestStatus.DECLINED -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ClayInset)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "❌ Declined",
                                    color = ClayPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
