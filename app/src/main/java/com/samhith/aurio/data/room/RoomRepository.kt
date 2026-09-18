package com.samhith.aurio.data.room

import android.util.Log
import com.samhith.aurio.data.auth.AuthConfig
import com.samhith.aurio.data.auth.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Repository for Listen Together rooms, participant presence, and real-time messaging.
 * Uses Supabase PostgREST endpoints with in-memory fallback resilience.
 */
class RoomRepository private constructor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    private val TAG = "RoomRepository"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Resilient in-memory local state cache
    private val localRooms = ConcurrentHashMap<String, Room>()
    private val localParticipants = ConcurrentHashMap<String, MutableList<RoomParticipant>>()
    private val localMessages = ConcurrentHashMap<String, MutableList<RoomMessage>>()
    private val localRequests = ConcurrentHashMap<String, MutableList<SongRequest>>()

    // Persisted reactive stream of user created rooms across screen transitions
    private val _userCreatedRooms = MutableStateFlow<List<Room>>(emptyList())
    val userCreatedRooms: StateFlow<List<Room>> = _userCreatedRooms.asStateFlow()

    init {
        // Pre-populate with default seed rooms if needed
        MockRoomData.popularRooms.forEach { room ->
            localRooms[room.id] = room
            localParticipants[room.id] = MockRoomData.sampleParticipants.toMutableList()
            localMessages[room.id] = MockRoomData.sampleMessages.toMutableList()
        }
    }

    private fun getRestUrl(table: String): String {
        return "${AuthConfig.supabaseUrl}/rest/v1/$table"
    }

    private fun getHeaders(): Map<String, String> {
        return mapOf(
            "apikey" to AuthConfig.supabaseAnonKey,
            "Authorization" to "Bearer ${AuthConfig.supabaseAnonKey}",
            "Content-Type" to "application/json"
        )
    }

    /**
     * Fetches all active public rooms sorted by most recent.
     */
    suspend fun getPublicRooms(): List<Room> = withContext(Dispatchers.IO) {
        if (!AuthConfig.isSupabaseConfigured) {
            return@withContext localRooms.values.filter { it.isPublic }.toList()
        }

        try {
            val url = "${getRestUrl("rooms")}?is_public=eq.true&order=created_at.desc"
            val reqBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            client.newCall(reqBuilder.build()).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful) {
                    val arr = JSONArray(body)
                    val rooms = mutableListOf<Room>()
                    for (i in 0 until arr.length()) {
                        val room = Room.fromJson(arr.getJSONObject(i))
                        rooms.add(room)
                        localRooms[room.id] = room
                    }
                    if (rooms.isNotEmpty()) return@withContext rooms
                } else {
                    Log.w(TAG, "getPublicRooms remote returned ${res.code}: $body")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching public rooms from Supabase", e)
        }

        return@withContext localRooms.values.filter { it.isPublic }.toList()
    }

    /**
     * Fetches all rooms hosted by a specific user.
     */
    suspend fun getUserRooms(userId: String): List<Room> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext emptyList()
        if (!AuthConfig.isSupabaseConfigured) {
            return@withContext localRooms.values.filter { it.hostId == userId }.toList()
        }

        try {
            val url = "${getRestUrl("rooms")}?host_id=eq.$userId&order=created_at.desc"
            val reqBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            client.newCall(reqBuilder.build()).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful) {
                    val arr = JSONArray(body)
                    val rooms = mutableListOf<Room>()
                    for (i in 0 until arr.length()) {
                        val room = Room.fromJson(arr.getJSONObject(i))
                        rooms.add(room)
                        localRooms[room.id] = room
                    }
                    _userCreatedRooms.value = (rooms + _userCreatedRooms.value).distinctBy { it.id }
                    return@withContext rooms
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user rooms from Supabase", e)
        }

        val localList = localRooms.values.filter { it.hostId == userId }.toList()
        _userCreatedRooms.value = (localList + _userCreatedRooms.value).distinctBy { it.id }
        return@withContext localList
    }

    /**
     * Look up a room by its 4-digit code.
     */
    suspend fun getRoomByCode(code: String): Room? = withContext(Dispatchers.IO) {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return@withContext null

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("rooms")}?code=eq.$cleanCode&limit=1"
                val reqBuilder = Request.Builder().url(url).get()
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val room = Room.fromJson(arr.getJSONObject(0))
                            localRooms[room.id] = room
                            return@withContext room
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding room by code from Supabase", e)
            }
        }

        return@withContext localRooms.values.find { it.code == cleanCode }
    }

    /**
     * Look up a room by its ID.
     */
    suspend fun getRoomById(roomId: String): Room? = withContext(Dispatchers.IO) {
        if (roomId.isBlank()) return@withContext null

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("rooms")}?id=eq.$roomId&limit=1"
                val reqBuilder = Request.Builder().url(url).get()
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val room = Room.fromJson(arr.getJSONObject(0))
                            localRooms[room.id] = room
                            return@withContext room
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding room by id from Supabase", e)
            }
        }

        return@withContext localRooms[roomId]
    }

    /**
     * Creates a new listening room.
     */
    suspend fun createRoom(
        name: String,
        isPublic: Boolean,
        host: UserAccount?,
        tags: List<String> = emptyList()
    ): Result<Room> = withContext(Dispatchers.IO) {
        val roomId = "room_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val code = MockRoomData.generateRoomCode()
        val hostName = host?.displayName?.ifBlank { "You" } ?: "You"
        val hostId = host?.supabaseId?.ifBlank { host.email }?.ifBlank { "local_user" } ?: "local_user"

        val room = Room(
            id = roomId,
            name = name.trim().ifBlank { "Aurio Listening Room" },
            isPublic = isPublic,
            hostName = hostName,
            hostId = hostId,
            listenerCount = 1,
            tags = if (tags.isNotEmpty()) tags else listOf("Live", if (isPublic) "Public" else "Private"),
            code = code,
            tagline = "Same Music More Vibes"
        )

        localRooms[roomId] = room
        localParticipants[roomId] = mutableListOf(
            RoomParticipant(
                id = "${roomId}_$hostId",
                roomId = roomId,
                userId = hostId,
                name = hostName,
                avatarUrl = host?.photoUrl ?: "",
                isHost = true,
                isOnline = true
            )
        )
        localMessages[roomId] = mutableListOf(
            RoomMessage(
                id = "msg_${System.currentTimeMillis()}",
                roomId = roomId,
                senderId = "system",
                senderName = "Aurio",
                text = "Welcome to $name! Start listening together \uD83C\uDFB5",
                timestamp = "Just now"
            )
        )

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = getRestUrl("rooms")
                val payload = room.toJson()
                val reqBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Prefer", "return=representation")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val created = Room.fromJson(arr.getJSONObject(0))
                            localRooms[created.id] = created
                            _userCreatedRooms.value = listOf(created) + _userCreatedRooms.value.filter { it.id != created.id }
                            // Register host participant
                            joinRoom(created.id, host, isHost = true)
                            return@withContext Result.success(created)
                        }
                    } else {
                        Log.w(TAG, "createRoom remote returned ${res.code}: $body")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating room on Supabase", e)
            }
        }

        _userCreatedRooms.value = listOf(room) + _userCreatedRooms.value.filter { it.id != room.id }
        return@withContext Result.success(room)
    }

    /**
     * Updates playback state for real-time synchronization.
     */
    suspend fun updateRoomPlayback(
        roomId: String,
        songId: String,
        songTitle: String,
        songArtist: String,
        songThumbnail: String,
        isPlaying: Boolean,
        positionMs: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = localRooms[roomId]
        if (existing != null) {
            localRooms[roomId] = existing.copy(
                currentSongId = songId,
                currentSongTitle = songTitle,
                currentSongArtist = songArtist,
                currentSongThumbnail = songThumbnail,
                isPlaying = isPlaying,
                playbackPositionMs = positionMs,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        }

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("rooms")}?id=eq.$roomId"
                val payload = JSONObject().apply {
                    put("current_song_id", songId)
                    put("current_song_title", songTitle)
                    put("current_song_artist", songArtist)
                    put("current_song_thumbnail", songThumbnail)
                    put("is_playing", isPlaying)
                    put("playback_position_ms", positionMs)
                    put("last_sync_timestamp", System.currentTimeMillis())
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    if (res.isSuccessful) {
                        return@withContext Result.success(Unit)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating room playback on Supabase", e)
            }
        }

        return@withContext Result.success(Unit)
    }

    /**
     * Registers a user as an active participant in a room.
     */
    suspend fun joinRoom(
        roomId: String,
        user: UserAccount?,
        isHost: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = user?.supabaseId?.ifBlank { user.email }?.ifBlank { "guest_${System.currentTimeMillis()}" } ?: "guest_${System.currentTimeMillis()}"
        val userName = user?.displayName?.ifBlank { "You" } ?: "Guest"
        val participant = RoomParticipant(
            id = "${roomId}_$userId",
            roomId = roomId,
            userId = userId,
            name = userName,
            avatarUrl = user?.photoUrl ?: "",
            isHost = isHost,
            isOnline = true
        )

        val list = localParticipants.getOrPut(roomId) { mutableListOf() }
        list.removeAll { it.userId == userId }
        list.add(participant)

        val r = localRooms[roomId]
        if (r != null) {
            localRooms[roomId] = r.copy(listenerCount = list.size)
        }

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = getRestUrl("room_participants")
                val payload = participant.toJson()
                val reqBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    if (res.isSuccessful) {
                        // Update listener count
                        val updateCountUrl = "${getRestUrl("rooms")}?id=eq.$roomId"
                        val countPayload = JSONObject().apply { put("listener_count", list.size) }
                        val countReq = Request.Builder()
                            .url(updateCountUrl)
                            .patch(countPayload.toString().toRequestBody(jsonMediaType))
                        getHeaders().forEach { (k, v) -> countReq.addHeader(k, v) }
                        client.newCall(countReq.build()).execute().close()
                        return@withContext Result.success(Unit)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error joining room on Supabase", e)
            }
        }

        return@withContext Result.success(Unit)
    }

    /**
     * Leaves a room and decrements listener count.
     */
    suspend fun leaveRoom(roomId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        localParticipants[roomId]?.removeAll { it.userId == userId }
        val r = localRooms[roomId]
        if (r != null) {
            val count = (localParticipants[roomId]?.size ?: 1).coerceAtLeast(1)
            localRooms[roomId] = r.copy(listenerCount = count)
        }

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("room_participants")}?room_id=eq.$roomId&user_id=eq.$userId"
                val reqBuilder = Request.Builder().url(url).delete()
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                client.newCall(reqBuilder.build()).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Error leaving room on Supabase", e)
            }
        }

        return@withContext Result.success(Unit)
    }

    /**
     * Fetches participants for a room.
     */
    suspend fun getParticipants(roomId: String): List<RoomParticipant> = withContext(Dispatchers.IO) {
        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("room_participants")}?room_id=eq.$roomId&order=is_host.desc,last_seen.desc"
                val reqBuilder = Request.Builder().url(url).get()
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val participants = mutableListOf<RoomParticipant>()
                        for (i in 0 until arr.length()) {
                            participants.add(RoomParticipant.fromJson(arr.getJSONObject(i)))
                        }
                        if (participants.isNotEmpty()) {
                            localParticipants[roomId] = participants
                            return@withContext participants
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching participants from Supabase", e)
            }
        }

        return@withContext localParticipants[roomId]?.toList() ?: MockRoomData.sampleParticipants
    }

    /**
     * Fetches messages for a room.
     */
    suspend fun getMessages(roomId: String): List<RoomMessage> = withContext(Dispatchers.IO) {
        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("room_messages")}?room_id=eq.$roomId&order=created_at.asc"
                val reqBuilder = Request.Builder().url(url).get()
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val messages = mutableListOf<RoomMessage>()
                        for (i in 0 until arr.length()) {
                            messages.add(RoomMessage.fromJson(arr.getJSONObject(i)))
                        }
                        localMessages[roomId] = messages
                        return@withContext messages
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching messages from Supabase", e)
            }
        }

        return@withContext localMessages[roomId]?.toList() ?: MockRoomData.sampleMessages
    }

    /**
     * Sends a chat message in a room.
     */
    suspend fun sendMessage(
        roomId: String,
        sender: UserAccount?,
        text: String
    ): Result<RoomMessage> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext Result.failure(Exception("Message cannot be empty"))

        val senderName = sender?.displayName?.ifBlank { "You" } ?: "You"
        val senderId = sender?.supabaseId?.ifBlank { sender.email }?.ifBlank { "local_user" } ?: "local_user"
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val message = RoomMessage(
            id = msgId,
            roomId = roomId,
            senderId = senderId,
            senderName = senderName,
            avatarUrl = sender?.photoUrl ?: "",
            text = cleanText,
            timestamp = "Just now"
        )

        localMessages.getOrPut(roomId) { mutableListOf() }.add(message)

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = getRestUrl("room_messages")
                val payload = message.toJson()
                val reqBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Prefer", "return=representation")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val created = RoomMessage.fromJson(arr.getJSONObject(0))
                            return@withContext Result.success(created)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message on Supabase", e)
            }
        }

        return@withContext Result.success(message)
    }

    /**
     * Deletes a room.
     */
    suspend fun deleteRoom(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        localRooms.remove(roomId)
        localParticipants.remove(roomId)
        localMessages.remove(roomId)
        localRequests.remove(roomId)
        _userCreatedRooms.value = _userCreatedRooms.value.filter { it.id != roomId }

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("rooms")}?id=eq.$roomId"
                val reqBuilder = Request.Builder().url(url).delete()
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                client.newCall(reqBuilder.build()).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting room on Supabase", e)
            }
        }

        return@withContext Result.success(Unit)
    }

    /**
     * Submits a song request from a listener to the room host.
     */
    suspend fun submitSongRequest(request: SongRequest): Result<SongRequest> = withContext(Dispatchers.IO) {
        val list = localRequests.getOrPut(request.roomId) { mutableListOf() }
        list.removeAll { it.id == request.id }
        list.add(request)

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = getRestUrl("room_song_requests")
                val payload = request.toJson()
                val reqBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Prefer", "return=representation")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val created = SongRequest.fromJson(arr.getJSONObject(0))
                            return@withContext Result.success(created)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting song request on Supabase", e)
            }
        }

        return@withContext Result.success(request)
    }

    /**
     * Fetches pending song requests for a room.
     */
    suspend fun getPendingRequests(roomId: String): List<SongRequest> = withContext(Dispatchers.IO) {
        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("room_song_requests")}?room_id=eq.$roomId&status=eq.PENDING&order=created_at.asc"
                val reqBuilder = Request.Builder().url(url).get()
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val requests = mutableListOf<SongRequest>()
                        for (i in 0 until arr.length()) {
                            requests.add(SongRequest.fromJson(arr.getJSONObject(i)))
                        }
                        localRequests[roomId] = requests
                        return@withContext requests
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching pending requests from Supabase", e)
            }
        }

        return@withContext localRequests[roomId]?.filter { it.status == SongRequestStatus.PENDING } ?: emptyList()
    }

    /**
     * Updates status of a song request (ACCEPTED or DECLINED).
     */
    suspend fun updateRequestStatus(requestId: String, roomId: String, status: SongRequestStatus): Result<Unit> = withContext(Dispatchers.IO) {
        localRequests[roomId]?.let { list ->
            val idx = list.indexOfFirst { it.id == requestId }
            if (idx != -1) {
                list[idx] = list[idx].copy(status = status)
            }
        }

        if (AuthConfig.isSupabaseConfigured) {
            try {
                val url = "${getRestUrl("room_song_requests")}?id=eq.$requestId"
                val payload = JSONObject().apply { put("status", status.name) }
                val reqBuilder = Request.Builder()
                    .url(url)
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                getHeaders().forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating request status on Supabase", e)
            }
        }

        return@withContext Result.success(Unit)
    }

    companion object {
        @Volatile
        private var instance: RoomRepository? = null

        fun getInstance(): RoomRepository {
            return instance ?: synchronized(this) {
                instance ?: RoomRepository().also { instance = it }
            }
        }
    }
}
