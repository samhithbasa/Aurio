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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.data.room.MockRoomData
import com.samhith.aurio.data.room.Room
import com.samhith.aurio.data.room.RoomRepository
import com.samhith.aurio.ui.theme.ClayCardGradient
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClayLilac
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayPrimaryGradient
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.clayButton
import com.samhith.aurio.ui.theme.clayCard
import com.samhith.aurio.ui.theme.clayCircle
import com.samhith.aurio.ui.theme.clayInset
import kotlinx.coroutines.launch

/**
 * Create Room Dialog matching the 3D Claymorphism reference design (Image 1):
 * - "Create a Room" title with Room highlighted in soft periwinkle/lilac
 * - Inset clay Room Name input with music note icon and counter
 * - 3D Public / Private visibility selector cards
 * - Info note about inviting friends
 * - Inflated 3D Clay CTA button + Cancel text
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
    val maxChars = 30
    val scope = rememberCoroutineScope()
    val repository = remember { RoomRepository.getInstance() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clayCard(
                    cornerRadius = 28.dp,
                    elevation = 16.dp,
                    gradient = ClayCardGradient
                )
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
                            withStyle(SpanStyle(color = ClayLabel)) { append("Create a ") }
                            withStyle(SpanStyle(color = ClayPrimary)) { append("Room") }
                        },
                        fontSize = 24.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clayCircle(elevation = 3.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClaySecondaryLabel,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Listen together, from anywhere",
                    color = ClaySecondaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Room Name Label
                Text(
                    text = "Room Name",
                    color = ClayLabel,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Debossed Inset Clay Room Name Input
                BasicTextField(
                    value = roomName,
                    onValueChange = { if (it.length <= maxChars) roomName = it },
                    textStyle = TextStyle(
                        color = ClayLabel,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(ClayPrimary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clayInset(cornerRadius = 16.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = ClaySecondaryLabel,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (roomName.isEmpty()) {
                                    Text(
                                        text = "Late Night Vibes",
                                        color = ClaySecondaryLabel.copy(alpha = 0.55f),
                                        fontSize = 14.5.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                                innerTextField()
                            }
                            Text(
                                text = "${roomName.length}/$maxChars",
                                color = ClaySecondaryLabel,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Room Visibility Label
                Text(
                    text = "Room Visibility",
                    color = ClayLabel,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Public / Private Clay Cards (Matching Image 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClayVisibilityCard(
                        icon = Icons.Default.Language,
                        title = "Public",
                        description = "Anyone can find and join this room",
                        isSelected = isPublic,
                        onClick = { isPublic = true },
                        modifier = Modifier.weight(1f)
                    )
                    ClayVisibilityCard(
                        icon = Icons.Default.Lock,
                        title = "Private",
                        description = "Only people with the invite link can join",
                        isSelected = !isPublic,
                        onClick = { isPublic = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Info Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(cornerRadius = 14.dp, elevation = 2.dp)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ClayPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "You can invite friends after creating the room.",
                        color = ClaySecondaryLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3D Puffy Clay Create Room Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clayButton(
                            gradient = ClayPrimaryGradient,
                            cornerRadius = 26.dp,
                            elevation = 8.dp
                        )
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
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Create Room",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cancel Text
                Text(
                    text = "Cancel",
                    color = ClaySecondaryLabel,
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
 * 3D Claymorphic Visibility Card for Public/Private option.
 */
@Composable
private fun ClayVisibilityCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clayCard(
                cornerRadius = 18.dp,
                elevation = if (isSelected) 6.dp else 2.dp,
                gradient = if (isSelected) {
                    Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF2EFFF)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFF9F8FD), Color(0xFFEDEBF5)))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Round Clay Icon Bubble
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clayCircle(
                            elevation = 3.dp,
                            gradient = if (isSelected) ClayPrimaryGradient else ClayCardGradient,
                            shadowColor = if (isSelected) Color(0x407A96FE) else Color(0x208488A6)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else ClaySecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Radio Indicator
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) ClayPrimary else Color.Transparent)
                        .border(
                            width = if (isSelected) 0.dp else 1.5.dp,
                            color = if (isSelected) ClayPrimary else ClaySecondaryLabel.copy(alpha = 0.6f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = ClayLabel,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = ClaySecondaryLabel,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 14.sp
            )
        }
    }
}
