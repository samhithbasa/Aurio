package com.samhith.aurio.data.room

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a listening room for the Listen Together feature.
 */
data class Room(
    val id: String,
    val name: String,
    val isPublic: Boolean = true,
    val hostName: String = "",
    val hostId: String = "",
    val listenerCount: Int = 1,
    val tags: List<String> = emptyList(),
    val imageUrl: String = "",
    val code: String = "",
    val tagline: String = "Same Music More Vibes",
    val currentSongId: String = "",
    val currentSongTitle: String = "",
    val currentSongArtist: String = "",
    val currentSongThumbnail: String = "",
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val lastSyncTimestamp: Long = 0L,
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("is_public", isPublic)
        put("host_name", hostName)
        put("host_id", hostId)
        put("listener_count", listenerCount)
        put("tags", JSONArray(tags))
        put("image_url", imageUrl)
        put("code", code)
        put("tagline", tagline)
        put("current_song_id", currentSongId)
        put("current_song_title", currentSongTitle)
        put("current_song_artist", currentSongArtist)
        put("current_song_thumbnail", currentSongThumbnail)
        put("is_playing", isPlaying)
        put("playback_position_ms", playbackPositionMs)
        put("last_sync_timestamp", lastSyncTimestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): Room {
            val tagsList = mutableListOf<String>()
            val tagsArr = json.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    tagsList.add(tagsArr.optString(i, ""))
                }
            }
            return Room(
                id = json.optString("id", ""),
                name = json.optString("name", "Aurio Room"),
                isPublic = json.optBoolean("is_public", true),
                hostName = json.optString("host_name", "Host"),
                hostId = json.optString("host_id", ""),
                listenerCount = json.optInt("listener_count", 1),
                tags = tagsList,
                imageUrl = json.optString("image_url", ""),
                code = json.optString("code", ""),
                tagline = json.optString("tagline", "Same Music More Vibes"),
                currentSongId = json.optString("current_song_id", ""),
                currentSongTitle = json.optString("current_song_title", ""),
                currentSongArtist = json.optString("current_song_artist", ""),
                currentSongThumbnail = json.optString("current_song_thumbnail", ""),
                isPlaying = json.optBoolean("is_playing", false),
                playbackPositionMs = json.optLong("playback_position_ms", 0L),
                lastSyncTimestamp = json.optLong("last_sync_timestamp", 0L),
                createdAt = json.optString("created_at", "")
            )
        }
    }
}

/**
 * Represents a participant in a listening room.
 */
data class RoomParticipant(
    val id: String = "",
    val roomId: String = "",
    val userId: String = "",
    val name: String,
    val avatarUrl: String = "",
    val isHost: Boolean = false,
    val isOnline: Boolean = true,
    val isAudioEnabled: Boolean = true,
    val isMicEnabled: Boolean = false,
    val lastSeen: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (id.isNotBlank()) put("id", id)
        put("room_id", roomId)
        put("user_id", userId)
        put("user_name", name)
        put("avatar_url", avatarUrl)
        put("is_host", isHost)
        put("is_online", isOnline)
        put("is_audio_enabled", isAudioEnabled)
        put("is_mic_enabled", isMicEnabled)
    }

    companion object {
        fun fromJson(json: JSONObject): RoomParticipant {
            return RoomParticipant(
                id = json.optString("id", ""),
                roomId = json.optString("room_id", ""),
                userId = json.optString("user_id", ""),
                name = json.optString("user_name", json.optString("name", "User")),
                avatarUrl = json.optString("avatar_url", ""),
                isHost = json.optBoolean("is_host", false),
                isOnline = json.optBoolean("is_online", true),
                isAudioEnabled = json.optBoolean("is_audio_enabled", true),
                isMicEnabled = json.optBoolean("is_mic_enabled", false),
                lastSeen = json.optString("last_seen", "")
            )
        }
    }
}

/**
 * Represents a chat message in a listening room.
 */
data class RoomMessage(
    val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String,
    val avatarUrl: String = "",
    val text: String,
    val timestamp: String = "",
    val createdAt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (id.isNotBlank()) put("id", id)
        put("room_id", roomId)
        put("sender_id", senderId)
        put("sender_name", senderName)
        put("avatar_url", avatarUrl)
        put("text", text)
    }

    companion object {
        fun fromJson(json: JSONObject): RoomMessage {
            val created = json.optString("created_at", "")
            val formattedTime = if (created.length >= 16) {
                try {
                    val timePart = created.substring(11, 16)
                    val parts = timePart.split(":")
                    val hour = parts[0].toInt()
                    val min = parts[1]
                    val amPm = if (hour >= 12) "PM" else "AM"
                    val displayHour = if (hour % 12 == 0) 12 else hour % 12
                    "$displayHour:$min $amPm"
                } catch (_: Exception) {
                    "Just now"
                }
            } else {
                "Just now"
            }
            return RoomMessage(
                id = json.optString("id", ""),
                roomId = json.optString("room_id", ""),
                senderId = json.optString("sender_id", ""),
                senderName = json.optString("sender_name", "User"),
                avatarUrl = json.optString("avatar_url", ""),
                text = json.optString("text", ""),
                timestamp = formattedTime,
                createdAt = created
            )
        }
    }
}

/**
 * Provides fallback / initial seed data for the Listen Together feature.
 */
object MockRoomData {

    val popularRooms = listOf(
        Room(
            id = "room_1",
            name = "Late Night Vibes",
            isPublic = true,
            hostName = "Aarav",
            listenerCount = 328,
            tags = listOf("Chill", "Lo-fi", "Hindi"),
            code = "4821"
        ),
        Room(
            id = "room_2",
            name = "Good Music Good Company",
            isPublic = true,
            hostName = "Sneha",
            listenerCount = 271,
            tags = listOf("Pop", "Trending", "Mix"),
            code = "7135"
        ),
        Room(
            id = "room_3",
            name = "Road Trip Vibes",
            isPublic = true,
            hostName = "Rohan",
            listenerCount = 189,
            tags = listOf("Travel", "Retro", "Hindi"),
            code = "3964"
        ),
        Room(
            id = "room_4",
            name = "Study Together",
            isPublic = true,
            hostName = "Priya",
            listenerCount = 145,
            tags = listOf("Focus", "Lo-fi", "Instrumental"),
            code = "5207"
        ),
        Room(
            id = "room_5",
            name = "Bollywood Beats",
            isPublic = true,
            hostName = "Karan",
            listenerCount = 412,
            tags = listOf("Bollywood", "Dance", "Party"),
            code = "8653"
        ),
        Room(
            id = "room_6",
            name = "Indie Chill",
            isPublic = true,
            hostName = "Tanveer",
            listenerCount = 97,
            tags = listOf("Indie", "Acoustic", "English"),
            code = "1478"
        )
    )

    val sampleParticipants = listOf(
        RoomParticipant(name = "You", isHost = true, isOnline = true),
        RoomParticipant(name = "Aarav", isOnline = true),
        RoomParticipant(name = "Sneha", isOnline = true),
        RoomParticipant(name = "Rohan", isOnline = true),
        RoomParticipant(name = "Priya", isOnline = true),
        RoomParticipant(name = "Karan", isOnline = false)
    )

    val sampleMessages = listOf(
        RoomMessage(
            senderName = "Aarav",
            text = "This song hits different at night \u2764\uFE0F",
            timestamp = "10:24 PM"
        ),
        RoomMessage(
            senderName = "Sneha",
            text = "Totally! \u2728",
            timestamp = "10:25 PM"
        ),
        RoomMessage(
            senderName = "Rohan",
            text = "Add some Arijit Singh next!",
            timestamp = "10:26 PM"
        )
    )

    fun generateRoomCode(): String {
        return (1000..9999).random().toString()
    }
}
