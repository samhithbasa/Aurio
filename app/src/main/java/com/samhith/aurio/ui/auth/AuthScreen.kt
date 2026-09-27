package com.samhith.aurio.ui.auth

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.samhith.aurio.data.auth.AuthRepository
import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.ui.theme.AurioFontFamily
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch
import com.samhith.aurio.ui.theme.ClayBackground
import com.samhith.aurio.ui.theme.ClaySecondaryLabel
import com.samhith.aurio.ui.theme.ClayPrimary
import com.samhith.aurio.ui.theme.ClayPrimaryLight
import com.samhith.aurio.ui.theme.ClayPrimaryGradient

/**
 * Main Auth Screen assembling:
 * - Background studio image (assets/Login_Background.png)
 * - Ambient text overlays in cross pattern
 * - 3D Flipping Glowing Glass Card with integrated Mascot Logo
 * - "STREAM • DISCOVER • BELONG" footer with neon bottom bar
 */
@Composable
fun AuthScreen(
    onLoginSuccess: (UserAccount) -> Unit,
    onGoogleLoginSuccess: (UserAccount) -> Unit,
    onRegistrationSuccess: () -> Unit,
    logoBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Load background asset
    val backgroundBitmap: ImageBitmap? = remember {
        try {
            context.assets.open("Login_Background.png").use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClayBackground)
    ) {
        // Layer 1: Background Studio Image
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Layer 2: Ambient Text Overlays in CROSS pattern
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Top-Left: "Feel Every Beat ♡" — larger text, rotated diagonally
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 50.dp, start = 2.dp)
                    .rotate(-18f)
            ) {
                Text(
                    text = "Feel",
                    fontSize = 28.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    color = ClayPrimaryLight.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Cursive
                )
                Text(
                    text = "Every",
                    fontSize = 30.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    color = ClayPrimaryLight.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Cursive
                )
                Text(
                    text = "Beat",
                    fontSize = 34.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    color = ClayPrimary.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Cursive
                )
                Text(
                    text = "♡",
                    fontSize = 26.sp,
                    color = ClayPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                )
            }

            // Top-Right: "GOOD MUSIC BRIGHTER DAYS" — larger text, rotated opposite diagonal
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 70.dp, end = 2.dp)
                    .rotate(12f),
                horizontalAlignment = Alignment.End
            ) {
                val ambientWords = listOf("GOOD", "MUSIC", "BRIGHTER", "DAYS")
                ambientWords.forEach { word ->
                    Text(
                        text = word,
                        fontSize = 15.sp,
                        fontFamily = AurioFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = ClaySecondaryLabel.copy(alpha = 0.75f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }

        // Layer 3: Scrollable content — fills screen, card positioned from upper-mid
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dynamic spacer — pushes card down just enough for background to show
            Spacer(modifier = Modifier.height(230.dp))

            // Auth Card Container with integrated top notch & rotating 3D mascot logo
            AuthCardContainer(
                onLoginSuccess = { email, pass ->
                    scope.launch {
                        val result = authRepository.login(email, pass)
                        if (result.isSuccess) {
                            onLoginSuccess(result.getOrNull()!!)
                        } else {
                            Toast.makeText(
                                context,
                                result.exceptionOrNull()?.message ?: "Login failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onGoogleLogin = {
                    scope.launch {
                        val result = authRepository.loginWithGoogle(activityContext = context)
                        if (result.isSuccess) {
                            onGoogleLoginSuccess(result.getOrNull()!!)
                        } else {
                            val errorMsg = result.exceptionOrNull()?.message.orEmpty()
                            if (!errorMsg.contains("cancelled", ignoreCase = true)) {
                                Toast.makeText(
                                    context,
                                    errorMsg.ifBlank { "Google sign in could not be completed" },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                onRegistrationComplete = {
                    onRegistrationSuccess()
                },
                logoBitmap = logoBitmap,
                modifier = Modifier.fillMaxWidth(0.90f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Footer Text: "STREAM • DISCOVER • BELONG"
            Text(
                text = "STREAM     •     DISCOVER     •     BELONG",
                fontSize = 10.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.8.sp,
                color = ClaySecondaryLabel,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Gradient Pill Bar
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ClayPrimaryGradient)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
