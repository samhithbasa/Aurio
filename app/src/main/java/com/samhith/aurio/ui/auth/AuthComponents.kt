package com.samhith.aurio.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.samhith.aurio.R
import com.samhith.aurio.ui.theme.AurioFontFamily
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.ui.theme.ApplePrimaryGradient

/**
 * Custom styled input field matching the Aurio reference design.
 */
@Composable
fun AurioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) AppleBlue.copy(alpha = 0.9f) else AppleFill,
        animationSpec = tween(durationMillis = 200),
        label = "border_color"
    )

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(AppleFill)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) AppleBlue else AppleSecondaryLabel,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = AppleGray,
                        fontSize = 15.sp,
                        fontFamily = AurioFontFamily,
                        fontWeight = FontWeight.Normal
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = AppleLabel,
                        fontSize = 15.sp,
                        fontFamily = AurioFontFamily,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(AppleBlue),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine
                )
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Custom checkbox component matching the Crimson styled checkbox in the reference image.
 */
@Composable
fun AurioCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) AppleBlue else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "checkbox_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) AppleBlue else AppleGray,
        animationSpec = tween(durationMillis = 180),
        label = "checkbox_border"
    )

    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.2.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Checked",
                tint = AppleLabel,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

/**
 * Gradient action button with soft glowing crimson drop shadow.
 */
@Composable
fun AurioGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = true
) {
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .aurioGlow(
                color = AppleBlue,
                alpha = if (enabled) 0.55f else 0.15f,
                blurRadius = 24.dp,
                offsetY = 4.dp
            )
            .clip(shape)
            .background(
                brush = if (enabled) ApplePrimaryGradient else Brush.horizontalGradient(
                    listOf(AppleBlue.copy(alpha = 0.5f), AppleBlue.copy(alpha = 0.5f))
                )
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = AppleOnAccent)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = AppleOnAccent,
                fontSize = 16.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )

            if (showArrow) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = AppleOnAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Divider with text in the middle ("OR CONTINUE WITH").
 */
@Composable
fun AurioDividerWithText(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = AppleFill
        )
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp),
            color = AppleSecondaryLabel,
            fontSize = 11.sp,
            fontFamily = AurioFontFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = AppleFill
        )
    }
}

/**
 * Google social login button (circular with official multicolored 'G' logo).
 */
@Composable
fun GoogleLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(shape)
            .background(AppleSurface)
            .border(
                width = 1.dp,
                color = AppleFill,
                shape = shape
            )
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = AppleBlue)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_google_logo),
            contentDescription = "Google Sign In",
            tint = Color.Unspecified,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Custom modifier for glowing drop shadow effect.
 */
fun Modifier.aurioGlow(
    color: Color,
    alpha: Float = 0.5f,
    blurRadius: Dp = 20.dp,
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
 * Creates the exact vector path for the Aurio card with the top center U-notch cutout.
 */
fun createNotchedCardPath(
    size: Size,
    cornerRadius: Float,
    notchWidth: Float,
    notchDepth: Float
): Path {
    val path = Path()
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cr = cornerRadius.coerceAtMost(w / 4f).coerceAtMost(h / 4f)
    val nw = notchWidth.coerceAtMost(w - 2 * cr)
    val nd = notchDepth.coerceAtMost(h / 3f)

    val notchLeft = cx - nw / 2f
    val notchRight = cx + nw / 2f

    // 1. Top-left start after corner arc
    path.moveTo(cr, 0f)

    // 2. Line to left edge of notch
    path.lineTo(notchLeft, 0f)

    // 3. Left smooth S-curve down to notch base (cx, nd)
    val controlOffset = nw * 0.24f
    path.cubicTo(
        x1 = notchLeft + controlOffset,
        y1 = 0f,
        x2 = cx - controlOffset,
        y2 = nd,
        x3 = cx,
        y3 = nd
    )

    // 4. Right smooth S-curve up to notch right edge (notchRight, 0f)
    path.cubicTo(
        x1 = cx + controlOffset,
        y1 = nd,
        x2 = notchRight - controlOffset,
        y2 = 0f,
        x3 = notchRight,
        y3 = 0f
    )

    // 5. Line to top-right corner
    path.lineTo(w - cr, 0f)

    // 6. Top-right corner arc
    path.arcTo(
        rect = Rect(w - 2 * cr, 0f, w, 2 * cr),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // 7. Right edge
    path.lineTo(w, h - cr)

    // 8. Bottom-right corner arc
    path.arcTo(
        rect = Rect(w - 2 * cr, h - 2 * cr, w, h),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // 9. Bottom edge
    path.lineTo(cr, h)

    // 10. Bottom-left corner arc
    path.arcTo(
        rect = Rect(0f, h - 2 * cr, 2 * cr, h),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // 11. Left edge
    path.lineTo(0f, cr)

    // 12. Top-left corner arc
    path.arcTo(
        rect = Rect(0f, 0f, 2 * cr, 2 * cr),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    path.close()
    return path
}

/**
 * Shape of the Aurio card featuring a top center U-notch cutout where the 3D logo rests.
 */
class NotchedCardShape(
    val cornerRadius: Dp = 36.dp,
    val notchWidth: Dp = 136.dp,
    val notchDepth: Dp = 46.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val crPx = with(density) { cornerRadius.toPx() }
        val nwPx = with(density) { notchWidth.toPx() }
        val ndPx = with(density) { notchDepth.toPx() }
        val path = createNotchedCardPath(size, crPx, nwPx, ndPx)
        return Outline.Generic(path)
    }
}
