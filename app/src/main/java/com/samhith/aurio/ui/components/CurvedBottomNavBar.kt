package com.samhith.aurio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.ui.auth.aurioGlow
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.ui.theme.ApplePrimaryGradient
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSurface

enum class HomeTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LISTEN_TOGETHER("Listen Together", Icons.Default.People),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    PROFILE("Profile", Icons.Default.Person)
}

/**
 * Bottom navigation with a cradle: the bar has a notch carved out of its top edge and a raised
 * circle sitting in it, and both glide to whichever tab was tapped.
 *
 * The notch is drawn as one path so the cut-out and the bar are always in step - the earlier
 * version floated a bubble over a flat bar, and its slide animation was never applied.
 */
@Composable
fun CurvedBottomNavBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = HomeTab.entries
    val barHeight = 68.dp
    val bubbleSize = 54.dp
    val overhang = 26.dp // how far the circle rises above the bar

    // The notch and the circle share this position, so they always move together
    val animatedIndex by animateFloatAsState(
        targetValue = selectedTab.ordinal.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "NavCradleSlide"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        val barWidth = maxWidth
        val tabWidth = barWidth / tabs.size
        val cradleCenterX = tabWidth * (animatedIndex + 0.5f)
        val density = LocalDensity.current

        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(overhang))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
            ) {
                // The bar itself, with the notch cut where the selected tab is
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .aurioGlow(color = AppleBlue, alpha = 0.35f, blurRadius = 22.dp, offsetY = 4.dp)
                ) {
                    val path = cradledBarPath(
                        size = size,
                        cornerRadius = with(density) { 28.dp.toPx() },
                        cradleCenterX = with(density) { cradleCenterX.toPx() },
                        cradleRadius = with(density) { (bubbleSize / 2 + 7.dp).toPx() },
                        cradleDepth = with(density) { 20.dp.toPx() }
                    )
                    drawPath(path, color = AppleSurface.copy(alpha = 0.97f))
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            listOf(
                                AppleBlue.copy(alpha = 0.35f),
                                AppleSeparator.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        ),
                        style = Stroke(width = with(density) { 1.2.dp.toPx() })
                    )
                }

                // Labels sit in the bar; the selected tab's icon lives in the circle above it
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    tabs.forEach { tab ->
                        NavBarItem(
                            tab = tab,
                            isSelected = tab == selectedTab,
                            onClick = { onTabSelected(tab) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // The raised circle, riding in the notch
        Box(
            modifier = Modifier
                .offset(x = cradleCenterX - bubbleSize / 2, y = overhang - bubbleSize / 2 + 6.dp)
                .size(bubbleSize)
                .aurioGlow(color = AppleBlue, alpha = 0.55f, blurRadius = 18.dp, offsetY = 3.dp)
                .clip(CircleShape)
                .background(ApplePrimaryGradient)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabSelected(selectedTab) }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Fades between icons as the circle travels, instead of swapping abruptly
            val iconScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "NavIconScale"
            )
            Icon(
                imageVector = selectedTab.icon,
                contentDescription = selectedTab.label,
                tint = AppleOnAccent,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
            )
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
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        content = {
            if (!isSelected) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = AppleGray,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                // Room for the circle sitting in the notch above this label
                Spacer(modifier = Modifier.height(18.dp))
            }

            Text(
                text = tab.label,
                color = if (isSelected) AppleBlue else AppleGray,
                fontSize = if (isSelected) 11.sp else 10.5.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    )
}

/**
 * The bar outline with a rounded top and a dip carved where the circle sits.
 *
 * The dip is two mirrored curves so the bar flows into it smoothly rather than stepping down.
 */
private fun cradledBarPath(
    size: Size,
    cornerRadius: Float,
    cradleCenterX: Float,
    cradleRadius: Float,
    cradleDepth: Float
): Path {
    val path = Path()
    val width = size.width
    val height = size.height
    val transition = cradleRadius * 0.55f
    val left = (cradleCenterX - cradleRadius - transition).coerceAtLeast(cornerRadius)
    val right = (cradleCenterX + cradleRadius + transition).coerceAtMost(width - cornerRadius)

    path.moveTo(0f, height - cornerRadius)
    path.lineTo(0f, cornerRadius)
    path.quadraticTo(0f, 0f, cornerRadius, 0f)
    path.lineTo(left, 0f)

    // Down into the dip, then back up the other side
    path.cubicTo(
        left + transition * 0.6f, 0f,
        cradleCenterX - cradleRadius * 0.85f, cradleDepth,
        cradleCenterX, cradleDepth
    )
    path.cubicTo(
        cradleCenterX + cradleRadius * 0.85f, cradleDepth,
        right - transition * 0.6f, 0f,
        right, 0f
    )

    path.lineTo(width - cornerRadius, 0f)
    path.quadraticTo(width, 0f, width, cornerRadius)
    path.lineTo(width, height - cornerRadius)
    path.quadraticTo(width, height, width - cornerRadius, height)
    path.lineTo(cornerRadius, height)
    path.quadraticTo(0f, height, 0f, height - cornerRadius)
    path.close()
    return path
}
