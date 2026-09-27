package com.samhith.aurio.ui.listentogether

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samhith.aurio.data.room.MockRoomData
import com.samhith.aurio.data.room.Room
import com.samhith.aurio.data.room.RoomRepository
import kotlinx.coroutines.delay
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClayCard
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClayMint
import com.samhith.aurio.ui.theme.ClayPeach
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayPrimaryGradient
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClaySurface
import com.samhith.aurio.ui.theme.clayButton
import com.samhith.aurio.ui.theme.clayCard
import com.samhith.aurio.ui.theme.clayCircle
import com.samhith.aurio.ui.theme.clayInset

/**
 * Join Room Dialog with 4-digit OTP-style code entry.
 * Features animated feedback: rotation animation on each digit entry,
 * success tick or error X on code validation.
 */
@Composable
fun JoinRoomDialog(
    onDismiss: () -> Unit,
    onJoinRoom: (Room) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var validationState by remember { mutableStateOf<ValidationState>(ValidationState.Idle) }

    // Rotation animation for feedback
    val rotationAngle by animateFloatAsState(
        targetValue = when (validationState) {
            ValidationState.Success -> 360f
            ValidationState.Error -> 15f
            else -> 0f
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "code_rotation"
    )

    val repository = remember { RoomRepository.getInstance() }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus and open keyboard when dialog opens
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Auto-validate when 4 digits entered
    LaunchedEffect(code) {
        if (code.length == 4) {
            delay(200) // Brief pause for visual effect
            val matchedRoom = repository.getRoomByCode(code)
            if (matchedRoom != null) {
                validationState = ValidationState.Success
                delay(500)
                onJoinRoom(matchedRoom)
            } else {
                validationState = ValidationState.Error
                delay(1200)
                validationState = ValidationState.Idle
                code = ""
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clayCard(cornerRadius = 28.dp, elevation = 10.dp, backgroundColor = ClayCard)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = ClayLabel)) { append("Join a ") }
                            withStyle(SpanStyle(color = ClayPrimary)) { append("Room") }
                        },
                        fontSize = 24.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clayCircle(elevation = 3.dp, backgroundColor = ClaySurface)
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

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Enter the 4-digit room code",
                    color = ClaySecondaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 4-digit code input area
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Visual 4-digit code boxes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.rotate(
                            if (validationState == ValidationState.Error) rotationAngle else 0f
                        )
                    ) {
                        repeat(4) { index ->
                            val char = code.getOrNull(index)?.toString() ?: ""
                            val isActive = index == code.length && validationState == ValidationState.Idle
                            val borderColor = when {
                                validationState == ValidationState.Success -> ClayMint
                                validationState == ValidationState.Error -> ClayPeach
                                isActive -> ClayPrimary
                                char.isNotEmpty() -> ClayPrimary.copy(alpha = 0.6f)
                                else -> Color.Transparent
                            }

                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clayInset(cornerRadius = 16.dp, backgroundColor = ClayInset)
                                    .border(
                                        width = if (isActive || char.isNotEmpty() || validationState != ValidationState.Idle) 2.dp else 0.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char,
                                    color = ClayLabel,
                                    fontSize = 24.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Full overlay BasicTextField to capture all taps and focus
                    BasicTextField(
                        value = code,
                        onValueChange = { newValue ->
                            if (newValue.length <= 4 && newValue.all { it.isDigit() } && validationState == ValidationState.Idle) {
                                code = newValue
                            }
                        },
                        modifier = Modifier
                            .matchParentSize()
                            .focusRequester(focusRequester)
                            .alpha(0.01f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() }
                        ),
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        cursorBrush = SolidColor(Color.Transparent)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Validation feedback
                AnimatedVisibility(
                    visible = validationState != ValidationState.Idle,
                    enter = scaleIn(tween(200)) + fadeIn()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (validationState == ValidationState.Success) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (validationState == ValidationState.Success) ClayMint else ClayPeach,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (validationState == ValidationState.Success) "Room found! Joining..." else "Invalid code. Try again.",
                            color = if (validationState == ValidationState.Success) ClayMint else ClayPeach,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Join Room Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clayButton(
                            cornerRadius = 18.dp,
                            elevation = if (code.length == 4 && validationState == ValidationState.Idle) 6.dp else 1.dp,
                            backgroundColor = if (code.length == 4 && validationState == ValidationState.Idle) null else ClayInset
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = code.length == 4 && validationState == ValidationState.Idle
                        ) { /* Validation happens automatically */ },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Join Room",
                        color = if (code.length == 4) Color.White else ClaySecondaryLabel,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Cancel",
                    color = ClaySecondaryLabel,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                )
            }
        }
    }
}

private enum class ValidationState {
    Idle, Success, Error
}
