package com.samhith.aurio.data.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ResendEmailService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun sendOtpEmail(toEmail: String, otp: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!AuthConfig.isResendConfigured) {
            Log.w("ResendEmailService", "Resend API key not configured, skipping live email dispatch.")
            return@withContext Result.success(false)
        }

        val htmlContent = buildOtpEmailHtml(otp)

        // Webmail domains cannot be verified as senders in Resend free tier.
        // Use onboarding@resend.dev directly if sender is a webmail address or not configured.
        val isWebmail = AuthConfig.senderEmail.contains("@gmail.", ignoreCase = true) ||
            AuthConfig.senderEmail.contains("@yahoo.", ignoreCase = true) ||
            AuthConfig.senderEmail.contains("@outlook.", ignoreCase = true) ||
            AuthConfig.senderEmail.contains("@hotmail.", ignoreCase = true) ||
            AuthConfig.senderEmail.contains("@icloud.", ignoreCase = true)

        val sendersToTry = if (isWebmail || AuthConfig.senderEmail.isBlank()) {
            listOf("onboarding@resend.dev")
        } else {
            listOf(AuthConfig.senderEmail, "onboarding@resend.dev").distinct()
        }

        var lastException: Exception? = null

        for (fromSender in sendersToTry) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("from", if (fromSender.contains("@")) "Aurio <$fromSender>" else fromSender)
                    put("to", JSONArray().put(toEmail))
                    put("subject", "Your Aurio Verification Code: $otp")
                    put("html", htmlContent)
                }

                val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://api.resend.com/emails")
                    .addHeader("Authorization", "Bearer ${AuthConfig.resendApiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.i("ResendEmailService", "OTP email successfully sent to $toEmail from $fromSender. Response: $responseBody")
                        return@withContext Result.success(true)
                    } else {
                        Log.w("ResendEmailService", "Failed to send email from $fromSender: HTTP ${response.code} -> $responseBody")
                        lastException = Exception("HTTP ${response.code}: $responseBody")
                    }
                }
            } catch (e: Exception) {
                Log.e("ResendEmailService", "Error during Resend dispatch from $fromSender", e)
                lastException = e
            }
        }

        return@withContext Result.failure(lastException ?: Exception("Failed to send email via Resend"))
    }

    private fun buildOtpEmailHtml(otp: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background-color: #0B0914;
                        color: #FFFFFF;
                        margin: 0;
                        padding: 40px 20px;
                    }
                    .container {
                        max-width: 500px;
                        margin: 0 auto;
                        background: linear-gradient(145deg, #1C152B 0%, #110D1D 100%);
                        border-radius: 24px;
                        border: 1px solid rgba(255, 45, 85, 0.3);
                        padding: 36px;
                        text-align: center;
                        box-shadow: 0 12px 36px rgba(0, 0, 0, 0.6);
                    }
                    .logo-badge {
                        display: inline-block;
                        font-size: 32px;
                        font-weight: 900;
                        letter-spacing: 4px;
                        background: linear-gradient(135deg, #FF2D55, #FF6B8B);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        margin-bottom: 8px;
                    }
                    .tagline {
                        font-size: 13px;
                        letter-spacing: 2px;
                        color: #FF6B8B;
                        text-transform: uppercase;
                        margin-bottom: 24px;
                    }
                    .title {
                        font-size: 22px;
                        font-weight: 700;
                        color: #FFFFFF;
                        margin-bottom: 12px;
                    }
                    .desc {
                        font-size: 14px;
                        color: rgba(255, 255, 255, 0.7);
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }
                    .otp-box {
                        display: inline-block;
                        padding: 16px 36px;
                        background: rgba(255, 45, 85, 0.12);
                        border: 2px dashed #FF2D55;
                        border-radius: 16px;
                        font-size: 36px;
                        font-weight: 900;
                        letter-spacing: 12px;
                        color: #FF6B8B;
                        margin-bottom: 28px;
                    }
                    .footer {
                        font-size: 12px;
                        color: rgba(255, 255, 255, 0.4);
                        border-top: 1px solid rgba(255, 255, 255, 0.1);
                        padding-top: 20px;
                        margin-top: 10px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="logo-badge">AURIO</div>
                    <div class="tagline">Sonic Dimension</div>
                    <div class="title">Verification Code</div>
                    <div class="desc">
                        Use the code below to complete your Aurio registration and unlock your high-fidelity music universe.
                    </div>
                    <div class="otp-box">$otp</div>
                    <div class="desc" style="font-size: 12px; margin-bottom: 0;">
                        This code will expire in <strong>5 minutes</strong>. If you didn't request this code, you can safely ignore this email.
                    </div>
                    <div class="footer">
                        &copy; 2026 Aurio. All rights reserved.
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
