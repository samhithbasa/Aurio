package com.samhith.aurio.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.ui.theme.AurioFontFamily
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSecondaryLabel

/**
 * Login Form composable reproducing the front face of the Aurio card in the reference design.
 */
@Composable
fun LoginForm(
    onSignUpClick: () -> Unit,
    onLoginClick: (String, String) -> Unit,
    onGoogleLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    initialEmail: String = "",
    modifier: Modifier = Modifier
) {
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

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

        Spacer(modifier = Modifier.height(24.dp))

        // "Welcome Back" Header
        Text(
            text = "Welcome Back",
            fontSize = 24.sp,
            fontFamily = AurioFontFamily,
            fontWeight = FontWeight.Bold,
            color = AppleLabel,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle
        Text(
            text = "Sign in to continue your music journey",
            fontSize = 13.sp,
            fontFamily = AurioFontFamily,
            fontWeight = FontWeight.Normal,
            color = AppleSecondaryLabel,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Email Field
        AurioTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email address",
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password Field
        AurioTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Options Row (Remember me + Forgot Password)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { rememberMe = !rememberMe }
            ) {
                AurioCheckbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it }
                )
                Text(
                    text = "Remember me",
                    color = AppleLabel,
                    fontSize = 13.sp,
                    fontFamily = AurioFontFamily,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Text(
                text = "Forgot Password?",
                color = AppleBlue,
                fontSize = 13.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onForgotPasswordClick)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Log In Gradient Button
        AurioGradientButton(
            text = "Log In",
            onClick = { onLoginClick(email, password) },
            showArrow = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // "OR CONTINUE WITH" Divider
        AurioDividerWithText(text = "OR CONTINUE WITH")

        Spacer(modifier = Modifier.height(18.dp))

        // Google Login Only (Apple and Spotify removed as requested)
        GoogleLoginButton(
            onClick = onGoogleLoginClick
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Sign Up Link Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Don't have an account? ",
                color = AppleSecondaryLabel,
                fontSize = 13.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = "Sign Up",
                color = AppleBlue,
                fontSize = 13.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onSignUpClick)
            )
        }
    }
}
