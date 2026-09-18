package com.samhith.aurio.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GoogleUserData(
    val idToken: String,
    val email: String,
    val displayName: String?,
    val profilePictureUri: String?
)

class GoogleAuthManager(context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Launches Google Sign-In via Android CredentialManager.
     * IMPORTANT: activityContext MUST be an Activity (not applicationContext),
     * because CredentialManager needs it to launch the account picker UI.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<GoogleUserData> = withContext(Dispatchers.Main) {
        val serverClientId = AuthConfig.googleWebClientId

        if (serverClientId.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Google Web Client ID is not configured in local.properties.")
            )
        }

        // Verify we have an Activity context
        val activity = when (activityContext) {
            is Activity -> activityContext
            else -> {
                Log.e("GoogleAuthManager", "Context is not an Activity! CredentialManager requires an Activity context.")
                return@withContext Result.failure(
                    IllegalStateException("Google Sign-In requires an Activity context. Please try again.")
                )
            }
        }

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                
                val userData = GoogleUserData(
                    idToken = googleIdTokenCredential.idToken,
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName,
                    profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()
                )

                Log.i("GoogleAuthManager", "Google sign in successful for: ${userData.email}")
                return@withContext Result.success(userData)
            } else {
                return@withContext Result.failure(
                    IllegalStateException("Unexpected credential type returned: ${credential.type}")
                )
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("GoogleAuthManager", "CredentialManager cancellation: ${e.message}", e)
            return@withContext Result.failure(
                Exception("Google Sign-In was cancelled or not authorized for this account yet.")
            )
        } catch (e: GetCredentialException) {
            Log.e("GoogleAuthManager", "GetCredentialException during Google sign in: ${e.message}", e)
            return@withContext Result.failure(
                Exception("Google Sign-In error: ${e.message ?: "Authentication failed"}")
            )
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Unexpected error during Google sign in", e)
            return@withContext Result.failure(e)
        }
    }
}
