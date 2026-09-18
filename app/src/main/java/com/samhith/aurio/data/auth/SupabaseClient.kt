package com.samhith.aurio.data.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SupabaseAuthSession(
    val accessToken: String?,
    val userId: String,
    val email: String,
    val name: String
)

class SupabaseClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signUp(email: String, password: String, name: String): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!AuthConfig.isSupabaseConfigured) {
            return@withContext Result.failure(Exception("Supabase URL or Anon key is not configured."))
        }

        try {
            val url = "${AuthConfig.supabaseUrl}/auth/v1/signup"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("data", JSONObject().apply {
                    put("display_name", name)
                    put("full_name", name)
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", AuthConfig.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${AuthConfig.supabaseAnonKey}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val userObj = json.optJSONObject("user") ?: json
                    val userId = userObj.optString("id", "")
                    val userEmail = userObj.optString("email", email)
                    val userMeta = userObj.optJSONObject("user_metadata")
                    val userName = userMeta?.optString("display_name", name) ?: name
                    val accessToken = json.optString("access_token", "")

                    Log.i("SupabaseClient", "Supabase sign up successful: $userId ($userEmail)")
                    return@withContext Result.success(
                        SupabaseAuthSession(
                            accessToken = accessToken.ifBlank { null },
                            userId = userId,
                            email = userEmail,
                            name = userName
                        )
                    )
                } else {
                    Log.w("SupabaseClient", "Supabase sign up failed (${response.code}): $bodyStr")
                    val errorMsg = try {
                        val errJson = JSONObject(bodyStr)
                        errJson.optString("msg", errJson.optString("error_description", "Sign up failed ($bodyStr)"))
                    } catch (e: Exception) {
                        "Sign up failed (${response.code})"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Network exception during Supabase signUp", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun signInWithPassword(email: String, password: String): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!AuthConfig.isSupabaseConfigured) {
            return@withContext Result.failure(Exception("Supabase URL or Anon key is not configured."))
        }

        try {
            val url = "${AuthConfig.supabaseUrl}/auth/v1/token?grant_type=password"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", AuthConfig.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${AuthConfig.supabaseAnonKey}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val accessToken = json.optString("access_token", "")
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id", "") ?: ""
                    val userEmail = userObj?.optString("email", email) ?: email
                    val userMeta = userObj?.optJSONObject("user_metadata")
                    val userName = userMeta?.optString("display_name", "") ?: ""

                    Log.i("SupabaseClient", "Supabase password login success for: $userEmail")
                    return@withContext Result.success(
                        SupabaseAuthSession(
                            accessToken = accessToken,
                            userId = userId,
                            email = userEmail,
                            name = userName.ifBlank { userEmail.substringBefore("@") }
                        )
                    )
                } else {
                    Log.w("SupabaseClient", "Supabase login failed (${response.code}): $bodyStr")
                    val errorMsg = try {
                        val errJson = JSONObject(bodyStr)
                        errJson.optString("error_description", errJson.optString("msg", "Invalid email or password."))
                    } catch (e: Exception) {
                        "Invalid email or password."
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Network exception during Supabase signInWithPassword", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun signInWithIdToken(provider: String, idToken: String): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!AuthConfig.isSupabaseConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured."))
        }

        try {
            val url = "${AuthConfig.supabaseUrl}/auth/v1/token?grant_type=id_token"
            val payload = JSONObject().apply {
                put("provider", provider)
                put("id_token", idToken)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", AuthConfig.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${AuthConfig.supabaseAnonKey}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val accessToken = json.optString("access_token", "")
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id", "") ?: ""
                    val userEmail = userObj?.optString("email", "") ?: ""
                    val userMeta = userObj?.optJSONObject("user_metadata")
                    val userName = userMeta?.optString("full_name", userMeta.optString("name", "")) ?: ""

                    return@withContext Result.success(
                        SupabaseAuthSession(
                            accessToken = accessToken,
                            userId = userId,
                            email = userEmail,
                            name = userName.ifBlank { userEmail.substringBefore("@") }
                        )
                    )
                } else {
                    return@withContext Result.failure(Exception("Supabase ID token exchange failed: $bodyStr"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
