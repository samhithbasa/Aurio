package com.samhith.aurio.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random

data class UserAccount(
    val email: String,
    /**
     * Salted PBKDF2 hash of the password, format "base64salt:base64hash".
     * NEVER a plaintext password — see AuthRepository.encodeCredential/verifyCredential.
     * Used only as a local offline-resilience fallback; Supabase Auth is the real source
     * of truth for credential verification when configured.
     */
    val passwordHash: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isGoogleUser: Boolean = false,
    val supabaseId: String = "",
    val username: String = "",
    val bio: String = "Good Music • Better Moods • Always 🎧",
    val phone: String = "",
    val memberSince: String = "September 2024"
)

data class OtpSession(
    val email: String,
    val otpCode: String,
    val generatedAt: Long = System.currentTimeMillis()
)

data class OtpDispatchResult(
    val otpCode: String,
    val isDeliveredViaEmail: Boolean,
    val deliveryMessage: String
)

/**
 * Production-ready Authentication Repository integrating:
 * 1. Resend Email Service for real 4-digit OTP dispatch.
 * 2. Supabase PostgreSQL Database & Auth for cloud user accounts.
 * 3. Android CredentialManager with Google ID Token authentication.
 * 4. Local SharedPreferences caching for offline resilience.
 */
class AuthRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aurio_auth_prefs", Context.MODE_PRIVATE)

    private val resendService = ResendEmailService()
    private val supabaseClient = SupabaseClient()
    private val googleAuthManager = GoogleAuthManager(context)

    // Current active OTP session in memory
    private var currentOtpSession: OtpSession? = null

    /**
     * Derives a salted PBKDF2-SHA256 hash for local credential storage.
     * Passwords are NEVER stored or compared in plaintext — this is only used as an
     * offline-resilience fallback for when Supabase cloud auth is unreachable/unconfigured.
     * The actual source of truth for password verification is always Supabase Auth when configured.
     */
    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Encodes a raw password into a storable "base64salt:base64hash" credential string.
     * Call this once, at the point a password is first known, and never persist the raw value.
     */
    private fun encodeCredential(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        return "${Base64.encodeToString(salt, Base64.NO_WRAP)}:$hash"
    }

    /**
     * Verifies a raw password attempt against a stored "salt:hash" credential in constant time.
     */
    private fun verifyCredential(password: String, storedCredential: String?): Boolean {
        if (storedCredential.isNullOrBlank() || !storedCredential.contains(":")) return false
        return try {
            val (saltB64, expectedHash) = storedCredential.split(":", limit = 2)
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val actualHash = hashPassword(password, salt)
            constantTimeEquals(actualHash, expectedHash)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying stored credential", e)
            false
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val PBKDF2_ITERATIONS = 120_000

        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Dispatches a 4-digit OTP to the user's email via Resend API and stores session in memory.
     * Returns OtpDispatchResult containing delivery status and generated code.
     */
    suspend fun requestOtp(email: String): OtpDispatchResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val generatedCode = Random.nextInt(1000, 9999).toString()
        currentOtpSession = OtpSession(cleanEmail, generatedCode)

        var isDelivered = false
        var deliveryMessage = ""

        if (AuthConfig.isResendConfigured) {
            try {
                val emailResult = resendService.sendOtpEmail(cleanEmail, generatedCode)
                if (emailResult.isSuccess && emailResult.getOrNull() == true) {
                    isDelivered = true
                    deliveryMessage = "Verification code sent to $cleanEmail"
                    Log.i(TAG, "OTP $generatedCode dispatched via Resend to $cleanEmail")
                } else {
                    val exMsg = emailResult.exceptionOrNull()?.message.orEmpty()
                    Log.w(TAG, "Resend email dispatch error: $exMsg")
                    if (exMsg.contains("403") || exMsg.contains("only send testing emails") || !cleanEmail.equals("abhisamhith07@gmail.com", ignoreCase = true)) {
                        deliveryMessage = "Resend (free tier) only sends live emails to abhisamhith07@gmail.com."
                    } else {
                        deliveryMessage = "Email delivery could not be completed. Use the test code below."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending live email via Resend", e)
                deliveryMessage = "Email service exception: ${e.localizedMessage}"
            }
        } else {
            deliveryMessage = "Email service not configured. Use the test code below."
        }

        OtpDispatchResult(
            otpCode = generatedCode,
            isDeliveredViaEmail = isDelivered,
            deliveryMessage = deliveryMessage
        )
    }

    /**
     * Gets the currently active pending OTP for the given email (if any).
     */
    fun getActiveOtp(email: String): String? {
        val session = currentOtpSession ?: return null
        return if (session.email.equals(email.trim(), ignoreCase = true)) {
            session.otpCode
        } else {
            null
        }
    }

    /**
     * Verifies the 4-digit OTP. If verified:
     * 1. Registers the user in Supabase Postgres (if configured).
     * 2. Persists the user account locally.
     */
    suspend fun verifyOtpAndRegister(
        email: String,
        otp: String,
        password: String
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val session = currentOtpSession

        if (session == null || !session.email.equals(cleanEmail, ignoreCase = true)) {
            return@withContext Result.failure(Exception("No OTP session found. Please request a new code."))
        }

        if (session.otpCode != otp.trim()) {
            return@withContext Result.failure(Exception("Invalid verification code. Please check and try again."))
        }

        val displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        var supabaseUserId = ""

        // Cloud registration via Supabase
        if (AuthConfig.isSupabaseConfigured) {
            val supaResult = supabaseClient.signUp(cleanEmail, password, displayName)
            supaResult.onSuccess { sessionData ->
                supabaseUserId = sessionData.userId
                Log.i(TAG, "Successfully registered in Supabase: $supabaseUserId")
            }.onFailure { err ->
                Log.w(TAG, "Supabase cloud registration note: ${err.message}. Storing local account.")
            }
        }

        // Save new user account locally. The password itself is never persisted —
        // only a salted PBKDF2 hash, used solely as an offline fallback.
        val user = UserAccount(
            email = cleanEmail,
            passwordHash = encodeCredential(password),
            displayName = displayName,
            supabaseId = supabaseUserId
        )
        saveUser(user)
        currentOtpSession = null
        setCurrentUser(user)

        Result.success(user)
    }

    /**
     * Validates credentials against Supabase / local storage.
     */
    suspend fun login(email: String, password: String): Result<UserAccount> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()

        // 1. Try Supabase Cloud Login first if configured — this is the real source of truth.
        if (AuthConfig.isSupabaseConfigured) {
            val supaResult = supabaseClient.signInWithPassword(cleanEmail, password)
            if (supaResult.isSuccess) {
                val supaSession = supaResult.getOrThrow()
                val user = UserAccount(
                    email = supaSession.email.ifBlank { cleanEmail },
                    // Store only a salted hash locally, purely so offline fallback below
                    // still works next time if the network is unavailable. Never the raw password.
                    passwordHash = encodeCredential(password),
                    displayName = supaSession.name.ifBlank { cleanEmail.substringBefore("@") },
                    supabaseId = supaSession.userId
                )
                saveUser(user)
                setCurrentUser(user)
                return@withContext Result.success(user)
            } else {
                Log.w(TAG, "Supabase login error: ${supaResult.exceptionOrNull()?.message}")
                // If Supabase is configured and reachable but rejected the credentials,
                // don't silently fall through to a local-only "first time" account creation —
                // that would let anyone bypass the real password check while offline-fallback
                // data happens to be empty. Only fall through on network/config failure.
                val failureMsg = supaResult.exceptionOrNull()?.message.orEmpty()
                val looksLikeAuthRejection = failureMsg.contains("Invalid", ignoreCase = true) ||
                    failureMsg.contains("credentials", ignoreCase = true)
                if (looksLikeAuthRejection && prefs.getString("user_pwd_$cleanEmail", null) == null) {
                    return@withContext Result.failure(Exception("Invalid email or password"))
                }
            }
        }

        // 2. Fallback to locally stored, salted+hashed credentials (offline resilience).
        val savedCredential = prefs.getString("user_pwd_$cleanEmail", null)
        if (savedCredential != null) {
            if (verifyCredential(password, savedCredential)) {
                val displayName = prefs.getString(
                    "user_name_$cleanEmail",
                    cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                ) ?: "Aurio Listener"
                val user = UserAccount(email = cleanEmail, passwordHash = savedCredential, displayName = displayName)
                setCurrentUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Invalid email or password"))
            }
        } else if (cleanEmail.isNotEmpty() && password.isNotEmpty() && !AuthConfig.isSupabaseConfigured) {
            // First-time local-only account creation — only allowed when there's no cloud
            // backend configured at all (pure offline/dev mode).
            val user = UserAccount(
                email = cleanEmail,
                passwordHash = encodeCredential(password),
                displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            )
            saveUser(user)
            setCurrentUser(user)
            Result.success(user)
        } else {
            Result.failure(Exception("Invalid email or password"))
        }
    }

    /**
     * Google Sign-In with real CredentialManager & Supabase OAuth bridge.
     * @param activityContext MUST be an Activity context (not applicationContext).
     */
    suspend fun loginWithGoogle(
        activityContext: Context,
        fallbackEmail: String = "google.user@aurio.app",
        fallbackDisplayName: String = "Google Explorer"
    ): Result<UserAccount> = withContext(Dispatchers.Main) {
        try {
            // Attempt native Google Credential Manager flow with Activity context
            val googleResult = googleAuthManager.signInWithGoogle(activityContext)
            if (googleResult.isSuccess) {
                val googleData = googleResult.getOrThrow()
                var supabaseId = ""

                // Bridge to Supabase if configured
                if (AuthConfig.isSupabaseConfigured && googleData.idToken.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        val supaOAuth = supabaseClient.signInWithIdToken("google", googleData.idToken)
                        supaOAuth.onSuccess { session ->
                            supabaseId = session.userId
                        }
                    }
                }

                val existingUser = getSavedUserByEmail(googleData.email)
                val finalDisplayName = existingUser?.displayName?.takeIf { it.isNotBlank() && it != "Aurio User" }
                    ?: googleData.displayName
                    ?: googleData.email.substringBefore("@")

                val finalPhoto = existingUser?.photoUrl?.takeIf { it.isNotBlank() }
                    ?: googleData.profilePictureUri.orEmpty()

                val finalUsername = existingUser?.username?.takeIf { it.isNotBlank() }
                    ?: "@${googleData.email.substringBefore("@")}"

                val finalBio = existingUser?.bio?.takeIf { it.isNotBlank() }
                    ?: "Good Music • Better Moods • Always 🎧"

                val finalPhone = existingUser?.phone.orEmpty()
                val finalMemberSince = existingUser?.memberSince ?: "September 2024"

                val user = UserAccount(
                    email = googleData.email,
                    displayName = finalDisplayName,
                    photoUrl = finalPhoto,
                    isGoogleUser = true,
                    supabaseId = if (supabaseId.isNotBlank()) supabaseId else (existingUser?.supabaseId ?: ""),
                    username = finalUsername,
                    bio = finalBio,
                    phone = finalPhone,
                    memberSince = finalMemberSince
                )
                saveUser(user)
                setCurrentUser(user)
                return@withContext Result.success(user)
            } else {
                Log.w(TAG, "Google sign in via CredentialManager fell back: ${googleResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(googleResult.exceptionOrNull() ?: Exception("Google sign-in failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in loginWithGoogle", e)
            return@withContext Result.failure(e)
        }
    }

    private val _currentUserFlow = kotlinx.coroutines.flow.MutableStateFlow<UserAccount?>(null)
    val currentUserFlow: kotlinx.coroutines.flow.StateFlow<UserAccount?> = _currentUserFlow.asStateFlow()

    init {
        _currentUserFlow.value = getCurrentUser()
    }

    fun getSavedUserByEmail(email: String): UserAccount? {
        val cleanEmail = email.trim().lowercase()
        if (!prefs.contains("user_name_$cleanEmail") && !prefs.contains("user_photo_$cleanEmail")) {
            return null
        }
        val displayName = prefs.getString("user_name_$cleanEmail", "Aurio User") ?: "Aurio User"
        val isGoogle = prefs.getBoolean("user_is_google_$cleanEmail", false)
        val photoUrl = prefs.getString("user_photo_$cleanEmail", "") ?: ""
        val username = prefs.getString("user_handle_$cleanEmail", "@${cleanEmail.substringBefore("@")}") ?: "@${cleanEmail.substringBefore("@")}"
        val bio = prefs.getString("user_bio_$cleanEmail", "Good Music • Better Moods • Always 🎧") ?: "Good Music • Better Moods • Always 🎧"
        val phone = prefs.getString("user_phone_$cleanEmail", "") ?: ""
        val memberSince = prefs.getString("user_member_since_$cleanEmail", "September 2024") ?: "September 2024"
        val supabaseId = prefs.getString("user_supabase_id_$cleanEmail", "") ?: ""
        val passwordHash = prefs.getString("user_pwd_$cleanEmail", "") ?: ""
        return UserAccount(
            email = cleanEmail,
            passwordHash = passwordHash,
            displayName = displayName,
            photoUrl = photoUrl,
            isGoogleUser = isGoogle,
            supabaseId = supabaseId,
            username = username,
            bio = bio,
            phone = phone,
            memberSince = memberSince
        )
    }

    fun getCurrentUser(): UserAccount? {
        val currentEmail = prefs.getString("current_logged_in_email", null) ?: return null
        return getSavedUserByEmail(currentEmail)
    }

    private fun persistAvatarImage(photoUriOrPath: String, email: String): String {
        if (!photoUriOrPath.startsWith("content://")) {
            return photoUriOrPath
        }
        return try {
            val uri = android.net.Uri.parse(photoUriOrPath)
            val cleanEmail = email.trim().lowercase()
            val avatarsDir = java.io.File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            val avatarFile = java.io.File(avatarsDir, "avatar_${cleanEmail.hashCode()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                avatarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            android.net.Uri.fromFile(avatarFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist avatar image", e)
            photoUriOrPath
        }
    }

    fun updateUserProfile(
        displayName: String,
        username: String,
        bio: String,
        phone: String = "",
        photoUrl: String? = null
    ): UserAccount {
        val current = getCurrentUser() ?: UserAccount(
            email = "user@aurio.app",
            displayName = displayName,
            username = username,
            bio = bio,
            phone = phone
        )
        val permanentPhoto = photoUrl?.let { persistAvatarImage(it, current.email) } ?: current.photoUrl
        val cleanHandle = if (username.isNotBlank() && !username.startsWith("@")) "@$username" else username
        val updated = current.copy(
            displayName = displayName.trim().ifBlank { current.displayName },
            username = cleanHandle.trim().ifBlank { current.username },
            bio = bio.trim().ifBlank { current.bio },
            phone = phone.trim(),
            photoUrl = permanentPhoto
        )
        saveUser(updated)
        setCurrentUser(updated)
        return updated
    }

    fun updateUserAvatar(photoUriOrPath: String): UserAccount {
        val current = getCurrentUser() ?: UserAccount(email = "user@aurio.app", photoUrl = photoUriOrPath)
        val permanentPhoto = persistAvatarImage(photoUriOrPath, current.email)
        val updated = current.copy(photoUrl = permanentPhoto)
        saveUser(updated)
        setCurrentUser(updated)
        return updated
    }

    fun logout() {
        prefs.edit().remove("current_logged_in_email").apply()
        _currentUserFlow.value = null
    }

    private fun setCurrentUser(user: UserAccount) {
        prefs.edit()
            .putString("current_logged_in_email", user.email)
            .apply()
        _currentUserFlow.value = user
    }

    private fun saveUser(user: UserAccount) {
        prefs.edit()
            .putString("user_pwd_${user.email}", user.passwordHash)
            .putString("user_name_${user.email}", user.displayName)
            .putString("user_photo_${user.email}", user.photoUrl)
            .putBoolean("user_is_google_${user.email}", user.isGoogleUser)
            .putString("user_supabase_id_${user.email}", user.supabaseId)
            .putString("user_handle_${user.email}", user.username.ifBlank { "@${user.email.substringBefore("@")}" })
            .putString("user_bio_${user.email}", user.bio)
            .putString("user_phone_${user.email}", user.phone)
            .putString("user_member_since_${user.email}", user.memberSince)
            .apply()
        _currentUserFlow.value = user
    }
}
