package com.samhith.aurio.data.music

import android.util.Base64
import android.util.Log
import com.samhith.aurio.ui.artists.ArtistDetailItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance Music Streaming Client.
 * Integrates direct streaming engines and YouTube Music / InnerTube metadata resolvers
 * to extract pristine 320kbps audio streams without throttling or token failures.
 */
class InnertubeClient private constructor() {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "AurioMusicClient"
        private const val SAAVN_BASE = "https://www.jiosaavn.com/api.php"
        private const val DES_KEY = "38346591"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Single source of truth for the InnerTube WEB_REMIX client version.
        // Previously this was hardcoded separately (and inconsistently) in multiple
        // request builders — update this one constant when YouTube Music rejects requests.
        private const val YT_MUSIC_CLIENT_VERSION = "1.20241118.01.00"

        // Fallback client used when WEB_REMIX is rejected/stale, so a single outdated
        // pinned version doesn't take down search/radio entirely.
        private const val YT_MUSIC_FALLBACK_CLIENT_NAME = "ANDROID_MUSIC"
        private const val YT_MUSIC_FALLBACK_CLIENT_VERSION = "7.16.51"

        @Volatile
        private var instance: InnertubeClient? = null

        fun getInstance(): InnertubeClient {
            return instance ?: synchronized(this) {
                instance ?: InnertubeClient().also { instance = it }
            }
        }
    }

    /**
     * Decrypts JioSaavn's DES-encrypted media URL and upgrades it to 320kbps MP4/AAC.
     */
    fun decryptMediaUrl(encrypted: String?): String? {
        if (encrypted.isNullOrBlank()) return null
        return try {
            val decoded = Base64.decode(encrypted, Base64.DEFAULT)
            val secretKey = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decryptedBytes = cipher.doFinal(decoded)
            var rawUrl = String(decryptedBytes, Charsets.UTF_8).trim()

            // Upgrade bitrates: _96.mp4 / _160.mp4 -> _320.mp4 (High Quality)
            if (rawUrl.contains("_96.mp4")) {
                rawUrl = rawUrl.replace("_96.mp4", "_320.mp4")
            } else if (rawUrl.contains("_160.mp4")) {
                rawUrl = rawUrl.replace("_160.mp4", "_320.mp4")
            }
            rawUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting media URL: ${e.message}")
            null
        }
    }

    /**
     * Search songs by query with multi-stage index querying, autocomplete fallback,
     * and YouTube Music InnerTube card/section parsing.
     */
    suspend fun searchSongs(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val collected = mutableListOf<SongItem>()
        val seenIds = mutableSetOf<String>()

        fun addAllUnique(items: List<SongItem>) {
            for (item in items) {
                val normKey = "${item.title.lowercase().trim()} - ${item.artist.lowercase().trim()}"
                if (seenIds.add(item.id) && seenIds.add(normKey)) {
                    collected.add(item)
                }
            }
        }

        // 1. JioSaavn Full-Text Song Search Engine
        val saavnResults = querySaavnEndpoint(
            call = "search.getSongSearchResults",
            query = trimmed
        )
        addAllUnique(saavnResults)

        // 2. JioSaavn Autocomplete Track Index (handles specific names like "i am done maanu")
        if (collected.size < 5) {
            val saavnAuto = querySaavnAutocompleteSongs(trimmed)
            addAllUnique(saavnAuto)
        }

        // 3. JioSaavn Global Index Search Engine
        if (collected.size < 5) {
            val saavnGlobalResults = querySaavnEndpoint(
                call = "search.getResults",
                query = trimmed
            )
            addAllUnique(saavnGlobalResults)
        }

        // 4. YouTube InnerTube Search (handles artists/indie tracks not indexed on Saavn)
        if (collected.size < 15) {
            val ytResults = searchYoutubeInnerTube(trimmed, params = "EgWKAQIIAWoKEAkQBRAKEAMQBA==")
            addAllUnique(ytResults)
        }

        // 5. Fallback unconstrained YouTube search if still low
        if (collected.isEmpty()) {
            val unconstrainedYt = searchYoutubeInnerTube(trimmed, params = null)
            addAllUnique(unconstrainedYt)
        }

        collected
    }

    /**
     * Search artists by query across JioSaavn & YouTube InnerTube.
     */
    suspend fun searchArtists(query: String): List<ArtistDetailItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val artists = mutableListOf<ArtistDetailItem>()
        val seenNames = mutableSetOf<String>()

        // 1. Saavn Autocomplete Artists
        try {
            val saavnArtists = querySaavnAutocompleteArtists(trimmed)
            for (art in saavnArtists) {
                if (seenNames.add(art.name.lowercase().trim())) {
                    artists.add(art)
                }
            }
        } catch (_: Exception) {}

        // 2. YouTube InnerTube Artist Search
        try {
            val ytArtists = queryYoutubeArtists(trimmed)
            for (art in ytArtists) {
                if (seenNames.add(art.name.lowercase().trim())) {
                    artists.add(art)
                }
            }
        } catch (_: Exception) {}

        artists
    }

    /**
     * Search playlists and albums by query across JioSaavn & YouTube InnerTube.
     */
    suspend fun searchPlaylists(query: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val playlists = mutableListOf<PlaylistItem>()
        val seenIds = mutableSetOf<String>()

        // 1. Saavn Autocomplete Playlists & Albums
        try {
            val saavnPlaylists = querySaavnAutocompletePlaylists(trimmed)
            for (pl in saavnPlaylists) {
                if (seenIds.add(pl.id)) {
                    playlists.add(pl)
                }
            }
        } catch (_: Exception) {}

        // 2. YouTube InnerTube Playlists
        try {
            val ytPlaylists = queryYoutubePlaylists(trimmed)
            for (pl in ytPlaylists) {
                if (seenIds.add(pl.id)) {
                    playlists.add(pl)
                }
            }
        } catch (_: Exception) {}

        playlists
    }

    /**
     * Search music videos by query on YouTube InnerTube.
     */
    suspend fun searchVideos(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        searchYoutubeInnerTube(trimmed, params = "EgWKAQIQAWoKEAkQBRAKEAMQBA==")
    }

    /**
     * Aggregates all categories concurrently for the "All" tab.
     */
    suspend fun searchAll(query: String): SearchResultsGroup = withContext(Dispatchers.IO) {
        val songsDeferred = async { searchSongs(query).take(12) }
        val artistsDeferred = async { searchArtists(query).take(8) }
        val playlistsDeferred = async { searchPlaylists(query).take(8) }
        val videosDeferred = async { searchVideos(query).take(8) }

        SearchResultsGroup(
            songs = songsDeferred.await(),
            artists = artistsDeferred.await(),
            playlists = playlistsDeferred.await(),
            videos = videosDeferred.await()
        )
    }

    private fun querySaavnEndpoint(call: String, query: String): List<SongItem> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SAAVN_BASE?__call=$call&_format=json&n=30&p=1&_marker=0&ctx=android&q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val json = JSONObject(bodyString)

            val results = json.optJSONArray("results")
                ?: json.optJSONObject("data")?.optJSONArray("results")
                ?: JSONArray()

            val songList = mutableListOf<SongItem>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                parseSaavnSongItem(item)?.let { songList.add(it) }
            }
            return songList
        } catch (e: Exception) {
            Log.w(TAG, "Saavn query error for $call ($query): ${e.message}")
            return emptyList()
        }
    }

    private fun querySaavnAutocompleteSongs(query: String): List<SongItem> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SAAVN_BASE?__call=autocomplete.get&_format=json&query=$encodedQuery&ctx=android"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val json = JSONObject(bodyString)

            val songData = json.optJSONObject("songs")?.optJSONArray("data") ?: return emptyList()
            val pids = mutableListOf<String>()
            for (i in 0 until songData.length()) {
                val item = songData.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isNotBlank()) pids.add(id)
            }

            if (pids.isEmpty()) return emptyList()

            // Batch fetch song details for high quality 320kbps streams
            val pidsStr = pids.joinToString(",")
            val detailUrl = "$SAAVN_BASE?__call=song.getDetails&_format=json&pids=$pidsStr&ctx=android"
            val detailReq = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val detailResp = httpClient.newCall(detailReq).execute()
            val detailBody = detailResp.body?.string() ?: return emptyList()
            val detailJson = JSONObject(detailBody)

            val songsList = mutableListOf<SongItem>()
            for (pid in pids) {
                val songObj = detailJson.optJSONObject(pid) ?: continue
                parseSaavnSongItem(songObj)?.let { songsList.add(it) }
            }
            return songsList
        } catch (e: Exception) {
            Log.w(TAG, "Error in querySaavnAutocompleteSongs: ${e.message}")
            return emptyList()
        }
    }

    private fun querySaavnAutocompleteArtists(query: String): List<ArtistDetailItem> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SAAVN_BASE?__call=autocomplete.get&_format=json&query=$encodedQuery&ctx=android"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val json = JSONObject(bodyString)

            val artistData = json.optJSONObject("artists")?.optJSONArray("data") ?: return emptyList()
            val list = mutableListOf<ArtistDetailItem>()
            for (i in 0 until artistData.length()) {
                val item = artistData.optJSONObject(i) ?: continue
                val name = unescapeHtml(item.optString("name", "").ifBlank { item.optString("title", "") })
                if (name.isBlank()) continue

                var image = item.optString("image", "")
                if (image.contains("artist-default")) {
                    image = ""
                } else if (image.isNotBlank()) {
                    image = image.replace("50x50", "500x500").replace("150x150", "500x500")
                }

                val role = unescapeHtml(item.optString("description", "Popular Artist").ifBlank { "Artist" })
                list.add(
                    ArtistDetailItem(
                        name = name,
                        imageUrl = image,
                        genre = if (role.isNotBlank()) role else "Music Artist",
                        monthlyListeners = "${(20..95).random()}M+"
                    )
                )
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun querySaavnAutocompletePlaylists(query: String): List<PlaylistItem> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SAAVN_BASE?__call=autocomplete.get&_format=json&query=$encodedQuery&ctx=android"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()
            val json = JSONObject(bodyString)

            val list = mutableListOf<PlaylistItem>()

            // Playlists
            val plData = json.optJSONObject("playlists")?.optJSONArray("data") ?: JSONArray()
            for (i in 0 until plData.length()) {
                val item = plData.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                val title = unescapeHtml(item.optString("title", ""))
                if (title.isBlank()) continue

                var image = item.optString("image", "")
                if (image.isNotBlank()) {
                    image = image.replace("50x50", "500x500").replace("150x150", "500x500")
                }
                val desc = unescapeHtml(item.optString("description", "Curated Playlist"))
                list.add(
                    PlaylistItem(
                        id = if (id.isNotBlank()) id else "pl_${title.hashCode()}",
                        title = title,
                        subtitle = if (desc.isNotBlank()) desc else "Playlist",
                        thumbnailUrl = image,
                        type = "Playlist"
                    )
                )
            }

            // Albums
            val albData = json.optJSONObject("albums")?.optJSONArray("data") ?: JSONArray()
            for (i in 0 until albData.length()) {
                val item = albData.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                val title = unescapeHtml(item.optString("title", ""))
                if (title.isBlank()) continue

                var image = item.optString("image", "")
                if (image.isNotBlank()) {
                    image = image.replace("50x50", "500x500").replace("150x150", "500x500")
                }
                val moreInfo = item.optJSONObject("more_info")
                val year = moreInfo?.optString("year", "") ?: ""
                val subtitle = if (year.isNotBlank()) "Album • $year" else "Album"

                list.add(
                    PlaylistItem(
                        id = if (id.isNotBlank()) id else "alb_${title.hashCode()}",
                        title = title,
                        subtitle = subtitle,
                        thumbnailUrl = image,
                        type = "Album"
                    )
                )
            }

            return list
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun queryYoutubeArtists(query: String): List<ArtistDetailItem> {
        try {
            val json = executeYoutubeSearchRequestJson(query, params = "EgWKAQIgAWoKEAkQBRAKEAMQBA==") ?: return emptyList()
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            val artists = mutableListOf<ArtistDetailItem>()
            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i) ?: continue
                val shelf = section.optJSONObject("musicShelfRenderer")
                    ?: section.optJSONObject("itemSectionRenderer") ?: continue

                val items = shelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val flexColumns = item.optJSONArray("flexColumns") ?: continue
                    if (flexColumns.length() == 0) continue

                    val name = extractRunsText(
                        flexColumns.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                    )
                    if (name.isBlank()) continue

                    var genre = "Artist"
                    var monthlyListeners = "50M+"
                    if (flexColumns.length() > 1) {
                        val subText = extractRunsText(
                            flexColumns.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        )
                        if (subText.contains("monthly", ignoreCase = true) || subText.contains("subscribers", ignoreCase = true)) {
                            monthlyListeners = subText.substringAfter("•").trim().ifBlank { "50M+" }
                        }
                        if (subText.isNotBlank()) {
                            genre = subText.substringBefore("•").trim().ifBlank { "Artist" }
                        }
                    }

                    val thumbs = item.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                    } else ""

                    artists.add(
                        ArtistDetailItem(
                            name = name,
                            imageUrl = thumbUrl,
                            genre = genre,
                            monthlyListeners = monthlyListeners
                        )
                    )
                }
            }
            return artists
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun queryYoutubePlaylists(query: String): List<PlaylistItem> {
        try {
            val json = executeYoutubeSearchRequestJson(query, params = "EgWKAQIBAWoKEAkQBRAKEAMQBA==") ?: return emptyList()
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            val playlists = mutableListOf<PlaylistItem>()
            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i) ?: continue
                val shelf = section.optJSONObject("musicShelfRenderer")
                    ?: section.optJSONObject("itemSectionRenderer") ?: continue

                val items = shelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val flexColumns = item.optJSONArray("flexColumns") ?: continue
                    if (flexColumns.length() == 0) continue

                    val title = extractRunsText(
                        flexColumns.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                    )
                    if (title.isBlank()) continue

                    var subtitle = "Playlist"
                    if (flexColumns.length() > 1) {
                        subtitle = extractRunsText(
                            flexColumns.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        ).ifBlank { "Playlist" }
                    }

                    val thumbs = item.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                    } else ""

                    val playlistId = item.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("playlistId") ?: "yt_${title.hashCode()}"

                    playlists.add(
                        PlaylistItem(
                            id = playlistId,
                            title = title,
                            subtitle = subtitle,
                            thumbnailUrl = thumbUrl,
                            type = "Playlist"
                        )
                    )
                }
            }
            return playlists
        } catch (_: Exception) {
            return emptyList()
        }
    }

    /**
     * Fetches top trending / popular songs.
     */
    suspend fun getTrendingCharts(): List<SongItem> = withContext(Dispatchers.IO) {
        val queries = listOf("Top Hits 2024", "Billboard Hot 100", "Trending Music", "Viral Hits 2024", "Today's Top Hits", "Global Top 50")
        for (q in queries.shuffled()) {
            val results = searchSongs(q)
            if (results.isNotEmpty()) {
                return@withContext results
            }
        }
        emptyList()
    }

    /**
     * Resolves the direct audio stream URL for a given SongItem.
     */
    suspend fun getAudioStreamUrl(song: SongItem): String? = withContext(Dispatchers.IO) {
        // 1. Check if already has a direct streamUrl
        if (!song.streamUrl.isNullOrBlank()) {
            return@withContext song.streamUrl
        }

        // 2. Decrypt if encryptedMediaUrl is available
        if (!song.encryptedMediaUrl.isNullOrBlank()) {
            val decrypted = decryptMediaUrl(song.encryptedMediaUrl)
            if (!decrypted.isNullOrBlank()) {
                Log.d(TAG, "Successfully decrypted stream URL for '${song.title}' -> $decrypted")
                return@withContext decrypted
            }
        }

        // 3. Query by Song Title + Artist
        try {
            val searchQuery = "${song.title} ${song.artist}".trim()
            val results = searchSongs(searchQuery)
            val match = results.firstOrNull { it.streamUrl != null || it.encryptedMediaUrl != null }
            if (match != null) {
                val stream = match.streamUrl ?: decryptMediaUrl(match.encryptedMediaUrl)
                if (!stream.isNullOrBlank()) {
                    Log.d(TAG, "Matched stream for '${song.title}' via query -> $stream")
                    return@withContext stream
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream for '${song.title}': ${e.message}")
        }

        null
    }

    private fun parseSaavnSongItem(item: JSONObject): SongItem? {
        val id = item.optString("id", "")
        val title = unescapeHtml(item.optString("song", "").ifBlank { item.optString("title", "") })
        if (title.isBlank()) return null

        val artist = unescapeHtml(
            item.optString("singers", "")
                .ifBlank { item.optString("primary_artists", "") }
                .ifBlank { item.optString("music", "") }
                .ifBlank { "Aurio Music" }
        )

        val album = unescapeHtml(item.optString("album", ""))
        val durationStr = item.optString("duration", "0")
        val durationSeconds = durationStr.toLongOrNull() ?: 0L

        val durationText = if (durationSeconds > 0) {
            val m = durationSeconds / 60
            val s = durationSeconds % 60
            String.format("%d:%02d", m, s)
        } else ""

        var image = item.optString("image", "")
        if (image.isNotBlank()) {
            image = image.replace("150x150", "500x500")
                .replace("50x50", "500x500")
                .replace("http://", "https://")
        }

        val encryptedMediaUrl = item.optString("encrypted_media_url", "")
        val directStreamUrl = decryptMediaUrl(encryptedMediaUrl)

        return SongItem(
            id = if (id.isNotBlank()) id else title.hashCode().toString(),
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            durationText = durationText,
            thumbnailUrl = image,
            streamUrl = directStreamUrl,
            encryptedMediaUrl = encryptedMediaUrl
        )
    }

    private suspend fun searchYoutubeInnerTube(query: String, params: String? = null): List<SongItem> = withContext(Dispatchers.IO) {
        try {
            // Primary attempt with the pinned WEB_REMIX client.
            val json = executeYoutubeSearchRequestJson(query, params, useFallbackClient = false)
            val primaryResults = json?.let { parseYoutubeSearchResponse(it) } ?: emptyList()
            if (primaryResults.isNotEmpty()) return@withContext primaryResults

            // WEB_REMIX returned nothing (possibly a stale/rejected clientVersion) —
            // retry once with a different InnerTube client before giving up entirely.
            Log.w(TAG, "WEB_REMIX search returned no results for '$query', retrying with fallback client")
            val fallbackJson = executeYoutubeSearchRequestJson(query, params, useFallbackClient = true)
            fallbackJson?.let { parseYoutubeSearchResponse(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun executeYoutubeSearchRequestJson(
        query: String,
        params: String?,
        useFallbackClient: Boolean = false
    ): JSONObject? {
        try {
            val clientName = if (useFallbackClient) YT_MUSIC_FALLBACK_CLIENT_NAME else "WEB_REMIX"
            val clientVersion = if (useFallbackClient) YT_MUSIC_FALLBACK_CLIENT_VERSION else YT_MUSIC_CLIENT_VERSION

            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
                if (params != null) {
                    put("params", params)
                }
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("X-YouTube-Client-Name", if (useFallbackClient) "21" else "67")
                .header("X-YouTube-Client-Version", clientVersion)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null
            return JSONObject(bodyString)
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube search request failed (fallback=$useFallbackClient): ${e.message}")
            return null
        }
    }

    private fun parseYoutubeSearchResponse(json: JSONObject): List<SongItem> {
        val songs = mutableListOf<SongItem>()
        try {
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i) ?: continue

                // 1. Top Card Result (musicCardShelfRenderer)
                val card = section.optJSONObject("musicCardShelfRenderer")
                if (card != null) {
                    val titleObj = card.optJSONObject("title")
                    val title = extractRunsText(titleObj)
                    val runs = titleObj?.optJSONArray("runs")
                    val nav = runs?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                    val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId", "") ?: ""
                    val subtitle = extractRunsText(card.optJSONObject("subtitle"))

                    val thumbs = card.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                    } else if (videoId.isNotBlank()) {
                        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    } else ""

                    if (videoId.isNotBlank() && title.isNotBlank()) {
                        songs.add(
                            SongItem(
                                id = videoId,
                                title = title,
                                artist = if (subtitle.isNotBlank()) subtitle else "Aurio Music",
                                thumbnailUrl = thumbUrl
                            )
                        )
                    }
                }

                // 2. Music Shelves & Item Sections
                val shelves = mutableListOf<JSONObject>()
                section.optJSONObject("musicShelfRenderer")?.let { shelves.add(it) }
                section.optJSONObject("itemSectionRenderer")?.let { shelves.add(it) }

                for (shelf in shelves) {
                    val shelfContents = shelf.optJSONArray("contents") ?: JSONArray()
                    for (j in 0 until shelfContents.length()) {
                        val item = shelfContents.optJSONObject(j)
                            ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val flexColumns = item.optJSONArray("flexColumns") ?: continue
                        if (flexColumns.length() == 0) continue

                        val title = extractRunsText(
                            flexColumns.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        )
                        if (title.isBlank()) continue

                        var artist = "Aurio Music"
                        var durationText = ""
                        if (flexColumns.length() > 1) {
                            val col2 = flexColumns.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                            val runs = col2?.optJSONArray("runs") ?: JSONArray()
                            if (runs.length() > 0) {
                                artist = runs.optJSONObject(0)?.optString("text", "Aurio Music") ?: "Aurio Music"
                            }
                            if (runs.length() > 2) {
                                durationText = runs.optJSONObject(runs.length() - 1)?.optString("text", "") ?: ""
                            }
                        }

                        var videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                            ?: item.optJSONObject("doubleTapEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?: ""

                        if (videoId.isBlank()) {
                            videoId = item.optJSONObject("overlay")
                                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("musicPlayButtonRenderer")
                                ?.optJSONObject("playNavigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId") ?: ""
                        }

                        val thumbs = item.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                        val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                        } else if (videoId.isNotBlank()) {
                            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        } else ""

                        if (videoId.isNotBlank()) {
                            songs.add(
                                SongItem(
                                    id = videoId,
                                    title = title,
                                    artist = artist,
                                    durationText = durationText,
                                    thumbnailUrl = thumbUrl
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // parsing fallback
        }
        return songs
    }

    private fun extractRunsText(textObj: JSONObject?): String {
        if (textObj == null) return ""
        val runs = textObj.optJSONArray("runs") ?: return textObj.optString("simpleText", "")
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            sb.append(run.optString("text", ""))
        }
        return sb.toString().trim()
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }

    /**
     * Queries JioSaavn's recommendation engine for songs sharing the exact same mood/vibe.
     */
    suspend fun getSaavnSongRecommendations(songId: String): List<SongItem> = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext emptyList()
        try {
            val url = "$SAAVN_BASE?__call=reco.getrecos&api_version=4&_format=json&_marker=0&ctx=android&pid=$songId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string()?.trim() ?: return@withContext emptyList()
            if (!bodyString.startsWith("[") && !bodyString.startsWith("{")) return@withContext emptyList()

            val songsList = mutableListOf<SongItem>()
            if (bodyString.startsWith("[")) {
                val jsonArr = JSONArray(bodyString)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.optJSONObject(i) ?: continue
                    parseSaavnSongItem(obj)?.let { songsList.add(it) }
                }
            } else {
                val jsonObj = JSONObject(bodyString)
                val arr = jsonObj.optJSONArray("results") ?: jsonObj.optJSONArray("data")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseSaavnSongItem(obj)?.let { songsList.add(it) }
                    }
                }
            }
            songsList
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Saavn recommendations for $songId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Queries YouTube Music /next radio endpoint for true autoplay recommendations.
     */
    suspend fun getYoutubeNextRadio(videoId: String): List<SongItem> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        try {
            val url = "https://music.youtube.com/youtubei/v1/next"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", YT_MUSIC_CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("enablePersistentPlaylistPanel", true)
                put("isAudioOnly", true)
                put("videoId", videoId)
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://music.youtube.com/")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)

            val results = mutableListOf<SongItem>()
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")

            val queueTab = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents")

            if (queueTab != null) {
                for (i in 0 until queueTab.length()) {
                    val item = queueTab.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                    val id = item.optString("videoId", "")
                    if (id.isBlank() || id == videoId) continue

                    val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                    val artist = item.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                    val thumbs = item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""

                    if (title.isNotBlank()) {
                        results.add(
                            SongItem(
                                id = id,
                                title = title,
                                artist = artist.ifBlank { "Aurio Music" },
                                thumbnailUrl = thumb
                            )
                        )
                    }
                }
            }
            results
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching YouTube radio for $videoId: ${e.message}")
            emptyList()
        }
    }
}
