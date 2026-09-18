package com.samhith.aurio.ui.network

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSecondaryLabel

private val OfflineBackground = AppleBackground
private val OfflineCrimson = AppleBlue
private val OfflineCoral = AppleBlue
private val OfflineWhite = AppleLabel

private fun Modifier.buttonGlow(
    color: Color = OfflineCrimson,
    alpha: Float = 0.60f,
    blurRadius: Dp = 26.dp,
    offsetY: Dp = 4.dp
): Modifier = this.drawBehind {
    val transparentColor = color.copy(alpha = 0f).toArgb()
    val shadowColor = color.copy(alpha = alpha).toArgb()
    this.drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = transparentColor
        frameworkPaint.setShadowLayer(
            blurRadius.toPx(),
            0f,
            offsetY.toPx(),
            shadowColor
        )
        it.drawRoundRect(
            0f,
            0f,
            this.size.width,
            this.size.height,
            this.size.height / 2,
            this.size.height / 2,
            paint
        )
    }
}

/**
 * Pixel-perfect No Network Screen matching the Aurio reference design:
 * - Full-screen background artwork (assets/No_Netwrok_Bg.png)
 * - Top Left: Aurio Mascot Logo
 * - Top Right: "MUSIC ALWAYS WITH YOU" text with red accent line
 * - Title & Subtitle placed cleanly in the lower half below the character's feet
 * - Primary Button: Glowing pill "Try Again" with rotating refresh icon
 * - Bottom Tagline: "—  GOOD MUSIC WAITS  —"
 */
@Composable
fun NoNetworkScreen(
    onRetryClick: () -> Unit,
    logoBitmap: ImageBitmap?,
    onGoToLibraryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    val rotationAnim = remember { Animatable(0f) }

    // Load No Network full background asset
    val bgIllustrationBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("No_Netwrok_Bg.png").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun handleRetry() {
        if (isChecking) return
        scope.launch {
            isChecking = true
            rotationAnim.snapTo(0f)
            rotationAnim.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
            onRetryClick()
            delay(400)
            isChecking = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OfflineBackground)
    ) {
        // Layer 1: Fullscreen Background Artwork
        if (bgIllustrationBitmap != null) {
            Image(
                bitmap = bgIllustrationBitmap,
                contentDescription = "No Network Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Layer 2: Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Top-Left: Aurio Logo
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "Aurio Logo",
                    modifier = Modifier
                        .height(38.dp)
                        .width(96.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Top-Right: "MUSIC ALWAYS WITH YOU" + Coral Underline
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "MUSIC\nALWAYS\nWITH YOU",
                    color = AppleLabel,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(OfflineCoral)
                )
            }
        }

        // Layer 3: Bottom Content & Interactive Controls Section (Anchored below character)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- "No Network" Title ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No ",
                    color = OfflineWhite,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
                Text(
                    text = "Network",
                    color = OfflineCoral,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- Subtitle Description ---
            Text(
                text = "Looks like you're offline. Check your internet\nconnection and try again.",
                color = AppleSecondaryLabel,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- Primary Action Button: "Try Again" ---
            val buttonShape = RoundedCornerShape(32.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(56.dp)
                    .buttonGlow(
                        color = OfflineCrimson,
                        alpha = 0.60f,
                        blurRadius = 26.dp,
                        offsetY = 4.dp
                    )
                    .clip(buttonShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AppleBlue,
                                AppleBlue
                            )
                        )
                    )
                    .clickable(
                        onClick = { handleRetry() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = OfflineWhite)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = OfflineWhite,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotationAnim.value)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = if (isChecking) "Checking..." else "Try Again",
                        color = OfflineWhite,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Secondary Action Button: "Go to Offline Library" ---
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(52.dp)
                    .clip(buttonShape)
                    .background(AppleFill)
                    .border(
                        width = 1.dp,
                        color = AppleFill,
                        shape = buttonShape
                    )
                    .clickable(
                        onClick = onGoToLibraryClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = OfflineWhite)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadForOffline,
                        contentDescription = "Go to Offline Library",
                        tint = OfflineCoral,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Go to Offline Library",
                        color = OfflineWhite,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- Bottom Tagline: "—  GOOD MUSIC WAITS  —" ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(OfflineCoral.copy(alpha = 0.85f))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "GOOD MUSIC WAITS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.6.sp,
                    color = AppleSecondaryLabel
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(OfflineCoral.copy(alpha = 0.85f))
                )
            }
        }
    }
}
