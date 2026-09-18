package com.samhith.aurio.ui.listentogether

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samhith.aurio.data.room.MockRoomData
import com.samhith.aurio.data.room.Room

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.data.room.RoomRepository
import kotlinx.coroutines.launch
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.ui.theme.ApplePrimaryGradient

/**
 * Create Room Dialog matching the reference design:
 * - "Create a Room" title with Room in coral
 * - Room Name input with music note icon and character counter
 * - Public / Private visibility selector cards
 * - Info note about inviting friends
 * - Create Room gradient CTA + Cancel button
 */
@Composable
fun CreateRoomDialog(
    user: UserAccount? = null,
    onDismiss: () -> Unit,
    onCreate: (Room) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    val maxChars = 50
    val scope = rememberCoroutineScope()
    val repository = remember { RoomRepository.getInstance() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(AppleSurface, AppleSurface)
                    )
                )
                .border(1.dp, AppleSeparator, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = AppleLabel)) { append("Create a ") }
                            withStyle(SpanStyle(color = AppleBlue)) { append("Room") }
                        },
                        fontSize = 24.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppleSecondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = "Listen together, from anywhere",
                    color = AppleSecondaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Room Name Label
                Text(
                    text = "Room Name",
                    color = AppleLabel,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Room Name Input
                BasicTextField(
                    value = roomName,
                    onValueChange = { if (it.length <= maxChars) roomName = it },
                    textStyle = TextStyle(
                        color = AppleLabel,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    cursorBrush = SolidColor(AppleBlue),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppleFill)
                                .border(1.dp, AppleSeparator, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = AppleSecondaryLabel,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (roomName.isEmpty()) {
                                    Text(
                                        text = "Late Night Vibes",
                                        color = AppleSecondaryLabel.copy(alpha = 0.5f),
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                                innerTextField()
                            }
                            Text(
                                text = "${roomName.length}/$maxChars",
                                color = AppleSecondaryLabel,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Room Visibility Label
                Text(
                    text = "Room Visibility",
                    color = AppleLabel,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Public / Private Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VisibilityCard(
                        icon = Icons.Default.Language,
                        title = "Public",
                        description = "Anyone can find and join this room",
                        isSelected = isPublic,
                        onClick = { isPublic = true },
                        modifier = Modifier.weight(1f)
                    )
                    VisibilityCard(
                        icon = Icons.Default.Lock,
                        title = "Private",
                        description = "Only people with the invite link can join",
                        isSelected = !isPublic,
                        onClick = { isPublic = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Info Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleSurface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You can invite friends after creating the room.",
                        color = AppleSecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Create Room Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ApplePrimaryGradient)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !isCreating && roomName.isNotBlank()
                        ) {
                            if (roomName.isNotBlank() && !isCreating) {
                                isCreating = true
                                scope.launch {
                                    val result = repository.createRoom(
                                        name = roomName.trim(),
                                        isPublic = isPublic,
                                        host = user,
                                        tags = listOf("Live", if (isPublic) "Public" else "Private")
                                    )
                                    isCreating = false
                                    result.onSuccess { createdRoom ->
                                        onCreate(createdRoom)
                                    }.onFailure {
                                        val fallbackRoom = Room(
                                            id = "user_room_${System.currentTimeMillis()}",
                                            name = roomName.trim(),
                                            isPublic = isPublic,
                                            hostName = user?.displayName?.ifBlank { "You" } ?: "You",
                                            listenerCount = 1,
                                            tags = listOf("Live"),
                                            code = MockRoomData.generateRoomCode()
                                        )
                                        onCreate(fallbackRoom)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            color = AppleOnAccent,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Create Room",
                            color = AppleOnAccent,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cancel
                Text(
                    text = "Cancel",
                    color = AppleSecondaryLabel,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                )
            }
        }
    }
}

/**
 * Selectable visibility card for Public/Private option.
 */
@Composable
private fun VisibilityCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AppleBlue else AppleFill,
        animationSpec = tween(200),
        label = "visibility_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) AppleSurface else AppleFill,
        animationSpec = tween(200),
        label = "visibility_bg"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) AppleBlue else AppleSecondaryLabel,
                modifier = Modifier.size(22.dp)
            )
            // Radio indicator
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 5.dp else 1.5.dp,
                        color = if (isSelected) AppleBlue else AppleSecondaryLabel,
                        shape = CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            color = AppleLabel,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            color = AppleSecondaryLabel,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 14.sp
        )
    }
}
