package com.samhith.aurio.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class SongLyrics(
    val title: String,
    val artist: String,
    val plainLyrics: String?,
    val syncedLines: List<LyricLine> = emptyList(),
    val isInstrumental: Boolean = false
) {
    val hasLyrics: Boolean
        get() = isInstrumental || !plainLyrics.isNullOrBlank() || syncedLines.isNotEmpty()
}

/**
 * Repository for fetching synchronized and plain lyrics for any playing track
 * via free public LRCLIB and fallback lyrics endpoints.
 */
class LyricsRepository private constructor() {

    private val TAG = "LyricsRepository"
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // In-memory cache keyed by "title::artist"
    private val memoryCache = mutableMapOf<String, SongLyrics>()

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Long = 0L
    ): SongLyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = sanitizeTitle(title)
        val cleanArtist = sanitizeArtist(artist)
        val cacheKey = "${cleanTitle.lowercase()}::${cleanArtist.lowercase()}"

        // Check memory cache
        memoryCache[cacheKey]?.let {
            return@withContext it
        }

        // 1. Direct fetch via LRCLIB get endpoint
        var lyrics = fetchDirectLrclib(cleanTitle, cleanArtist, durationSeconds)

        // 2. If direct fetch missed, search LRCLIB
        if (lyrics == null || !lyrics.hasLyrics) {
            lyrics = searchLrclib(cleanTitle, cleanArtist)
        }

        // 3. Cache result if found
        if (lyrics != null) {
            memoryCache[cacheKey] = lyrics
        }

        lyrics
    }

    private fun fetchDirectLrclib(title: String, artist: String, durationSeconds: Long): SongLyrics? {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val urlBuilder = StringBuilder("https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist")
            if (durationSeconds > 0L) {
                urlBuilder.append("&duration=$durationSeconds")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "AurioMusicApp/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            parseLyricsJson(JSONObject(body), title, artist)
        } catch (e: Exception) {
            Log.w(TAG, "Direct LRCLIB fetch failed for '$title': ${e.message}")
            null
        }
    }

    private fun searchLrclib(title: String, artist: String): SongLyrics? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = "https://lrclib.net/api/search?q=$query"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AurioMusicApp/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            val array = JSONArray(body)
            if (array.length() == 0) return null

            // Pick the best match with lyrics
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val plain = obj.optString("plainLyrics", "")
                val synced = obj.optString("syncedLyrics", "")
                val instrumental = obj.optBoolean("instrumental", false)
                if (plain.isNotBlank() || synced.isNotBlank() || instrumental) {
                    return parseLyricsJson(obj, title, artist)
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Search LRCLIB failed for '$title': ${e.message}")
            null
        }
    }

    private fun parseLyricsJson(obj: JSONObject, fallbackTitle: String, fallbackArtist: String): SongLyrics {
        val title = obj.optString("name", fallbackTitle)
        val artist = obj.optString("artistName", fallbackArtist)
        val instrumental = obj.optBoolean("instrumental", false)
        val plain = obj.optString("plainLyrics", "").trim().takeIf { it.isNotBlank() }
        val syncedRaw = obj.optString("syncedLyrics", "").trim()

        val syncedLines = if (syncedRaw.isNotBlank()) {
            parseSyncedLyrics(syncedRaw)
        } else {
            emptyList()
        }

        return SongLyrics(
            title = title,
            artist = artist,
            plainLyrics = plain,
            syncedLines = syncedLines,
            isInstrumental = instrumental
        )
    }

    private fun parseSyncedLyrics(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val pattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2}(?:\\.\\d+)?)\\](.*)")

        for (lineStr in raw.lines()) {
            val trimmed = lineStr.trim()
            if (trimmed.isEmpty()) continue
            val matcher = pattern.matcher(trimmed)
            if (matcher.matches()) {
                try {
                    val min = matcher.group(1)?.toLong() ?: 0L
                    val sec = matcher.group(2)?.toDouble() ?: 0.0
                    val text = matcher.group(3)?.trim() ?: ""
                    val timestampMs = (min * 60 * 1000 + sec * 1000).toLong()
                    if (text.isNotBlank()) {
                        lines.add(LyricLine(timestampMs, text))
                    }
                } catch (_: Exception) {}
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    private fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(from \".*?\"\\)"), "")
            .replace(Regex("(?i)\\(from '.*?'\\)"), "")
            .replace(Regex("(?i)\\[official.*?\\]"), "")
            .replace(Regex("(?i)\\(official.*?\\)"), "")
            .replace(Regex("(?i)\\(lyric.*?\\)"), "")
            .replace(Regex("(?i)\\[lyric.*?\\]"), "")
            .replace(Regex("(?i)\\(feat\\..*?\\)"), "")
            .replace(Regex("(?i)\\[feat\\..*?\\]"), "")
            .replace(Regex("(?i)ft\\..*"), "")
            .replace(Regex("(?i)feat\\..*"), "")
            .replace(Regex("[|/].*"), "")
            .trim()
    }

    private fun sanitizeArtist(artist: String): String {
        return artist
            .split(",", "&", "feat.", "ft.", ";")
            .firstOrNull()
            ?.trim() ?: artist
    }

    companion object {
        @Volatile
        private var instance: LyricsRepository? = null

        fun getInstance(): LyricsRepository {
            return instance ?: synchronized(this) {
                instance ?: LyricsRepository().also { instance = it }
            }
        }
    }
}
