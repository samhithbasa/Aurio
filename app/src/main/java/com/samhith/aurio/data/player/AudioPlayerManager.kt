package com.samhith.aurio.data.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.samhith.aurio.data.download.DownloadManager
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import com.samhith.aurio.ui.components.highResArtworkUrl

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Singleton Audio Player Manager handling ExoPlayer lifecycle, playlist queue,
 * reactive playback states, dynamic recently played tracking, shuffle & repeat modes,
 * sleep timer, equalizer integration, and taste-based queue auto-enrichment.
 */
class AudioPlayerManager private constructor(private val appContext: Context) {

    private val TAG = "AurioPlayer"
    private val repository = MusicRepository.getInstance().apply { initialize(appContext) }
    private val downloadManager = DownloadManager.getInstance().apply { initialize(appContext) }
    private val libraryRepository = LibraryRepository.getInstance().apply { initialize(appContext) }
    val equalizerManager = EqualizerManager.getInstance(appContext)
    val musicHapticsManager = MusicHapticsManager.getInstance(appContext)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val prefs: SharedPreferences = appContext.getSharedPreferences("aurio_player_prefs", Context.MODE_PRIVATE)

    private val hapticAudioProcessor = HapticBeatAudioProcessor { rmsBass ->
        musicHapticsManager.onPcmBassSample(rmsBass)
    }

    /** "8D audio": makes the song orbit the listener. Reads its settings from this manager. */
    val spatialAudioManager = SpatialAudioManager.getInstance(appContext)
    private val spatialAudioProcessor = SpatialAudioProcessor(spatialAudioManager)

    private val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                // Haptics only measures the audio; the spatial effect then reshapes it
                .setAudioProcessors(arrayOf(hapticAudioProcessor, spatialAudioProcessor))
                .build()
        }
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)

    private val defaultDataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

    private val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
        .setDataSourceFactory(defaultDataSourceFactory)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Shuffle & Repeat Modes
    private val _isShuffleEnabled = MutableStateFlow(prefs.getBoolean("player_shuffle", false))
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(
        try {
            RepeatMode.valueOf(prefs.getString("player_repeat", RepeatMode.OFF.name) ?: RepeatMode.OFF.name)
        } catch (_: Exception) {
            RepeatMode.OFF
        }
    )
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // Playing from context label (e.g. "Recently Played", "For You", "Popular Right Now")
    private val _playingFrom = MutableStateFlow("Recently Played")
    val playingFrom: StateFlow<String> = _playingFrom.asStateFlow()

    // Liked Song IDs set for instant reactive like toggle
    private val _likedSongIds = MutableStateFlow<Set<String>>(loadLikedSongs())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    // Sleep Timer (remaining minutes, null when inactive, -1 when end-of-track mode)
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Unlimited Queue Mode — auto-enriches queue when nearing end
    private val _isUnlimitedQueueEnabled = MutableStateFlow(false)
    val isUnlimitedQueueEnabled: StateFlow<Boolean> = _isUnlimitedQueueEnabled.asStateFlow()

    private var originalQueue: List<SongItem> = emptyList()
    private var progressJob: Job? = null

    init {
        setupPlayerListener()
        startService()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: $isPlaying")
                _isPlaying.value = isPlaying
                musicHapticsManager.onPlaybackStateChanged(isPlaying)
                if (isPlaying) {
                    _isBuffering.value = false
                    startProgressTracker()
                    startService()
                    equalizerManager.bindAudioSession(exoPlayer.audioSessionId)
                    musicHapticsManager.bindAudioSession(exoPlayer.audioSessionId)
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // The reason separates "our code called pause()" from "the system took audio focus"
                val why = when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
                    else -> "reason=$reason"
                }
                Log.d(TAG, "playWhenReady=$playWhenReady ($why)")
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                val why = when (playbackSuppressionReason) {
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "TRANSIENT_AUDIO_FOCUS_LOSS"
                    else -> "reason=$playbackSuppressionReason"
                }
                Log.d(TAG, "Playback suppression: $why")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "Player STATE_BUFFERING")
                        _isBuffering.value = true
                    }
                    Player.STATE_READY -> {
                        Log.d(TAG, "Player STATE_READY, duration: ${exoPlayer.duration}ms")
                        _isBuffering.value = false
                        _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                        equalizerManager.bindAudioSession(exoPlayer.audioSessionId)
                        musicHapticsManager.bindAudioSession(exoPlayer.audioSessionId)
                    }
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Player STATE_ENDED -> handling end of track")
                        _isBuffering.value = false
                        handleTrackEnded()
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "Player STATE_IDLE")
                        _isBuffering.value = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer Error [${error.errorCodeName}]: ${error.message}", error)
                _isBuffering.value = false
                _isPlaying.value = false
                val current = _currentSong.value
                if (current != null) {
                    scope.launch {
                        delay(1200)
                        playNext()
                    }
                }
            }
        })
    }

    private fun handleTrackEnded() {
        // Check if sleep timer was set for "End of current track"
        if (_sleepTimerMinutes.value == -1) {
            cancelSleepTimer()
            pause()
            return
        }

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0)
                resume()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                val q = _queue.value
                if (_currentIndex.value < q.size - 1) {
                    playNext()
                } else if (_isUnlimitedQueueEnabled.value) {
                    // Unlimited queue: auto-enrich and keep playing
                    scope.launch {
                        autoEnrichQueue(autoPlayNext = true)
                    }
                } else {
                    // No more tracks and unlimited queue is off — stop
                    pause()
                }
            }
        }
    }

    private fun startService() {
        try {
            val intent = Intent(appContext, AurioAudioService::class.java)
            appContext.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Service start error: ${e.message}")
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _playbackPositionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        _durationMs.value = dur
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
    }

    fun setPlayingFrom(source: String) {
        _playingFrom.value = source
    }

    /**
     * Plays a single track directly, sets it as the active item, and auto-populates
     * the upcoming queue with similar tracks matching the song & user taste.
     */
    fun playSong(song: SongItem, source: String = "Aurio Radio") {
        Log.d(TAG, "playSong called for: '${song.title}' (${song.id}) from $source")
        _playingFrom.value = source
        _queue.value = listOf(song)
        _currentIndex.value = 0
        originalQueue = listOf(song)
        playInternal(song)

        // Asynchronously populate upcoming queue with similar songs & taste tracks
        scope.launch {
            try {
                val similarTracks = repository.getSimilarAndTasteTracks(
                    seedSong = song,
                    limit = 15,
                    excludeIds = setOf(song.id)
                )
                if (similarTracks.isNotEmpty() && _currentSong.value?.id == song.id) {
                    withContext(Dispatchers.Main) {
                        val updated = listOf(song) + similarTracks
                        _queue.value = updated
                        originalQueue = updated.toList()
                        Log.d(TAG, "Populated smart radio queue with ${similarTracks.size} similar tracks for '${song.title}'")
                        prefetchUpcomingTracks(3)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error generating similar tracks queue: ${e.message}")
            }
        }
    }

    /**
     * Replaces the current queue with a list of songs and starts playing from startIndex.
     */
    fun playQueue(songs: List<SongItem>, startIndex: Int = 0, source: String = "Playlist") {
        if (songs.isEmpty()) return
        Log.d(TAG, "playQueue called with ${songs.size} songs, startIndex: $startIndex from $source")
        _playingFrom.value = source
        originalQueue = songs.toList()
        _queue.value = if (_isShuffleEnabled.value) {
            val selected = songs.getOrElse(startIndex) { songs.first() }
            val remaining = songs.filter { it.id != selected.id }.shuffled()
            listOf(selected) + remaining
        } else {
            songs
        }
        val validIndex = if (_isShuffleEnabled.value) 0 else startIndex.coerceIn(0, songs.size - 1)
        _currentIndex.value = validIndex
        playInternal(_queue.value[validIndex])
    }

    /**
     * Adds a song to the end of the current queue.
     */
    fun addToQueue(song: SongItem) {
        val currentList = _queue.value.toMutableList()
        if (currentList.none { it.id == song.id }) {
            currentList.add(song)
            _queue.value = currentList
            originalQueue = currentList.toList()
            Log.d(TAG, "Added '${song.title}' to queue (Total: ${currentList.size})")
        }
    }

    /**
     * Inserts a song immediately next in line after the current track.
     */
    fun playNextInQueue(song: SongItem) {
        val currentList = _queue.value.toMutableList()
        currentList.removeAll { it.id == song.id }
        val insertIndex = (_currentIndex.value + 1).coerceAtMost(currentList.size)
        currentList.add(insertIndex, song)
        _queue.value = currentList
        originalQueue = currentList.toList()
        Log.d(TAG, "Queued '${song.title}' to play next at position $insertIndex")
    }

    /**
     * Reorders an item in the queue from fromIndex to toIndex in real time.
     */
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val current = _queue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val currentActiveSong = _currentSong.value
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _queue.value = current
            originalQueue = current.toList()
            // Update currentIndex to follow the active song
            val newActiveIndex = current.indexOfFirst { it.id == currentActiveSong?.id }
            if (newActiveIndex != -1) {
                _currentIndex.value = newActiveIndex
            }
        }
    }

    /**
     * Removes an item from the queue at the specified index.
     */
    fun removeFromQueue(index: Int) {
        val current = _queue.value.toMutableList()
        if (index in current.indices) {
            val removingActive = index == _currentIndex.value
            current.removeAt(index)
            _queue.value = current
            originalQueue = current.toList()
            if (removingActive) {
                if (current.isNotEmpty()) {
                    val nextIdx = index.coerceIn(0, current.size - 1)
                    _currentIndex.value = nextIdx
                    playInternal(current[nextIdx])
                } else {
                    exoPlayer.stop()
                    _currentSong.value = null
                    _isPlaying.value = false
                }
            } else if (index < _currentIndex.value) {
                _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
            }
        }
    }

    /**
     * Jumps directly to a specific track in the queue.
     */
    fun jumpToQueueIndex(index: Int) {
        val q = _queue.value
        if (index in q.indices) {
            _currentIndex.value = index
            playInternal(q[index])
        }
    }

    /**
     * Toggles shuffle mode. Shuffles upcoming tracks while preserving current track.
     */
    fun toggleShuffle() {
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        prefs.edit().putBoolean("player_shuffle", newShuffle).apply()

        val q = _queue.value
        val active = _currentSong.value ?: q.getOrNull(_currentIndex.value)
        if (q.isNotEmpty() && active != null) {
            if (newShuffle) {
                if (originalQueue.isEmpty()) {
                    originalQueue = q.toList()
                }
                val others = q.filter { it.id != active.id }.shuffled()
                val shuffled = listOf(active) + others
                _queue.value = shuffled
                _currentIndex.value = 0
            } else {
                if (originalQueue.isNotEmpty()) {
                    val originalIdx = originalQueue.indexOfFirst { it.id == active.id }
                    _queue.value = originalQueue
                    _currentIndex.value = if (originalIdx != -1) originalIdx else 0
                }
            }
        }
    }

    /**
     * Cycles repeat mode: OFF -> ALL -> ONE -> OFF.
     */
    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = nextMode
        prefs.edit().putString("player_repeat", nextMode.name).apply()
    }

    /**
     * Toggles like state for the current song or a given song.
     */
    fun toggleLikeSong(song: SongItem) {
        val currentSet = _likedSongIds.value.toMutableSet()
        if (currentSet.contains(song.id)) {
            currentSet.remove(song.id)
        } else {
            currentSet.add(song.id)
        }
        _likedSongIds.value = currentSet
        saveLikedSongs(currentSet)
        // Sync with LibraryRepository for full SongItem persistence
        libraryRepository.toggleLikeSong(song)
    }

    private fun loadLikedSongs(): Set<String> {
        val raw = prefs.getString("liked_songs_ids", null) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                set.add(array.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveLikedSongs(set: Set<String>) {
        try {
            val array = JSONArray()
            for (id in set) array.put(id)
            prefs.edit().putString("liked_songs_ids", array.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Sets a sleep timer in minutes (e.g. 15, 30, 45, 60, or -1 for end of track).
     */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes

        if (minutes > 0) {
            sleepTimerJob = scope.launch {
                var remainingSec = minutes * 60
                while (remainingSec > 0 && isActive) {
                    delay(1000)
                    remainingSec--
                    _sleepTimerMinutes.value = (remainingSec / 60) + if (remainingSec % 60 > 0) 1 else 0
                }
                if (isActive) {
                    _sleepTimerMinutes.value = null
                    pause()
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = null
    }

    /**
     * Auto-enriches the queue with similar tracks based on active/recent song and listening taste.
     */
    private suspend fun autoEnrichQueue(autoPlayNext: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val currentQ = _queue.value
            val existingIds = currentQ.map { it.id }.toSet()
            val anchorSong = _currentSong.value ?: currentQ.lastOrNull()

            val newTracks = if (anchorSong != null) {
                repository.getSimilarAndTasteTracks(
                    seedSong = anchorSong,
                    limit = 8,
                    excludeIds = existingIds
                )
            } else {
                repository.getForYouTracks(forceRefresh = false).filter { !existingIds.contains(it.id) }.take(8)
            }

            if (newTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val updated = _queue.value + newTracks
                    _queue.value = updated
                    originalQueue = updated.toList()
                    Log.d(TAG, "Auto-enriched queue with ${newTracks.size} new tracks (Total: ${updated.size})")
                    if (autoPlayNext && updated.isNotEmpty()) {
                        val nextIdx = (_currentIndex.value + 1).coerceIn(0, updated.size - 1)
                        _currentIndex.value = nextIdx
                        playInternal(updated[nextIdx])
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error auto-enriching queue: ${e.message}")
        }
    }

    private var prefetchJob: Job? = null

    /**
     * Prefetches and caches resolved stream URLs for the next [count] songs in the queue,
     * ensuring immediate and seamless transition between tracks.
     */
    private fun prefetchUpcomingTracks(count: Int = 3) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            val q = _queue.value
            if (q.isEmpty()) return@launch
            val active = _currentSong.value
            val currentIdx = if (active != null) {
                val found = q.indexOfFirst { it.id == active.id }
                if (found != -1) found else _currentIndex.value
            } else {
                _currentIndex.value
            }

            for (offset in 1..count) {
                val targetIdx = currentIdx + offset
                if (targetIdx < q.size) {
                    val track = q[targetIdx]
                    if (track.streamUrl.isNullOrBlank()) {
                        try {
                            val resolved = repository.resolveStreamUrl(track)
                            if (!resolved.streamUrl.isNullOrBlank()) {
                                withContext(Dispatchers.Main) {
                                    val currentQ = _queue.value.toMutableList()
                                    val idx = currentQ.indexOfFirst { it.id == track.id }
                                    if (idx != -1) {
                                        currentQ[idx] = resolved
                                        _queue.value = currentQ
                                        originalQueue = currentQ.toList()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Prefetch failed for '${track.title}': ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun playInternal(song: SongItem) {
        _currentSong.value = song
        _isBuffering.value = true
        _playbackPositionMs.value = 0L
        _durationMs.value = if (song.durationSeconds > 0) song.durationSeconds * 1000L else 0L

        // Track in dynamic Recently Played
        repository.addToRecentlyPlayed(song)

        // Check for locally downloaded file first (offline playback)
        val localPath = downloadManager.getDownloadedFilePath(song.id)
        if (localPath != null) {
            val localFile = java.io.File(localPath)
            if (localFile.exists() && localFile.length() > 0) {
                Log.d(TAG, "Playing local downloaded file for '${song.title}': ${localFile.absolutePath} (${localFile.length()} bytes)")
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(if (song.thumbnailUrl.isNotBlank()) Uri.parse(highResArtworkUrl(song.thumbnailUrl)) else null)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(localFile))
                    .setMediaMetadata(mediaMetadata)
                    .build()

                exoPlayer.stop()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.play()
                startService()
                equalizerManager.bindAudioSession(exoPlayer.audioSessionId)
                musicHapticsManager.bindAudioSession(exoPlayer.audioSessionId)
                prefetchUpcomingTracks(3)
                return
            }
        }

        if (!song.streamUrl.isNullOrBlank()) {
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(if (song.thumbnailUrl.isNotBlank()) Uri.parse(highResArtworkUrl(song.thumbnailUrl)) else null)
                .build()

            val streamUri = if (song.streamUrl.startsWith("/")) {
                Uri.fromFile(java.io.File(song.streamUrl))
            } else {
                Uri.parse(song.streamUrl)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(streamUri)
                .setMediaMetadata(mediaMetadata)
                .build()

            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
            startService()
            equalizerManager.bindAudioSession(exoPlayer.audioSessionId)
            musicHapticsManager.bindAudioSession(exoPlayer.audioSessionId)
            prefetchUpcomingTracks(3)
        } else {
            scope.launch(Dispatchers.IO) {
                val resolvedSong = repository.resolveStreamUrl(song)
                Log.d(TAG, "playInternal streamUrl resolved: ${resolvedSong.streamUrl}")

                withContext(Dispatchers.Main) {
                    if (_currentSong.value?.id == song.id) {
                        _currentSong.value = resolvedSong
                        val streamUrl = resolvedSong.streamUrl
                        if (!streamUrl.isNullOrBlank()) {
                            val mediaMetadata = MediaMetadata.Builder()
                                .setTitle(resolvedSong.title)
                                .setArtist(resolvedSong.artist)
                                .setAlbumTitle(resolvedSong.album)
                                .setArtworkUri(if (resolvedSong.thumbnailUrl.isNotBlank()) Uri.parse(highResArtworkUrl(resolvedSong.thumbnailUrl)) else null)
                                .build()

                            val mediaItem = MediaItem.Builder()
                                .setUri(streamUrl)
                                .setMediaMetadata(mediaMetadata)
                                .build()

                            exoPlayer.stop()
                            exoPlayer.setMediaItem(mediaItem)
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                            startService()
                            equalizerManager.bindAudioSession(exoPlayer.audioSessionId)
                            musicHapticsManager.bindAudioSession(exoPlayer.audioSessionId)
                            Log.d(TAG, "ExoPlayer prepared and play() invoked for '${resolvedSong.title}'")
                            prefetchUpcomingTracks(3)
                        } else {
                            Log.e(TAG, "Cannot play: stream URL is null or empty for '${resolvedSong.title}'")
                            _isBuffering.value = false
                        }
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.mediaItemCount == 0) {
                val current = _currentSong.value
                if (current != null) {
                    playInternal(current)
                }
            } else {
                exoPlayer.play()
                startService()
                equalizerManager.bindAudioSession(exoPlayer.audioSessionId)
            }
        }
    }

    fun pause() {
        // Record who asked, so an unexpected pause can be traced straight to its caller
        val caller = Throwable().stackTrace.getOrNull(1)
        Log.d(TAG, "pause() requested by ${caller?.className?.substringAfterLast('.')}.${caller?.methodName}")
        exoPlayer.pause()
    }

    fun resume() {
        exoPlayer.play()
        startService()
        equalizerManager.bindAudioSession(exoPlayer.audioSessionId)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackPositionMs.value = positionMs
    }

    fun seekToFraction(fraction: Float) {
        val dur = exoPlayer.duration
        if (dur > 0) {
            val target = (dur * fraction.coerceIn(0f, 1f)).toLong()
            seekTo(target)
        }
    }

    fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        // Always find current active song dynamically in queue to respect latest shuffle / reorders
        val active = _currentSong.value
        val currentIdx = if (active != null) {
            val found = q.indexOfFirst { it.id == active.id }
            if (found != -1) found else _currentIndex.value
        } else {
            _currentIndex.value
        }

        val nextIdx = (currentIdx + 1) % q.size
        _currentIndex.value = nextIdx
        playInternal(q[nextIdx])

        // If unlimited queue enabled and queue is nearing end, auto-enrich
        if (_isUnlimitedQueueEnabled.value && nextIdx >= q.size - 3) {
            scope.launch {
                autoEnrichQueue(autoPlayNext = false)
            }
        }
    }

    fun toggleUnlimitedQueue() {
        _isUnlimitedQueueEnabled.value = !_isUnlimitedQueueEnabled.value
        Log.d(TAG, "Unlimited queue toggled: ${_isUnlimitedQueueEnabled.value}")
        // If just enabled and we're near end, enrich immediately
        if (_isUnlimitedQueueEnabled.value) {
            val q = _queue.value
            if (_currentIndex.value >= q.size - 3) {
                scope.launch {
                    autoEnrichQueue(autoPlayNext = false)
                }
            }
        }
    }

    fun playPrevious(forcePreviousTrack: Boolean = false) {
        val q = _queue.value
        if (q.isEmpty()) return
        if (!forcePreviousTrack && exoPlayer.currentPosition > 3000) {
            exoPlayer.seekTo(0)
            _playbackPositionMs.value = 0L
            return
        }

        val active = _currentSong.value
        val currentIdx = if (active != null) {
            val found = q.indexOfFirst { it.id == active.id }
            if (found != -1) found else _currentIndex.value
        } else {
            _currentIndex.value
        }

        val prevIdx = if (currentIdx - 1 < 0) q.size - 1 else currentIdx - 1
        _currentIndex.value = prevIdx
        playInternal(q[prevIdx])
    }

    /**
     * Fully releases native player/effect resources and tears down this singleton instance.
     * Must be called when AurioAudioService is truly being destroyed (not just backgrounded),
     * so decoder/effect resources aren't leaked across repeated service restarts. After calling
     * this, the next getInstance() call creates a brand new AudioPlayerManager + ExoPlayer.
     */
    fun release() {
        Log.d(TAG, "Releasing AudioPlayerManager: ExoPlayer + effects")
        try {
            stopProgressTracker()
            sleepTimerJob?.cancel()
            prefetchJob?.cancel()
        } catch (_: Exception) {}
        try {
            equalizerManager.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing equalizerManager: ${e.message}")
        }
        try {
            musicHapticsManager.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing musicHapticsManager: ${e.message}")
        }
        try {
            exoPlayer.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing exoPlayer: ${e.message}")
        }
        scope.coroutineContext[Job]?.cancel()
        synchronized(this) {
            if (instance === this) instance = null
        }
    }

    companion object {
        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
