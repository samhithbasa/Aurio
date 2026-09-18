package com.samhith.aurio.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.ui.theme.AurioFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleBlueLight
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.ui.theme.AppleGreen

/**
 * OTP Verification view featuring:
 * 1. 4 individual digit boxes.
 * 2. Rotating lighting sweep border animation when user enters a number.
 * 3. 4-box morph into 1 single box animation with animated green checkmark upon verification.
 */
@Composable
fun OtpVerificationView(
    email: String,
    otpCode: String? = null,
    isEmailDelivered: Boolean = false,
    deliveryNote: String = "",
    onBackClick: () -> Unit,
    onVerifyOtp: (String, (Boolean, String?) -> Unit) -> Unit,
    onResendOtp: () -> Unit = {},
    onVerificationSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val otpValues = remember { mutableStateListOf("", "", "", "") }
    val focusRequesters = remember { List(4) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Rotating border animation state (0f to 360f)
    val rotationAnim = remember { Animatable(0f) }

    // Morphing animation states
    var isVerifying by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }

    // Green checkmark path animation progress
    val checkmarkProgress = remember { Animatable(0f) }

    // Combined box animation values
    val boxWidth by animateDpAsState(
        targetValue = if (isVerified) 76.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f),
        label = "box_width"
    )
    val boxHeight by animateDpAsState(
        targetValue = if (isVerified) 76.dp else 60.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f),
        label = "box_height"
    )
    val boxCornerRadius by animateDpAsState(
        targetValue = if (isVerified) 38.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "box_corner"
    )
    val boxSpacing by animateDpAsState(
        targetValue = if (isVerified) 0.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f),
        label = "box_spacing"
    )

    // Trigger rotating light sweep when a digit is entered
    fun triggerRotateGlow() {
        scope.launch {
            rotationAnim.snapTo(0f)
            rotationAnim.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(Unit) {
        // Auto focus on first box on enter
        try {
            delay(300)
            focusRequesters[0].requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Brand Header inside the Card
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = AppleLabel,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AurioFontFamily
                    )
                ) {
                    append("Aur")
                }
                withStyle(
                    style = SpanStyle(
                        color = AppleBlue,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AurioFontFamily
                    )
                ) {
                    append("io")
                }
            },
            fontSize = 34.sp,
            fontFamily = AurioFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Tagline "MUSIC MAKES A BETTER YOU"
        Text(
            text = "M U S I C   M A K E S   A   B E T T E R   Y O U",
            fontSize = 9.5.sp,
            fontFamily = AurioFontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            color = AppleSecondaryLabel,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Top Back Row + Verification Code Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppleFill)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppleLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Verification Code",
                fontSize = 22.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Bold,
                color = AppleLabel
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter the 4-digit code sent to\n${if (email.isNotBlank()) email else "your registered email"}",
            fontSize = 13.sp,
            fontFamily = AurioFontFamily,
            color = AppleSecondaryLabel,
            textAlign = TextAlign.Start,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (isEmailDelivered) {
            Text(
                text = "✓ Live email sent to $email. Please check your inbox and spam folder.",
                fontSize = 11.5.sp,
                fontFamily = AurioFontFamily,
                color = AppleGreen,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!otpCode.isNullOrBlank()) {
            // Notice card when testing with non-owner email on free Resend tier
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppleBlue.copy(alpha = 0.20f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (deliveryNote.isNotBlank()) deliveryNote else "Resend free tier only sends emails to abhisamhith07@gmail.com.",
                            fontSize = 10.sp,
                            fontFamily = AurioFontFamily,
                            color = AppleBlue,
                            lineHeight = 13.sp
                        )
                        Text(
                            text = "Test Code: $otpCode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AurioFontFamily,
                            color = AppleLabel
                        )
                    }
                    Text(
                        text = "Auto-Fill",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AurioFontFamily,
                        color = AppleBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppleBlue.copy(alpha = 0.27f))
                            .clickable {
                                for (i in 0 until 4) {
                                    if (i < otpCode.length) {
                                        otpValues[i] = otpCode[i].toString()
                                    }
                                }
                                triggerRotateGlow()
                                keyboardController?.hide()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // OTP Boxes Container / Morphing Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isVerified) {
                // 4 Individual OTP Input Boxes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(boxSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (index in 0 until 4) {
                        val isFocused = otpValues[index].isNotEmpty()
                        val currentRotation = rotationAnim.value

                        Box(
                            modifier = Modifier
                                .size(width = boxWidth, height = boxHeight)
                                .clip(RoundedCornerShape(boxCornerRadius))
                                .background(AppleFill)
                                .then(
                                    if (currentRotation > 0f && currentRotation < 360f) {
                                        Modifier.border(
                                            width = 2.dp,
                                            brush = Brush.sweepGradient(
                                                colors = listOf(
                                                    AppleBlue,
                                                    AppleBlue,
                                                    AppleBlueLight,
                                                    AppleBlue,
                                                    AppleBlue
                                                )
                                            ),
                                            shape = RoundedCornerShape(boxCornerRadius)
                                        )
                                    } else {
                                        Modifier.border(
                                            width = if (isFocused) 1.5.dp else 1.dp,
                                            color = if (isFocused) AppleBlue else AppleSeparator,
                                            shape = RoundedCornerShape(boxCornerRadius)
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = otpValues[index],
                                onValueChange = { newValue ->
                                    if (newValue.length <= 1 && newValue.all { it.isDigit() }) {
                                        otpValues[index] = newValue
                                        errorMessage = null
                                        if (newValue.isNotEmpty()) {
                                            triggerRotateGlow()
                                            if (index < 3) {
                                                focusRequesters[index + 1].requestFocus()
                                            } else {
                                                keyboardController?.hide()
                                            }
                                        }
                                    } else if (newValue.length == 4 && newValue.all { it.isDigit() }) {
                                        // Pasted full OTP
                                        for (i in 0 until 4) {
                                            otpValues[i] = newValue[i].toString()
                                        }
                                        errorMessage = null
                                        triggerRotateGlow()
                                        keyboardController?.hide()
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(focusRequesters[index])
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.key == Key.Backspace && otpValues[index].isEmpty() && index > 0) {
                                            focusRequesters[index - 1].requestFocus()
                                            otpValues[index - 1] = ""
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .padding(horizontal = 8.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = AppleLabel,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = if (index == 3) ImeAction.Done else ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { keyboardController?.hide() }
                                ),
                                singleLine = true
                            )
                        }
                    }
                }
            } else {
                // Combined single box with green animated checkmark
                Box(
                    modifier = Modifier
                        .size(width = boxWidth, height = boxHeight)
                        .aurioGlow(
                            color = AppleGreen,
                            alpha = 0.5f,
                            blurRadius = 24.dp
                        )
                        .clip(RoundedCornerShape(boxCornerRadius))
                        .background(AppleFill)
                        .border(
                            width = 2.5.dp,
                            color = AppleGreen,
                            shape = RoundedCornerShape(boxCornerRadius)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Animated Checkmark
                    Canvas(
                        modifier = Modifier.size(36.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(size.width * 0.2f, size.height * 0.52f)
                            lineTo(size.width * 0.44f, size.height * 0.74f)
                            lineTo(size.width * 0.82f, size.height * 0.28f)
                        }

                        // Measure path length and trim by checkmarkProgress
                        drawPath(
                            path = path,
                            color = AppleGreen,
                            style = Stroke(
                                width = 5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = AppleBlue,
                fontSize = 12.sp,
                fontFamily = AurioFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (isVerified) {
            Text(
                text = "OTP Verified Successfully!",
                fontSize = 17.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Bold,
                color = AppleGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your Aurio account is ready",
                fontSize = 13.sp,
                fontFamily = AurioFontFamily,
                color = AppleSecondaryLabel
            )
        } else {
            // Verify OTP Button
            val isOtpComplete = otpValues.all { it.isNotEmpty() }

            AurioGradientButton(
                text = if (isVerifying) "Verifying..." else "Verify OTP",
                enabled = isOtpComplete && !isVerifying,
                onClick = {
                    keyboardController?.hide()
                    errorMessage = null
                    isVerifying = true
                    val fullOtp = otpValues.joinToString("")
                    onVerifyOtp(fullOtp) { success, error ->
                        isVerifying = false
                        if (success) {
                            scope.launch {
                                isVerified = true
                                checkmarkProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                                )
                                delay(500)
                                onVerificationSuccess()
                            }
                        } else {
                            errorMessage = error ?: "Incorrect OTP code. Please try again."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Resend Code Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Didn't receive code? ",
                    fontSize = 13.sp,
                    fontFamily = AurioFontFamily,
                    color = AppleSecondaryLabel
                )
                Text(
                    text = "Resend Code",
                    fontSize = 13.sp,
                    fontFamily = AurioFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = AppleBlue,
                    modifier = Modifier.clickable {
                        otpValues.replaceAll { "" }
                        errorMessage = null
                        focusRequesters[0].requestFocus()
                        triggerRotateGlow()
                        onResendOtp()
                    }
                )
            }
        }
    }
}
