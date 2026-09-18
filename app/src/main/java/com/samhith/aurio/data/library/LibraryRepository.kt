package com.samhith.aurio.data.library

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.ui.home.ArtistProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Data model for a user-created or imported playlist.
 */
data class PlaylistData(
    val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val songs: List<SongItem> = emptyList(),
    val isSpotifyImport: Boolean = false,
    val spotifyUrl: String = ""
)

/**
 * Singleton Repository managing:
 * 1. Liked Songs: Persistent collection with full SongItem objects
 * 2. Playlists: Curated defaults + Custom user-created + Imported Spotify playlists
 * 3. Followed Artists: Persistent collection with reactive flows
 * 4. Spotify Importer: Parses playlist URLs and populates playlist tracks
 */
class LibraryRepository private constructor() {

    private val TAG = "LibraryRepository"
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // ─── Liked Songs ──────────────────────────────────────────────

    private val _likedSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val likedSongs: StateFlow<List<SongItem>> = _likedSongs.asStateFlow()

    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    // ─── Playlists ────────────────────────────────────────────────

    private val _playlists = MutableStateFlow<List<PlaylistData>>(emptyList())
    val playlists: StateFlow<List<PlaylistData>> = _playlists.asStateFlow()

    // ─── Followed Artists ─────────────────────────────────────────

    private val _followedArtists = MutableStateFlow<List<ArtistProfile>>(emptyList())
    val followedArtists: StateFlow<List<ArtistProfile>> = _followedArtists.asStateFlow()

    private val _followedArtistNames = MutableStateFlow<Set<String>>(emptySet())
    val followedArtistNames: StateFlow<Set<String>> = _followedArtistNames.asStateFlow()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences("aurio_library_prefs", Context.MODE_PRIVATE)
        loadLikedSongs()
        loadPlaylists()
        loadFollowedArtists()
    }

    // ═══════════════════════════════════════════════════════════════
    // ─── Liked Songs API ──────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    fun toggleLikeSong(song: SongItem) {
        val currentIds = _likedSongIds.value.toMutableSet()
        val currentSongs = _likedSongs.value.toMutableList()

        if (currentIds.contains(song.id)) {
            currentIds.remove(song.id)
            currentSongs.removeAll { it.id == song.id }
            Log.d(TAG, "Unliked song: '${song.title}'")
        } else {
            currentIds.add(song.id)
            currentSongs.add(0, song) // Add to beginning (most recent first)
            Log.d(TAG, "Liked song: '${song.title}'")
        }

        _likedSongIds.value = currentIds
        _likedSongs.value = currentSongs
        persistLikedSongs()
    }

    fun isLiked(songId: String): Boolean = _likedSongIds.value.contains(songId)

    fun getLikedSongsCount(): Int = _likedSongs.value.size

    private fun persistLikedSongs() {
        scope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                for (song in _likedSongs.value) {
                    val obj = JSONObject().apply {
                        put("id", song.id)
                        put("title", song.title)
                        put("artist", song.artist)
                        put("album", song.album)
                        put("durationSeconds", song.durationSeconds)
                        put("durationText", song.durationText)
                        put("thumbnailUrl", song.thumbnailUrl)
                        if (!song.streamUrl.isNullOrBlank()) put("streamUrl", song.streamUrl)
                        if (!song.encryptedMediaUrl.isNullOrBlank()) put("encryptedMediaUrl", song.encryptedMediaUrl)
                    }
                    array.put(obj)
                }
                prefs?.edit()?.putString("liked_songs_data", array.toString())?.apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving liked songs: ${e.message}")
            }
        }
    }

    private fun loadLikedSongs() {
        val raw = prefs?.getString("liked_songs_data", null) ?: return
        try {
            val array = JSONArray(raw)
            val songs = mutableListOf<SongItem>()
            val ids = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val song = SongItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.optString("album", ""),
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    durationText = obj.optString("durationText", ""),
                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                    streamUrl = if (obj.has("streamUrl") && !obj.isNull("streamUrl")) obj.getString("streamUrl") else null,
                    encryptedMediaUrl = if (obj.has("encryptedMediaUrl") && !obj.isNull("encryptedMediaUrl")) obj.getString("encryptedMediaUrl") else null
                )
                songs.add(song)
                ids.add(song.id)
            }
            _likedSongs.value = songs
            _likedSongIds.value = ids
        } catch (e: Exception) {
            Log.e(TAG, "Error loading liked songs: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ─── Playlists API ────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    fun createPlaylist(name: String, description: String = ""): PlaylistData {
        val playlist = PlaylistData(
            id = "playlist_${System.currentTimeMillis()}",
            name = name,
            description = description
        )
        val current = _playlists.value.toMutableList()
        current.add(playlist)
        _playlists.value = current
        persistPlaylists()
        Log.d(TAG, "Created playlist: '$name'")
        return playlist
    }

    fun deletePlaylist(playlistId: String) {
        val current = _playlists.value.toMutableList()
        current.removeAll { it.id == playlistId }
        _playlists.value = current
        persistPlaylists()
    }

    fun addSongToPlaylist(playlistId: String, song: SongItem) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            if (playlist.songs.none { it.id == song.id }) {
                val updatedSongs = playlist.songs.toMutableList()
                updatedSongs.add(song)
                current[index] = playlist.copy(songs = updatedSongs)
                _playlists.value = current
                persistPlaylists()
                Log.d(TAG, "Added '${song.title}' to playlist '${playlist.name}'")
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            val updatedSongs = playlist.songs.toMutableList()
            updatedSongs.removeAll { it.id == songId }
            current[index] = playlist.copy(songs = updatedSongs)
            _playlists.value = current
            persistPlaylists()
        }
    }

    // ─── Spotify Import ───────────────────────────────────────────

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun importSpotifyPlaylist(name: String, spotifyUrl: String, onResult: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val initialPlaylist = PlaylistData(
                    id = "spotify_${System.currentTimeMillis()}",
                    name = name.ifBlank { "Spotify Playlist" },
                    description = "Imported from Spotify",
                    isSpotifyImport = true,
                    spotifyUrl = spotifyUrl
                )

                // Scrape track details and cover art from Spotify embed page
                val resolvedPlaylist = resolveSpotifyTracks(initialPlaylist)

                withContext(Dispatchers.Main) {
                    val current = _playlists.value.toMutableList()
                    current.add(resolvedPlaylist)
                    _playlists.value = current
                    persistPlaylists()
                    onResult(true)
                    Log.d(TAG, "Imported Spotify playlist: '${resolvedPlaylist.name}' with ${resolvedPlaylist.songs.size} tracks")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Spotify import error: ${e.message}")
                withContext(Dispatchers.Main) {
                    val fallback = PlaylistData(
                        id = "spotify_${System.currentTimeMillis()}",
                        name = name.ifBlank { "Spotify Playlist" },
                        description = "Imported from Spotify",
                        isSpotifyImport = true,
                        spotifyUrl = spotifyUrl
                    )
                    val current = _playlists.value.toMutableList()
                    current.add(fallback)
                    _playlists.value = current
                    persistPlaylists()
                    onResult(true)
                }
            }
        }
    }

    private suspend fun resolveSpotifyTracks(playlist: PlaylistData): PlaylistData = withContext(Dispatchers.IO) {
        val inputUrl = playlist.spotifyUrl.trim()
        if (inputUrl.isBlank()) return@withContext playlist

        try {
            // 1. Follow redirects (e.g. spotify.link shorteners) to resolve the canonical Spotify URL
            val initialRequest = Request.Builder()
                .url(inputUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()

            val initialResponse = httpClient.newCall(initialRequest).execute()
            val finalUrl = initialResponse.request.url.toString()
            initialResponse.close()

            // 2. Extract item type (playlist, album, track) and Spotify ID
            val pattern = Regex("(playlist|album|track)[/:]([a-zA-Z0-9]+)")
            val match = pattern.find(finalUrl) ?: pattern.find(inputUrl)
            if (match == null) {
                Log.w(TAG, "Could not extract Spotify type/ID from URL: $finalUrl")
                return@withContext playlist
            }

            val itemType = match.groupValues[1]
            val itemId = match.groupValues[2]
            val embedUrl = "https://open.spotify.com/embed/$itemType/$itemId"
            Log.d(TAG, "Fetching Spotify embed from: $embedUrl")

            // 3. Fetch Spotify Embed HTML
            val embedRequest = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()

            val embedResponse = httpClient.newCall(embedRequest).execute()
            if (!embedResponse.isSuccessful) {
                Log.w(TAG, "Spotify embed response unsuccessful: ${embedResponse.code}")
                embedResponse.close()
                return@withContext playlist
            }

            val html = embedResponse.body?.string() ?: ""
            embedResponse.close()

            // 4. Extract <script id="__NEXT_DATA__" type="application/json"> payload
            val scriptRegex = Regex("<script\\s+id=[\"']__NEXT_DATA__[\"']\\s+type=[\"']application/json[\"']>(.*?)</script>")
            val scriptMatch = scriptRegex.find(html)
            if (scriptMatch == null) {
                Log.w(TAG, "Could not find __NEXT_DATA__ in Spotify embed HTML")
                return@withContext playlist
            }

            val jsonStr = scriptMatch.groupValues[1]
            val rootObj = JSONObject(jsonStr)
            val propsObj = rootObj.optJSONObject("props")
            val pageProps = propsObj?.optJSONObject("pageProps")
            val stateObj = pageProps?.optJSONObject("state")
            val dataObj = stateObj?.optJSONObject("data")
            val entity = dataObj?.optJSONObject("entity") ?: dataObj?.optJSONObject("track")

            if (entity == null) {
                Log.w(TAG, "Could not parse entity from Spotify JSON")
                return@withContext playlist
            }

            // Extract Name
            val scrapedName = entity.optString("name").ifBlank { entity.optString("title") }
            val finalName = if (playlist.name.isNotBlank() && playlist.name != "Spotify Playlist" && playlist.name != "My Playlist") {
                playlist.name
            } else if (scrapedName.isNotBlank()) {
                scrapedName
            } else {
                playlist.name.ifBlank { "Spotify Playlist" }
            }

            // Extract Cover Art
            val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            val scrapedCoverUrl = if (coverSources != null && coverSources.length() > 0) {
                coverSources.optJSONObject(0)?.optString("url", "") ?: ""
            } else {
                entity.optString("coverArtUrl", "")
            }

            // Extract Track List
            val trackListArray = entity.optJSONArray("trackList")
            val parsedSongs = mutableListOf<SongItem>()

            if (trackListArray != null && trackListArray.length() > 0) {
                for (i in 0 until trackListArray.length()) {
                    val tObj = trackListArray.getJSONObject(i)
                    val title = tObj.optString("title", "").trim()
                    if (title.isBlank()) continue
                    val subtitle = tObj.optString("subtitle", "").trim().ifBlank { "Unknown Artist" }
                    val durationMs = tObj.optLong("duration", 0L)
                    val durationSec = durationMs / 1000L
                    val durationText = if (durationSec > 0) {
                        String.format("%d:%02d", durationSec / 60, durationSec % 60)
                    } else ""

                    val songItem = SongItem(
                        id = "sp_${itemId}_$i",
                        title = title,
                        artist = subtitle,
                        album = finalName,
                        durationSeconds = durationSec,
                        durationText = durationText,
                        thumbnailUrl = scrapedCoverUrl
                    )
                    parsedSongs.add(songItem)
                }
            } else if (itemType == "track") {
                // Single track embed
                val title = entity.optString("name", "").ifBlank { entity.optString("title", "") }.trim()
                val subtitle = entity.optString("subtitle", "").ifBlank { entity.optString("artists", "") }.trim().ifBlank { "Unknown Artist" }
                val durationMs = entity.optLong("duration", 0L)
                val durationSec = durationMs / 1000L
                val durationText = if (durationSec > 0) {
                    String.format("%d:%02d", durationSec / 60, durationSec % 60)
                } else ""

                if (title.isNotBlank()) {
                    parsedSongs.add(
                        SongItem(
                            id = "sp_${itemId}_0",
                            title = title,
                            artist = subtitle,
                            album = finalName,
                            durationSeconds = durationSec,
                            durationText = durationText,
                            thumbnailUrl = scrapedCoverUrl
                        )
                    )
                }
            }

            Log.d(TAG, "Scraped ${parsedSongs.size} tracks from Spotify: '$finalName'")

            // 5. Pre-match first 5 tracks in background for instant playback readiness
            val musicRepository = MusicRepository.getInstance()
            val enrichedSongs = parsedSongs.mapIndexed { index, song ->
                if (index < 5) {
                    try {
                        val matches = musicRepository.search("${song.title} ${song.artist}")
                        val best = matches.firstOrNull()
                        if (best != null) {
                            song.copy(
                                id = best.id,
                                thumbnailUrl = if (best.thumbnailUrl.isNotBlank()) best.thumbnailUrl else song.thumbnailUrl,
                                streamUrl = best.streamUrl,
                                encryptedMediaUrl = best.encryptedMediaUrl
                            )
                        } else {
                            song
                        }
                    } catch (_: Exception) {
                        song
                    }
                } else {
                    song
                }
            }

            return@withContext playlist.copy(
                name = finalName,
                coverUrl = if (scrapedCoverUrl.isNotBlank()) scrapedCoverUrl else playlist.coverUrl,
                songs = enrichedSongs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving Spotify tracks: ${e.message}", e)
            return@withContext playlist
        }
    }

    private fun persistPlaylists() {
        scope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                for (playlist in _playlists.value) {
                    val obj = JSONObject().apply {
                        put("id", playlist.id)
                        put("name", playlist.name)
                        put("description", playlist.description)
                        put("coverUrl", playlist.coverUrl)
                        put("isSpotifyImport", playlist.isSpotifyImport)
                        put("spotifyUrl", playlist.spotifyUrl)
                        val songsArray = JSONArray()
                        for (song in playlist.songs) {
                            val songObj = JSONObject().apply {
                                put("id", song.id)
                                put("title", song.title)
                                put("artist", song.artist)
                                put("album", song.album)
                                put("durationSeconds", song.durationSeconds)
                                put("durationText", song.durationText)
                                put("thumbnailUrl", song.thumbnailUrl)
                                if (!song.streamUrl.isNullOrBlank()) put("streamUrl", song.streamUrl)
                                if (!song.encryptedMediaUrl.isNullOrBlank()) put("encryptedMediaUrl", song.encryptedMediaUrl)
                            }
                            songsArray.put(songObj)
                        }
                        put("songs", songsArray)
                    }
                    array.put(obj)
                }
                prefs?.edit()?.putString("user_playlists_data", array.toString())?.apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playlists: ${e.message}")
            }
        }
    }

    private fun loadPlaylists() {
        val loaded = mutableListOf<PlaylistData>()
        val raw = prefs?.getString("user_playlists_data", null)
        if (raw != null) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val songs = mutableListOf<SongItem>()
                    val songsArray = obj.optJSONArray("songs")
                    if (songsArray != null) {
                        for (j in 0 until songsArray.length()) {
                            val songObj = songsArray.getJSONObject(j)
                            songs.add(
                                SongItem(
                                    id = songObj.getString("id"),
                                    title = songObj.getString("title"),
                                    artist = songObj.getString("artist"),
                                    album = songObj.optString("album", ""),
                                    durationSeconds = songObj.optLong("durationSeconds", 0L),
                                    durationText = songObj.optString("durationText", ""),
                                    thumbnailUrl = songObj.optString("thumbnailUrl", ""),
                                    streamUrl = if (songObj.has("streamUrl") && !songObj.isNull("streamUrl")) songObj.getString("streamUrl") else null,
                                    encryptedMediaUrl = if (songObj.has("encryptedMediaUrl") && !songObj.isNull("encryptedMediaUrl")) songObj.getString("encryptedMediaUrl") else null
                                )
                            )
                        }
                    }
                    val playlistData = PlaylistData(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        coverUrl = obj.optString("coverUrl", ""),
                        songs = songs,
                        isSpotifyImport = obj.optBoolean("isSpotifyImport", false),
                        spotifyUrl = obj.optString("spotifyUrl", "")
                    )
                    loaded.add(playlistData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlists: ${e.message}")
            }
        }

        _playlists.value = loaded
    }

    // ═══════════════════════════════════════════════════════════════
    // ─── Followed Artists API ─────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    fun toggleFollowArtist(artist: ArtistProfile) {
        val currentNames = _followedArtistNames.value.toMutableSet()
        val currentArtists = _followedArtists.value.toMutableList()

        if (currentNames.contains(artist.name)) {
            currentNames.remove(artist.name)
            currentArtists.removeAll { it.name == artist.name }
            Log.d(TAG, "Unfollowed artist: '${artist.name}'")
        } else {
            currentNames.add(artist.name)
            currentArtists.add(0, artist)
            Log.d(TAG, "Followed artist: '${artist.name}'")
        }

        _followedArtistNames.value = currentNames
        _followedArtists.value = currentArtists
        persistFollowedArtists()
    }

    fun isFollowing(artistName: String): Boolean = _followedArtistNames.value.contains(artistName)

    fun getFollowedArtistsCount(): Int = _followedArtists.value.size

    private fun persistFollowedArtists() {
        scope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                for (artist in _followedArtists.value) {
                    val obj = JSONObject().apply {
                        put("name", artist.name)
                        put("imageUrl", artist.imageUrl)
                    }
                    array.put(obj)
                }
                prefs?.edit()?.putString("followed_artists_data", array.toString())?.apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving followed artists: ${e.message}")
            }
        }
    }

    private fun loadFollowedArtists() {
        val raw = prefs?.getString("followed_artists_data", null) ?: return
        try {
            val array = JSONArray(raw)
            val artists = mutableListOf<ArtistProfile>()
            val names = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val artist = ArtistProfile(
                    name = obj.getString("name"),
                    imageUrl = obj.optString("imageUrl", "")
                )
                artists.add(artist)
                names.add(artist.name)
            }
            _followedArtists.value = artists
            _followedArtistNames.value = names
        } catch (e: Exception) {
            Log.e(TAG, "Error loading followed artists: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ─── Sync with AudioPlayerManager ─────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called from AudioPlayerManager to keep liked song IDs in sync bidirectionally.
     */
    fun syncLikedSongIdsFromPlayer(playerIds: Set<String>) {
        // If player has IDs we don't know about, we can't reconstruct SongItem
        // This only removes songs that player says are no longer liked
        val currentIds = _likedSongIds.value
        if (currentIds != playerIds) {
            val removedIds = currentIds - playerIds
            if (removedIds.isNotEmpty()) {
                val currentSongs = _likedSongs.value.toMutableList()
                currentSongs.removeAll { it.id in removedIds }
                _likedSongs.value = currentSongs
                _likedSongIds.value = playerIds
                persistLikedSongs()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: LibraryRepository? = null

        fun getInstance(): LibraryRepository {
            return instance ?: synchronized(this) {
                instance ?: LibraryRepository().also { instance = it }
            }
        }
    }
}
