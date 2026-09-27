package com.samhith.aurio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 3D Claymorphic Card Modifier.
 * Produces an inflated, puffy matte clay surface with:
 * - Dual layered soft ambient and directional drop shadows
 * - Top-left light catch highlight
 * - Smooth clay surface gradient
 */
fun Modifier.clayCard(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 8.dp,
    gradient: Brush = ClayCardGradient,
    backgroundColor: Color? = null,
    borderAlpha: Float = 0.90f
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        spotColor = Color(0x388286A4),
        ambientColor = Color(0x1E6A6E88)
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor?.let { Brush.linearGradient(listOf(it, it)) } ?: gradient)
    .border(
        width = 1.2.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha),
                Color(0xFFE4E1EE).copy(alpha = 0.65f * borderAlpha),
                Color(0xFFD0CCE0).copy(alpha = 0.30f * borderAlpha)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
    .drawWithContent {
        drawContent()
        // Top-left diffuse clay highlight sheen
        val highlightHeight = size.height * 0.45f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = highlightHeight
            ),
            size = Size(size.width, highlightHeight),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }

/**
 * 3D Claymorphic Pill Modifier (for category filters, chips, toggle bars).
 * - Selected: Vibrant inflated Periwinkle Clay with soft colored glow
 * - Unselected: Soft White 3D Clay with subtle drop shadow
 */
fun Modifier.clayPill(
    isSelected: Boolean = true,
    cornerRadius: Dp = 100.dp,
    elevation: Dp = if (isSelected) 8.dp else 4.dp,
    selectedGradient: Brush = ClayPrimaryGradient,
    unselectedGradient: Brush = ClayCardGradient
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        spotColor = if (isSelected) Color(0x407A96FE) else Color(0x288488A6),
        ambientColor = Color(0x1460647C)
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(if (isSelected) selectedGradient else unselectedGradient)
    .border(
        width = 1.2.dp,
        brush = if (isSelected) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color(0xFF5A74E8).copy(alpha = 0.40f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color(0xFFD8D5E6).copy(alpha = 0.50f)
                )
            )
        },
        shape = RoundedCornerShape(cornerRadius)
    )
    .drawWithContent {
        drawContent()
        val shineHeight = size.height * 0.50f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isSelected) 0.50f else 0.40f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = shineHeight
            ),
            size = Size(size.width, shineHeight),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }

/**
 * 3D Claymorphic Button (for CTAs like "Create Room", "Join", "Import").
 */
fun Modifier.clayButton(
    gradient: Brush = ClayPrimaryGradient,
    cornerRadius: Dp = 100.dp,
    elevation: Dp = 7.dp,
    backgroundColor: Color? = null
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        spotColor = Color(0x457A96FE),
        ambientColor = Color(0x205A74E8)
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor?.let { Brush.linearGradient(listOf(it, it)) } ?: gradient)
    .border(
        width = 1.2.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.90f),
                Color(0xFF5A74E8).copy(alpha = 0.45f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
    .drawWithContent {
        drawContent()
        val shineH = size.height * 0.48f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f),
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = shineH
            ),
            size = Size(size.width, shineH),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }

/**
 * 3D Claymorphic Circle Modifier (for circular icon buttons, avatars, bubble indicators).
 */
fun Modifier.clayCircle(
    elevation: Dp = 6.dp,
    gradient: Brush = ClayCardGradient,
    backgroundColor: Color? = null,
    shadowColor: Color = Color(0x358488A6)
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = CircleShape,
        spotColor = shadowColor,
        ambientColor = Color(0x1860647C)
    )
    .clip(CircleShape)
    .background(backgroundColor?.let { Brush.linearGradient(listOf(it, it)) } ?: gradient)
    .border(
        width = 1.2.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                Color(0xFFD8D5E6).copy(alpha = 0.45f)
            )
        ),
        shape = CircleShape
    )
    .drawWithContent {
        drawContent()
        val shineH = size.height * 0.48f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = shineH
            ),
            size = Size(size.width, shineH),
            cornerRadius = CornerRadius(size.width / 2, size.width / 2)
        )
    }

/**
 * Debossed / Inset Clay Trough Modifier (for Search Bar, Input Fields, Track Troughs).
 * Creates the pressed-in clay effect with inner soft shadow and crisp rounded contour.
 */
fun Modifier.clayInset(
    cornerRadius: Dp = 100.dp,
    backgroundColor: Color = ClayInset
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color(0xFFD2CFDE).copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.85f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
    .drawWithContent {
        drawContent()
        // Top-inner shadow (pressed-in trough impression)
        val innerShadowHeight = size.height * 0.35f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x18000000),
                    Color(0x06000000),
                    Color.Transparent
                ),
                startY = 0f,
                endY = innerShadowHeight
            ),
            size = Size(size.width, innerShadowHeight),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }

/**
 * Soft Ambient Colored Glow for Clay Elements.
 */
fun Modifier.clayGlow(
    color: Color = ClayPrimary,
    alpha: Float = 0.30f,
    blurRadius: Dp = 16.dp,
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
