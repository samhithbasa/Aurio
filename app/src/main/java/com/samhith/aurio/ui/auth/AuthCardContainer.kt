package com.samhith.aurio.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.samhith.aurio.data.auth.AuthRepository
import kotlinx.coroutines.launch
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClaySurface

enum class AuthScreenMode {
    LOGIN,
    SIGN_UP
}

enum class SignUpStep {
    FORM,
    OTP
}

/**
 * 3D 180° Vertical Flip Glassmorphic Card Container with Top Notch & Synchronized Rotating Mascot Logo.
 */
@Composable
fun AuthCardContainer(
    onLoginSuccess: (String, String) -> Unit,
    onGoogleLogin: () -> Unit,
    onRegistrationComplete: () -> Unit,
    logoBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(AuthScreenMode.LOGIN) }
    var signUpStep by remember { mutableStateOf(SignUpStep.FORM) }
    var registeredEmail by remember { mutableStateOf("") }
    var registeredPassword by remember { mutableStateOf("") }
    var otpDispatchResult by remember { mutableStateOf<com.samhith.aurio.data.auth.OtpDispatchResult?>(null) }

    // 180° 3D Horizontal Flip Animation (rotation on Y axis)
    val rotationAngle by animateFloatAsState(
        targetValue = if (currentMode == AuthScreenMode.LOGIN) 0f else 180f,
        animationSpec = tween(
            durationMillis = 700,
            easing = FastOutSlowInEasing
        ),
        label = "card_flip_rotation"
    )

    val isFrontFace = rotationAngle < 90f
    val cornerRadius = 40.dp
    val notchWidth = 150.dp
    val notchDepth = 28.dp
    val cardHeight = 636.dp
    val cardShape = remember { NotchedCardShape(cornerRadius, notchWidth, notchDepth) }

    // Outer 3D Perspective Box that flips around Y-axis
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotationAngle
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // Notched Card Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .drawBehind {
                    val crPx = cornerRadius.toPx()
                    val nwPx = notchWidth.toPx()
                    val ndPx = notchDepth.toPx()
                    val notchedPath = createNotchedCardPath(size, crPx, nwPx, ndPx)

                    // Outer diffuse red neon halo
                    val outerPaint = Paint().apply {
                        asFrameworkPaint().apply {
                            color = Color.Transparent.toArgb()
                            setShadowLayer(
                                26.dp.toPx(),
                                0f,
                                0f,
                                ClayPrimary.copy(alpha = 0.40f).toArgb()
                            )
                        }
                    }

                    // Mid concentrated red neon glow
                    val midPaint = Paint().apply {
                        asFrameworkPaint().apply {
                            color = Color.Transparent.toArgb()
                            setShadowLayer(
                                10.dp.toPx(),
                                0f,
                                0f,
                                ClayPrimary.copy(alpha = 0.70f).toArgb()
                            )
                        }
                    }

                    drawIntoCanvas { canvas ->
                        canvas.drawPath(notchedPath, outerPaint)
                        canvas.drawPath(notchedPath, midPaint)
                    }

                    // Neon gradient border along the notch path
                    drawPath(
                        path = notchedPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ClayPrimary,
                                ClayPrimary.copy(alpha = 0.60f),
                                ClayPrimary
                            )
                        ),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
                .clip(cardShape)
                .background(ClaySurface.copy(alpha = 0.93f))
                .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 22.dp)
        ) {
            if (isFrontFace) {
                // Front Face: Login Form
                LoginForm(
                    initialEmail = registeredEmail,
                    onSignUpClick = {
                        signUpStep = SignUpStep.FORM
                        currentMode = AuthScreenMode.SIGN_UP
                    },
                    onLoginClick = onLoginSuccess,
                    onGoogleLoginClick = onGoogleLogin,
                    onForgotPasswordClick = {
                        // Handled or future forgot password
                    }
                )
            } else {
                // Back Face: Rendered rotated by 180° around Y so contents are upright
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = 180f
                        }
                ) {
                    AnimatedContent(
                        targetState = signUpStep,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                        },
                        label = "signup_step_transition"
                    ) { step ->
                        when (step) {
                            SignUpStep.FORM -> {
                                SignUpForm(
                                    onLoginClick = {
                                        currentMode = AuthScreenMode.LOGIN
                                    },
                                    onVerifyClick = { email, pass, _ ->
                                        registeredEmail = email
                                        registeredPassword = pass
                                        scope.launch {
                                            val result = authRepository.requestOtp(email)
                                            otpDispatchResult = result
                                            signUpStep = SignUpStep.OTP
                                        }
                                    },
                                    onGoogleSignUpClick = onGoogleLogin
                                )
                            }
                            SignUpStep.OTP -> {
                                OtpVerificationView(
                                    email = registeredEmail,
                                    otpCode = otpDispatchResult?.otpCode,
                                    isEmailDelivered = otpDispatchResult?.isDeliveredViaEmail == true,
                                    deliveryNote = otpDispatchResult?.deliveryMessage.orEmpty(),
                                    onBackClick = {
                                        signUpStep = SignUpStep.FORM
                                    },
                                    onVerifyOtp = { otpCode, callback ->
                                        scope.launch {
                                            val result = authRepository.verifyOtpAndRegister(
                                                email = registeredEmail,
                                                otp = otpCode,
                                                password = registeredPassword
                                            )
                                            callback(result.isSuccess, result.exceptionOrNull()?.message)
                                        }
                                    },
                                    onResendOtp = {
                                        scope.launch {
                                            val newResult = authRepository.requestOtp(registeredEmail)
                                            otpDispatchResult = newResult
                                        }
                                    },
                                    onVerificationSuccess = {
                                        // Show Bottom Notification Banner: "Registration successful! Welcome to Aurio 🎵"
                                        onRegistrationComplete()
                                        // Flip back to login with prefilled email
                                        currentMode = AuthScreenMode.LOGIN
                                        signUpStep = SignUpStep.FORM
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mascot 3D Logo resting in the top notch, rotating synchronized with the card flip
        if (logoBitmap != null) {
            if (isFrontFace) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "Aurio Logo",
                    modifier = Modifier
                        .size(148.dp)
                        .zIndex(5f)
                        .offset(y = (-118).dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "Aurio Logo",
                    modifier = Modifier
                        .size(148.dp)
                        .zIndex(5f)
                        .offset(y = (-118).dp)
                        .graphicsLayer {
                            rotationY = 180f
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
