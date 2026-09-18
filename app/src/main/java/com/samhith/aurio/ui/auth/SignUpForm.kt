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
 * Sign Up Form composable representing the back face of the 3D flipping card.
 */
@Composable
fun SignUpForm(
    onLoginClick: () -> Unit,
    onVerifyClick: (email: String, pass: String, confirmPass: String) -> Unit,
    onGoogleSignUpClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

        Spacer(modifier = Modifier.height(18.dp))

        // Header Titles
        Text(
            text = "Create Account",
            fontSize = 22.sp,
            fontFamily = AurioFontFamily,
            fontWeight = FontWeight.Bold,
            color = AppleLabel,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "Start your high-fidelity music journey",
            fontSize = 12.5.sp,
            fontFamily = AurioFontFamily,
            fontWeight = FontWeight.Normal,
            color = AppleSecondaryLabel,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Email Field
        AurioTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            placeholder = "Email address",
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Password Field
        AurioTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            placeholder = "Password",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
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

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm Password Field
        AurioTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                errorMessage = null
            },
            placeholder = "Confirm Password",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(
                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                        tint = AppleSecondaryLabel,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage!!,
                color = AppleBlue,
                fontSize = 12.sp,
                fontFamily = AurioFontFamily,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Verify / Continue Button
        AurioGradientButton(
            text = "Verify",
            onClick = {
                if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                    errorMessage = "Please fill in all fields"
                } else if (password != confirmPassword) {
                    errorMessage = "Passwords do not match"
                } else {
                    onVerifyClick(email, password, confirmPassword)
                }
            },
            showArrow = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // "OR CONTINUE WITH" Divider
        AurioDividerWithText(text = "OR CONTINUE WITH")

        Spacer(modifier = Modifier.height(12.dp))

        // Google Sign Up Button
        GoogleLoginButton(
            onClick = onGoogleSignUpClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Return to Log In Link Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Already have an account? ",
                color = AppleSecondaryLabel,
                fontSize = 13.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = "Log In",
                color = AppleBlue,
                fontSize = 13.sp,
                fontFamily = AurioFontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onLoginClick)
            )
        }
    }
}
