package com.samhith.aurio.data.auth

import com.samhith.aurio.BuildConfig

object AuthConfig {
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val resendApiKey: String = BuildConfig.RESEND_API_KEY
    val senderEmail: String = BuildConfig.SENDER_EMAIL.ifBlank { "onboarding@resend.dev" }
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    val isResendConfigured: Boolean
        get() = resendApiKey.isNotBlank()

    val isGoogleAuthConfigured: Boolean
        get() = googleWebClientId.isNotBlank()
}
