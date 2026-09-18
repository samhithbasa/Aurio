package com.samhith.aurio.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.ui.theme.AurioFontFamily
import kotlinx.coroutines.delay
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleBlueLight

/**
 * Splash Screen featuring:
 * - Aurio color palette background gradient with vibrant ambient glow
 * - 3D Mascot Logo in the center
 * - Concentric pulsing audio soundwaves radiating outward
 * - Glowing Aurio branding tagline
 */
@Composable
fun SplashScreen(
    logoBitmap: ImageBitmap?,
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto transition to main app after 2.4s
    LaunchedEffect(Unit) {
        delay(2400)
        onSplashFinished()
    }

    // Logo intro scale and alpha
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
    }

    // Continuous soundwave pulsing animations (3 staggered concentric waves)
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_waves")

    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_1_scale"
    )
    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_1_alpha"
    )

    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_2_scale"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_2_alpha"
    )

    val wave3Scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_3_scale"
    )
    val wave3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_3_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AppleBlue.copy(alpha = 0.12f),
                        AppleBlue.copy(alpha = 0.12f),
                        AppleBackground,
                        AppleBackground
                    ),
                    radius = 900f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSplashFinished() },
        contentAlignment = Alignment.Center
    ) {
        // Soundwave Canvas
        Canvas(
            modifier = Modifier.size(280.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.38f

            // Wave 1
            drawCircle(
                color = AppleBlue.copy(alpha = wave1Alpha),
                radius = baseRadius * wave1Scale,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Wave 2
            drawCircle(
                color = AppleBlue.copy(alpha = wave2Alpha),
                radius = baseRadius * wave2Scale,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Wave 3
            drawCircle(
                color = AppleBlueLight.copy(alpha = wave3Alpha),
                radius = baseRadius * wave3Scale,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center Mascot Logo & Branding Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(logoScale.value)
                .padding(bottom = 20.dp)
        ) {
            if (logoBitmap != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(170.dp)
                ) {
                    Image(
                        bitmap = logoBitmap,
                        contentDescription = "Aurio Logo",
                        modifier = Modifier.size(160.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Glowing "Aurio" Title
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
                fontSize = 42.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "M U S I C   M A K E S   A   B E T T E R   Y O U",
                fontSize = 11.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.5.sp,
                color = AppleLabel,
                textAlign = TextAlign.Center
            )
        }
    }
}
