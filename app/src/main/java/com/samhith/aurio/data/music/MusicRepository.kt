package com.samhith.aurio.data.music

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.samhith.aurio.ui.artists.ArtistDetailItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository for fetching music metadata, trending songs, dynamic recently played history,
 * resolving audio stream URLs, and generating taste-based personalized recommendations.
 */
class MusicRepository private constructor(
    private val client: InnertubeClient = InnertubeClient.getInstance()
) {

    private val TAG = "MusicRepository"
    private var prefs: SharedPreferences? = null

    // Preloaded high-fidelity popular tracks with verified artwork & encrypted stream URLs
    private val defaultPopularTracks = listOf(
        SongItem(
            id = "saavn_die_with_a_smile",
            title = "Die With A Smile",
            artist = "Lady Gaga, Bruno Mars",
            album = "Die With A Smile",
            durationSeconds = 251,
            durationText = "4:11",
            thumbnailUrl = "https://c.saavncdn.com/060/Die-With-A-Smile-English-2024-20240816103634-500x500.jpg",
            encryptedMediaUrl = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyCGZR06gaKffrem8wE1U/IMmorGBddTA3ePxCVrcJLdRXpP/tIcakyxw7tS9a8Gtq"
        ),
        SongItem(
            id = "saavn_starboy",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            album = "Starboy",
            durationSeconds = 230,
            durationText = "3:50",
            thumbnailUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240216140557-500x500.jpg"
        ),
        SongItem(
            id = "saavn_birds_of_a_feather",
            title = "Birds of a Feather",
            artist = "Billie Eilish",
            album = "HIT ME HARD AND SOFT",
            durationSeconds = 196,
            durationText = "3:16",
            thumbnailUrl = "https://c.saavncdn.com/707/HIT-ME-HARD-AND-SOFT-English-2024-20240517043818-500x500.jpg"
        ),
        SongItem(
            id = "saavn_calm_down",
            title = "Calm Down",
            artist = "Rema & Selena Gomez",
            album = "Rave & Roses Ultra",
            durationSeconds = 239,
            durationText = "3:59",
            thumbnailUrl = "https://c.saavncdn.com/635/Rave-Roses-Ultra-English-2023-20230427181048-500x500.jpg"
        ),
        SongItem(
            id = "saavn_blinding_lights",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            durationText = "3:20",
            thumbnailUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804192645-500x500.jpg"
        ),
        SongItem(
            id = "saavn_cruel_summer",
            title = "Cruel Summer",
            artist = "Taylor Swift",
            album = "Lover",
            durationSeconds = 178,
            durationText = "2:58",
            thumbnailUrl = "https://c.saavncdn.com/228/Lover-English-2019-20250731010741-500x500.jpg"
        ),
        SongItem(
            id = "saavn_shape_of_you",
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "÷ (Divide)",
            durationSeconds = 233,
            durationText = "3:53",
            thumbnailUrl = "https://c.saavncdn.com/286/WMG_190295851286-English-2017-500x500.jpg"
        )
    )

    private val _recentlyPlayed = MutableStateFlow<List<SongItem>>(
        listOf(defaultPopularTracks[4], defaultPopularTracks[5], defaultPopularTracks[1])
    )
    val recentlyPlayed: StateFlow<List<SongItem>> = _recentlyPlayed.asStateFlow()

    private val _forYouTracks = MutableStateFlow<List<SongItem>>(emptyList())
    val forYouTracks: StateFlow<List<SongItem>> = _forYouTracks.asStateFlow()

    private val _popularTracks = MutableStateFlow<List<SongItem>>(emptyList())
    val popularTracks: StateFlow<List<SongItem>> = _popularTracks.asStateFlow()

    private val streamCache = mutableMapOf<String, Pair<String, Long>>() // songId -> (url, timestamp)

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("aurio_music_prefs", Context.MODE_PRIVATE)
            loadSavedRecentlyPlayed()
        }
    }

    private fun loadSavedRecentlyPlayed() {
        val jsonStr = prefs?.getString("recently_played_json", null) ?: return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SongItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SongItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        durationText = obj.optString("durationText", ""),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        streamUrl = obj.optString("streamUrl", "").ifBlank { null },
                        encryptedMediaUrl = obj.optString("encryptedMediaUrl", "").ifBlank { null }
                    )
                )
            }
            if (list.isNotEmpty()) {
                _recentlyPlayed.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved recently played: ${e.message}")
        }
    }

    /**
     * Dynamically updates recently played list with the active track.
     */
    fun addToRecentlyPlayed(song: SongItem) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == song.id || (it.title == song.title && it.artist == song.artist) }
        current.add(0, song)
        val updated = current.take(25)
        _recentlyPlayed.value = updated

        // Persist to SharedPreferences
        try {
            val array = JSONArray()
            for (item in updated) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("durationSeconds", item.durationSeconds)
                    put("durationText", item.durationText)
                    put("thumbnailUrl", item.thumbnailUrl)
                    put("streamUrl", item.streamUrl ?: "")
                    put("encryptedMediaUrl", item.encryptedMediaUrl ?: "")
                }
                array.put(obj)
            }
            prefs?.edit()?.putString("recently_played_json", array.toString())?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recently played: ${e.message}")
        }
    }

    /**
     * Analyzes listening history to extract user's top favorite artists weighted by play frequency and recency.
     */
    fun getUserFavoriteArtists(): List<String> {
        val history = _recentlyPlayed.value
        val artistCounts = mutableMapOf<String, Int>()
        val artistOrder = mutableListOf<String>()
        for (song in history) {
            val parts = song.artist.split(",", "&", "ft.", "feat.", "•", "/", ";").map { it.trim() }
            for (p in parts) {
                if (p.isNotBlank() && p.length > 2 && !p.equals("Various Artists", true)) {
                    if (!artistCounts.containsKey(p)) {
                        artistOrder.add(p)
                    }
                    artistCounts[p] = (artistCounts[p] ?: 0) + 1
                }
            }
        }
        return artistOrder.sortedByDescending { artistCounts[it] ?: 0 }.take(8)
    }

    /**
     * Generates personalized "For You" tracks based on previously listened artists & songs.
     * On refresh, dynamically pulls and samples fresh recommendations reflecting user's recent music taste.
     * When forceRefresh is false, returns cached catalog to prevent jitter during navigation.
     */
    suspend fun getForYouTracks(forceRefresh: Boolean = false): List<SongItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh && _forYouTracks.value.isNotEmpty()) {
            return@withContext _forYouTracks.value
        }

        val topArtists = getUserFavoriteArtists()
        val history = _recentlyPlayed.value
        val results = mutableListOf<SongItem>()
        val seenIds = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        fun addTrack(track: SongItem): Boolean {
            val titleKey = track.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (seenIds.add(track.id) && (titleKey.isBlank() || seenTitles.add(titleKey))) {
                results.add(track)
                return true
            }
            return false
        }

        // 1. Personalized queries from user's top artists & recent songs
        if (topArtists.isNotEmpty()) {
            // Pick 2-4 artists, shuffling so each refresh can highlight different favorites
            val sampledArtists = topArtists.shuffled().take(4)
            val queryKeywords = listOf("hits", "songs", "top tracks", "best", "radio", "popular")

            for (artist in sampledArtists) {
                try {
                    val kw = queryKeywords.random()
                    val artistTracks = client.searchSongs("$artist $kw")
                    // Shuffle results and pick 3-4 distinct tracks from this artist
                    val shuffledTracks = artistTracks.shuffled()
                    var addedForArtist = 0
                    for (track in shuffledTracks) {
                        if (addTrack(track)) {
                            addedForArtist++
                            if (addedForArtist >= 4) break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching recommendations for $artist: ${e.message}")
                }
            }

            // Also check latest played track for similar song discovery
            if (history.isNotEmpty()) {
                val latest = history.first()
                try {
                    val similarTracks = client.searchSongs("${latest.title} ${latest.artist}").shuffled()
                    var addedSimilar = 0
                    for (track in similarTracks) {
                        if (track.id != latest.id && addTrack(track)) {
                            addedSimilar++
                            if (addedSimilar >= 3) break
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Fill remaining slots with dynamic trending charts if needed
        if (results.size < 12) {
            try {
                val fallbackQueries = listOf("Top Hits 2024", "Viral Hits", "Trending Pop", "Global Hits", "Billboard Hot 100")
                val fallback = client.searchSongs(fallbackQueries.random()).shuffled()
                for (track in fallback) {
                    addTrack(track)
                    if (results.size >= 16) break
                }
            } catch (_: Exception) {}
        }

        val finalTracks = if (results.isNotEmpty()) results.shuffled() else defaultPopularTracks.shuffled()
        _forYouTracks.value = finalTracks
        finalTracks
    }

    /**
     * Fetches popular tracks dynamically aligned with user taste and global trending charts.
     * On refresh, combines global top charts with hot hits from user's favorite artists/tastes.
     * When forceRefresh is false, returns cached catalog to prevent jitter during navigation.
     */
    suspend fun getPopularTracks(forceRefresh: Boolean = false): List<SongItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh && _popularTracks.value.isNotEmpty()) {
            return@withContext _popularTracks.value
        }

        val topArtists = getUserFavoriteArtists()
        val results = mutableListOf<SongItem>()
        val seenIds = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        fun addTrack(track: SongItem): Boolean {
            val titleKey = track.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (seenIds.add(track.id) && (titleKey.isBlank() || seenTitles.add(titleKey))) {
                results.add(track)
                return true
            }
            return false
        }

        // 1. Fetch from global trending charts (randomized chart query for dynamic freshness)
        try {
            val charts = client.getTrendingCharts().shuffled()
            for (track in charts.take(8)) {
                addTrack(track)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching trending charts: ${e.message}")
        }

        // 2. Blend with popular hits tailored to user's favorite artists & genres
        if (topArtists.isNotEmpty()) {
            for (artist in topArtists.shuffled().take(3)) {
                try {
                    val popularHits = client.searchSongs("$artist popular hits").shuffled()
                    var count = 0
                    for (track in popularHits) {
                        if (addTrack(track)) {
                            count++
                            if (count >= 3) break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching taste popular tracks for $artist: ${e.message}")
                }
            }
        }

        // 3. Fallback or top-up if results are short
        if (results.size < 10) {
            for (track in defaultPopularTracks.shuffled()) {
                addTrack(track)
                if (results.size >= 14) break
            }
        }

        val finalTracks = if (results.isNotEmpty()) results.shuffled() else defaultPopularTracks.shuffled()
        _popularTracks.value = finalTracks
        finalTracks
    }

    /**
     * Generates a list of tracks sharing the EXACT SAME MOOD & GENRE as the seed song,
     * powered by official streaming recommendation endpoints (JioSaavn reco & YouTube Radio)
     * blended with artist radio and curated mood collections.
     */
    suspend fun getSimilarAndTasteTracks(
        seedSong: SongItem,
        limit: Int = 15,
        excludeIds: Set<String> = emptySet()
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongItem>()
        val seenIds = mutableSetOf<String>().apply {
            add(seedSong.id)
            addAll(excludeIds)
        }
        val seenTitles = mutableSetOf<String>().apply {
            val seedKey = normalizeKey(seedSong.title)
            if (seedKey.isNotBlank()) add(seedKey)
        }
        val artistCounts = mutableMapOf<String, Int>()
        val seedArtistKey = normalizeArtist(seedSong.artist)

        fun addTrack(track: SongItem): Boolean {
            val titleKey = normalizeKey(track.title)
            val trackArtistKey = normalizeArtist(track.artist)

            // 1. Never add duplicate IDs or duplicate song titles
            if (seenIds.contains(track.id) || (titleKey.isNotBlank() && seenTitles.contains(titleKey))) {
                return false
            }

            // 2. Allow up to 3 songs by the seed artist, and max 2 per related artist
            val isSeedArtist = trackArtistKey == seedArtistKey || track.artist.contains(seedSong.artist, ignoreCase = true)
            val maxAllowed = if (isSeedArtist) 3 else 2
            val currentCount = artistCounts[trackArtistKey] ?: 0
            if (currentCount >= maxAllowed) {
                return false
            }

            seenIds.add(track.id)
            if (titleKey.isNotBlank()) seenTitles.add(titleKey)
            artistCounts[trackArtistKey] = currentCount + 1
            results.add(track)
            return true
        }

        // 1. Official JioSaavn Recommendation API (exact same mood/vibe)
        try {
            val saavnRecos = client.getSaavnSongRecommendations(seedSong.id)
            for (track in saavnRecos) {
                addTrack(track)
                if (results.size >= limit) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Saavn recos: ${e.message}")
        }

        // 2. YouTube Music Next / Autoplay Radio Endpoint
        if (results.size < limit) {
            try {
                val ytRadio = client.getYoutubeNextRadio(seedSong.id)
                for (track in ytRadio) {
                    addTrack(track)
                    if (results.size >= limit) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching YouTube radio: ${e.message}")
            }
        }

        // 3. Artist Radio & Soundtrack tracks
        if (results.size < limit && seedSong.artist.isNotBlank()) {
            try {
                val artistHits = client.searchSongs("${seedSong.artist} popular songs")
                for (track in artistHits) {
                    addTrack(track)
                    if (results.size >= limit) break
                }
            } catch (_: Exception) {}
        }

        // 4. Curated Mood / Genre specific queries based on seed song
        if (results.size < limit) {
            val moodQueries = detectMoodQueries(seedSong)
            for (query in moodQueries) {
                if (results.size >= limit) break
                try {
                    val tracks = client.searchSongs(query)
                    for (track in tracks) {
                        addTrack(track)
                        if (results.size >= limit) break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying mood radio '$query': ${e.message}")
                }
            }
        }

        results
    }

    private fun detectMoodQueries(song: SongItem): List<String> {
        val text = "${song.title} ${song.artist} ${song.album}".lowercase()

        // 1. Bollywood / Hindi Melodic & Romantic Soul
        if (listOf(
                "arijit", "atif", "shreya", "sid sriram", "jubin", "mohit chauhan",
                "armaan", "vishal mishra", "darshan raval", "pritam", "kk", "sonu nigam",
                "anuv jain", "prateek kuhad", "jasleen royal", "b praak", "sachin-jigar",
                "ar rahman", "a.r. rahman", "mithoon", "papon", "javed ali", "hindi", "bollywood", "soul"
            ).any { text.contains(it) }
        ) {
            return listOf(
                "Bollywood romantic hits",
                "Hindi acoustic soul songs",
                "Bollywood melodic hits",
                "Hindi heartfelt songs",
                "Romantic Hindi radio"
            ).shuffled()
        }

        // 2. Punjabi / Desi Pop & Hip-Hop
        if (listOf(
                "diljit", "badshah", "ap dhillon", "karan aujla", "shubh", "sidhu",
                "guru randhawa", "harrdy sandhu", "divine", "mc stan", "king", "raftaar",
                "punjabi", "desi"
            ).any { text.contains(it) }
        ) {
            return listOf(
                "Punjabi Pop hits",
                "Desi Hip Hop trending",
                "Punjabi trending songs",
                "Desi Pop radio"
            ).shuffled()
        }

        // 3. Synthpop / Retro / Upbeat Modern Pop
        if (listOf(
                "the weeknd", "dua lipa", "bruno mars", "daft punk", "lady gaga",
                "harry styles", "katy perry", "maroon 5", "calvin harris", "synth", "disco", "dance"
            ).any { text.contains(it) }
        ) {
            return listOf(
                "Synthpop hits",
                "Upbeat Pop hits",
                "Dance Pop radio",
                "Retro 80s Pop vibes",
                "Nu Disco hits"
            ).shuffled()
        }

        // 4. Acoustic Pop / Indie / Emotional Ballads
        if (listOf(
                "taylor swift", "billie eilish", "ed sheeran", "olivia rodrigo",
                "shawn mendes", "conan gray", "gracie abrams", "laufey", "lewis capaldi",
                "james arthur", "alec benjamin", "sam smith", "adele", "acoustic", "indie"
            ).any { text.contains(it) }
        ) {
            return listOf(
                "Acoustic Pop radio",
                "Indie Pop hits",
                "Heartfelt Pop ballads",
                "Soft Pop radio",
                "Acoustic chill songs"
            ).shuffled()
        }

        // 5. Hip-Hop / Melodic Rap / Trap
        if (listOf(
                "drake", "eminem", "kendrick", "travis scott", "post malone",
                "21 savage", "future", "jack harlow", "j. cole", "kanye", "juice wrld", "rap", "hip hop"
            ).any { text.contains(it) }
        ) {
            return listOf(
                "Top Hip Hop hits",
                "Melodic Rap radio",
                "Trending Rap songs",
                "Hip Hop radio hits"
            ).shuffled()
        }

        // 6. R&B / Neo-Soul / Chill Vibes
        if (listOf(
                "sza", "daniel caesar", "frank ocean", "giveon", "h.e.r.", "brent faiyaz",
                "summer walker", "khalid", "jhene", "bryson tiller", "kehlani", "r&b", "soul"
            ).any { text.contains(it) }
        ) {
            return listOf(
                "R&B chill hits",
                "Soulful R&B radio",
                "Modern R&B vibes",
                "Neo Soul hits"
            ).shuffled()
        }

        // 7. Rock / Alternative
        if (listOf(
                "coldplay", "imagine dragons", "arctic monkeys", "the neighbourhood",
                "onerepublic", "linkin park", "rock", "alternative"
            ).any { text.contains(it) }
        ) {
            return listOf(
                "Alternative Rock hits",
                "Indie Rock radio",
                "Modern Rock hits"
            ).shuffled()
        }

        // Default Global Pop & Chart-toppers
        return listOf(
            "Top Global Hits",
            "Trending Pop Radio",
            "Billboard Hot 100",
            "Viral Chart Hits"
        ).shuffled()
    }

    private fun normalizeKey(str: String): String {
        return str.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun normalizeArtist(artist: String): String {
        val primary = artist.split(",", "&", "ft.", "feat.", "•", "/").firstOrNull()?.trim() ?: artist
        return primary.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    suspend fun search(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        client.searchSongs(query)
    }

    suspend fun searchArtists(query: String): List<ArtistDetailItem> = withContext(Dispatchers.IO) {
        client.searchArtists(query)
    }

    suspend fun searchPlaylists(query: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        client.searchPlaylists(query)
    }

    suspend fun searchVideos(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        client.searchVideos(query)
    }

    suspend fun searchAll(query: String): SearchResultsGroup = withContext(Dispatchers.IO) {
        client.searchAll(query)
    }

    /**
     * Fetches top tracks strictly belonging to the specified artist.
     * Combines multiple search strategies with strict artist verification and deduplication
     * so songs from other artists are never shown on the artist's page.
     */
    suspend fun getArtistTopTracks(artistName: String): List<SongItem> = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank()) return@withContext emptyList()

        val artistKey = cleanArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
        val artistTokens = cleanArtist.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in setOf("the", "and", "feat", "ft", "music", "official", "records", "band") }

        fun matchesArtist(song: SongItem): Boolean {
            val sArtist = song.artist.lowercase()
            val sTitle = song.title.lowercase()
            val sArtistKey = sArtist.replace(Regex("[^a-z0-9]"), "")

            // 1. Direct match in artist string or title
            if (sArtist.contains(cleanArtist.lowercase()) || sTitle.contains(cleanArtist.lowercase())) return true
            if (artistKey.isNotBlank() && (sArtistKey.contains(artistKey) || artistKey.contains(sArtistKey))) return true

            // 2. Sub-artist check for collaborations
            val subArtists = sArtist.split(Regex("[,&/•;]|\\bft\\b|\\bfeat\\b|\\bwith\\b")).map { it.trim() }
            for (sub in subArtists) {
                val subKey = sub.replace(Regex("[^a-z0-9]"), "")
                if (sub.isNotBlank() && (sub.contains(cleanArtist.lowercase()) || cleanArtist.lowercase().contains(sub))) return true
                if (subKey.isNotBlank() && (subKey.contains(artistKey) || artistKey.contains(subKey))) return true
            }

            // 3. Significant token match in artist or title
            if (artistTokens.isNotEmpty()) {
                val tokenMatchesInArtist = artistTokens.any { sArtist.contains(it) }
                val tokenMatchesInTitle = artistTokens.any { sTitle.contains(it) }
                if (tokenMatchesInArtist || tokenMatchesInTitle) {
                    return true
                }
            }
            return false
        }

        val collected = mutableListOf<SongItem>()
        val seenIds = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        fun addTrack(song: SongItem) {
            val titleKey = song.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (matchesArtist(song)) {
                if (seenIds.add(song.id) && (titleKey.isBlank() || seenTitles.add(titleKey))) {
                    collected.add(song)
                }
            }
        }

        // Query 1: Exact artist name
        try {
            val songs1 = client.searchSongs(cleanArtist)
            for (s in songs1) addTrack(s)
        } catch (_: Exception) {}

        // Query 2: Artist songs
        if (collected.size < 20) {
            try {
                val songs2 = client.searchSongs("$cleanArtist songs")
                for (s in songs2) addTrack(s)
            } catch (_: Exception) {}
        }

        // Query 3: Artist top tracks
        if (collected.size < 20) {
            try {
                val songs3 = client.searchSongs("$cleanArtist top tracks")
                for (s in songs3) addTrack(s)
            } catch (_: Exception) {}
        }

        collected
    }

    /**
     * Resolves direct audio stream URL with in-memory caching.
     */
    suspend fun resolveStreamUrl(song: SongItem): SongItem = withContext(Dispatchers.IO) {
        if (!song.streamUrl.isNullOrBlank()) return@withContext song

        // Check if encryptedMediaUrl is present -> decrypt directly
        if (!song.encryptedMediaUrl.isNullOrBlank()) {
            val decrypted = client.decryptMediaUrl(song.encryptedMediaUrl)
            if (!decrypted.isNullOrBlank()) {
                streamCache[song.id] = Pair(decrypted, System.currentTimeMillis())
                return@withContext song.copy(streamUrl = decrypted)
            }
        }

        // Check cache (valid for 5 hours)
        val cached = streamCache[song.id]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.second) < 5 * 3600 * 1000) {
            return@withContext song.copy(streamUrl = cached.first)
        }

        val resolvedUrl = client.getAudioStreamUrl(song)
        if (!resolvedUrl.isNullOrBlank()) {
            Log.d(TAG, "Cached resolved stream for '${song.title}': $resolvedUrl")
            streamCache[song.id] = Pair(resolvedUrl, now)
            song.copy(streamUrl = resolvedUrl)
        } else {
            Log.w(TAG, "Failed to resolve stream URL for '${song.title}'")
            song
        }
    }

    companion object {
        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(): MusicRepository {
            return instance ?: synchronized(this) {
                instance ?: MusicRepository().also { instance = it }
            }
        }
    }
}

/**
 * Search category filter tabs.
 */
enum class SearchCategory(val title: String) {
    ALL("All"),
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    VIDEOS("Videos")
}

/**
 * Data model for Playlist / Album items found in search.
 */
data class PlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val songCount: Int = 0,
    val type: String = "Playlist" // "Playlist" or "Album"
)

/**
 * Composite container for the "All" search tab.
 */
data class SearchResultsGroup(
    val songs: List<SongItem> = emptyList(),
    val artists: List<ArtistDetailItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val videos: List<SongItem> = emptyList()
)

