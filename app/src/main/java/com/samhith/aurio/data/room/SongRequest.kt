package com.samhith.aurio.data.room

import com.samhith.aurio.data.music.SongItem
import org.json.JSONObject

enum class SongRequestType {
    PLAY_NEXT,
    ADD_TO_QUEUE
}

enum class SongRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}

data class SongRequest(
    val id: String,
    val roomId: String,
    val song: SongItem,
    val requesterId: String,
    val requesterName: String,
    val requestType: SongRequestType,
    val status: SongRequestStatus = SongRequestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("room_id", roomId)
            put("song_id", song.id)
            put("song_title", song.title)
            put("song_artist", song.artist)
            put("song_thumbnail", song.thumbnailUrl)
            put("requester_id", requesterId)
            put("requester_name", requesterName)
            put("request_type", requestType.name)
            put("status", status.name)
            put("created_at", createdAt)
        }
    }

    fun toMessageText(): String {
        return "[SONG_REQUEST:${toJson()}]"
    }

    companion object {
        fun fromJson(json: JSONObject): SongRequest {
            val typeStr = json.optString("request_type", SongRequestType.ADD_TO_QUEUE.name)
            val statusStr = json.optString("status", SongRequestStatus.PENDING.name)
            return SongRequest(
                id = json.optString("id", ""),
                roomId = json.optString("room_id", ""),
                song = SongItem(
                    id = json.optString("song_id", ""),
                    title = json.optString("song_title", "Unknown Title"),
                    artist = json.optString("song_artist", "Unknown Artist"),
                    thumbnailUrl = json.optString("song_thumbnail", "")
                ),
                requesterId = json.optString("requester_id", ""),
                requesterName = json.optString("requester_name", "Listener"),
                requestType = try { SongRequestType.valueOf(typeStr) } catch (_: Exception) { SongRequestType.ADD_TO_QUEUE },
                status = try { SongRequestStatus.valueOf(statusStr) } catch (_: Exception) { SongRequestStatus.PENDING },
                createdAt = json.optLong("created_at", System.currentTimeMillis())
            )
        }

        fun fromMessageText(text: String): SongRequest? {
            if (!text.contains("[SONG_REQUEST:")) return null
            return try {
                val startIdx = text.indexOf("[SONG_REQUEST:")
                val jsonStart = startIdx + "[SONG_REQUEST:".length
                val endIdx = text.indexOf("]", startIndex = jsonStart)
                val jsonStr = if (endIdx != -1) text.substring(jsonStart, endIdx) else text.substring(jsonStart)
                fromJson(JSONObject(jsonStr))
            } catch (_: Exception) {
                null
            }
        }

        fun parseStatusUpdate(text: String): Pair<String, SongRequestStatus>? {
            if (!text.contains("[STATUS_UPDATE:")) return null
            return try {
                val startIdx = text.indexOf("[STATUS_UPDATE:")
                val tagStart = startIdx + "[STATUS_UPDATE:".length
                val endIdx = text.indexOf("]", startIndex = tagStart)
                val content = if (endIdx != -1) text.substring(tagStart, endIdx) else text.substring(tagStart)
                val parts = content.split(":")
                if (parts.size >= 2) {
                    val status = SongRequestStatus.valueOf(parts[0])
                    val reqId = parts[1]
                    Pair(reqId, status)
                } else null
            } catch (_: Exception) {
                null
            }
        }

        fun cleanDisplayText(text: String): String {
            var clean = text
            if (clean.contains("[SONG_REQUEST:")) {
                val start = clean.indexOf("[SONG_REQUEST:")
                val end = clean.indexOf("]", startIndex = start)
                if (end != -1) {
                    clean = clean.removeRange(start, end + 1)
                }
            }
            if (clean.contains("[STATUS_UPDATE:")) {
                val start = clean.indexOf("[STATUS_UPDATE:")
                val end = clean.indexOf("]", startIndex = start)
                if (end != -1) {
                    clean = clean.removeRange(start, end + 1)
                }
            }
            return clean.trim()
        }
    }
}
