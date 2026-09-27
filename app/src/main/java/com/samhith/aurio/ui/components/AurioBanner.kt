package com.samhith.aurio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.samhith.aurio.ui.theme.AurioFontFamily
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayInset
import com.samhith.aurio.ui.theme.ClayLabel
import com.samhith.aurio.ui.theme.ClaySurface

/**
 * Floating bottom notification pill banner displaying Aurio mascot logo
 * and confirmation message with glowing border.
 */
@Composable
fun AurioBottomBanner(
    message: String,
    isVisible: Boolean,
    logoBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .zIndex(50f)
    ) {
        val shape = RoundedCornerShape(32.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val cornerRadiusPx = 32.dp.toPx()
                    val glowPaint = Paint().apply {
                        asFrameworkPaint().apply {
                            color = Color.Transparent.toArgb()
                            setShadowLayer(
                                20.dp.toPx(),
                                0f,
                                0f,
                                ClayPrimary.copy(alpha = 0.55f).toArgb()
                            )
                        }
                    }
                    drawIntoCanvas { canvas ->
                        canvas.drawRoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            cornerRadiusPx,
                            cornerRadiusPx,
                            glowPaint
                        )
                    }
                }
                .clip(shape)
                .background(ClaySurface.copy(alpha = 0.96f))
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            ClayPrimary,
                            ClayPrimary,
                            ClayPrimary
                        )
                    ),
                    shape = shape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Aurio Logo Thumbnail
                if (logoBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ClayInset),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = logoBitmap,
                            contentDescription = "Aurio Logo",
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Message Text
                Text(
                    text = message,
                    color = ClayLabel,
                    fontSize = 14.sp,
                    fontFamily = AurioFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
