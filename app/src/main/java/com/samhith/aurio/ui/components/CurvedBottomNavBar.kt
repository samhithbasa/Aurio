package com.samhith.aurio.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.ui.theme.ClayInactiveIcon
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayPrimaryGradient
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.clayCircle

enum class HomeTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LISTEN_TOGETHER("Listen", Icons.Default.People),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    PROFILE("Profile", Icons.Default.Person)
}

/**
 * Modern Curved Notch Floating Bottom Navigation Bar matching the reference design:
 * - Clean white/clay pill container with rounded corners
 * - Concave scooped notch dipping dynamically at the active tab position
 * - Elevated floating circular active badge sitting inside the scoop
 * - Active label in signature accent color placed beneath the scoop
 * - Clean vertical inactive items with subtle grey typography
 * - Smooth spring morphing slide animation across tabs
 */
@Composable
fun CurvedBottomNavBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = HomeTab.entries
    val barHeight = 68.dp
    val bubbleSize = 50.dp
    val notchWidthDp = 78.dp
    val notchDepthDp = 22.dp
    val cornerRadiusDp = 34.dp

    // Smooth spring morphing slide animation across tabs
    val animatedIndex by animateFloatAsState(
        targetValue = selectedTab.ordinal.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "CurvedNavSlide"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val density = LocalDensity.current
        val barWidthPx = with(density) { maxWidth.toPx() }
        val barHeightPx = with(density) { barHeight.toPx() }
        val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
        val notchWidthPx = with(density) { notchWidthDp.toPx() }
        val notchDepthPx = with(density) { notchDepthDp.toPx() }

        val tabWidth = maxWidth / tabs.size
        val tabWidthPx = barWidthPx / tabs.size
        val activeCenterXPx = tabWidthPx * (animatedIndex + 0.5f)
        val activeCenterXDp = tabWidth * (animatedIndex + 0.5f)

        // 1. Floating Scooped Pill Container (Custom Bezier Path with Shadow & Specular Gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .drawBehind {
                    val path = buildCurvedNavPath(
                        width = size.width,
                        height = size.height,
                        cornerRadius = cornerRadiusPx,
                        activeCenterX = activeCenterXPx,
                        notchWidth = notchWidthPx,
                        notchDepth = notchDepthPx
                    )

                    // Ambient Diffuse Drop Shadow
                    drawIntoCanvas { canvas ->
                        val shadowPaint = Paint().asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(40, 110, 115, 145)
                            maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawPath(path.asAndroidPath(), shadowPaint)
                    }

                    // Solid Soft Clay / Clean White Pill Fill
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF9F8FD),
                                Color(0xFFECEAF4)
                            )
                        )
                    )

                    // Specular 3D Border Outline
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFE4E1EE).copy(alpha = 0.70f),
                                Color(0xFFD0CCE0).copy(alpha = 0.40f)
                            )
                        ),
                        style = Stroke(width = with(density) { 1.2.dp.toPx() })
                    )
                }
        ) {
            // 2. Elevated Floating Circular Active Tab Bubble (Sitting inside the scooped notch)
            Box(
                modifier = Modifier
                    .offset(x = activeCenterXDp - bubbleSize / 2, y = (-15).dp)
                    .size(bubbleSize)
                    .clayCircle(
                        elevation = 10.dp,
                        gradient = ClayPrimaryGradient,
                        shadowColor = Color(0x557A96FE)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val iconScale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "ActiveIconScale"
                )
                Icon(
                    imageVector = selectedTab.icon,
                    contentDescription = selectedTab.label,
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(iconScale)
                )
            }

            // 3. Navigation Items Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    NavBarItem(
                        tab = tab,
                        isSelected = isSelected,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(
    tab: HomeTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isSelected) Arrangement.Bottom else Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(bottom = if (isSelected) 10.dp else 4.dp, top = if (isSelected) 0.dp else 4.dp)
    ) {
        if (!isSelected) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = ClayInactiveIcon,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
        }

        Text(
            text = tab.label,
            color = if (isSelected) ClayPrimary else ClaySecondaryLabel,
            fontSize = if (isSelected) 11.sp else 10.5.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Builds the geometric path for the navbar pill with the scooped concave notch around activeCenterX.
 */
private fun buildCurvedNavPath(
    width: Float,
    height: Float,
    cornerRadius: Float,
    activeCenterX: Float,
    notchWidth: Float,
    notchDepth: Float
): Path {
    val path = Path()
    val r = cornerRadius.coerceAtMost(height / 2f)

    path.moveTo(r, 0f)

    val notchStart = (activeCenterX - notchWidth / 2f).coerceAtLeast(r)
    val notchEnd = (activeCenterX + notchWidth / 2f).coerceAtMost(width - r)

    if (activeCenterX > 0f && notchStart < notchEnd) {
        path.lineTo(notchStart, 0f)

        // Smooth cubic bezier into and out of the concave scooped notch
        val halfW = (notchEnd - notchStart) / 2f
        val cp1x = notchStart + halfW * 0.35f
        val cp1y = 0f
        val cp2x = activeCenterX - halfW * 0.45f
        val cp2y = notchDepth
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, activeCenterX, notchDepth)

        val cp3x = activeCenterX + halfW * 0.45f
        val cp3y = notchDepth
        val cp4x = notchEnd - halfW * 0.35f
        val cp4y = 0f
        path.cubicTo(cp3x, cp3y, cp4x, cp4y, notchEnd, 0f)
    }

    // Top-right line & rounded corner
    path.lineTo(width - r, 0f)
    path.arcTo(
        rect = Rect(width - 2 * r, 0f, width, 2 * r),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // Right edge & bottom-right rounded corner
    path.lineTo(width, height - r)
    path.arcTo(
        rect = Rect(width - 2 * r, height - 2 * r, width, height),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // Bottom edge & bottom-left rounded corner
    path.lineTo(r, height)
    path.arcTo(
        rect = Rect(0f, height - 2 * r, 2 * r, height),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // Left edge & top-left rounded corner
    path.lineTo(0f, r)
    path.arcTo(
        rect = Rect(0f, 0f, 2 * r, 2 * r),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    path.close()
    return path
}
