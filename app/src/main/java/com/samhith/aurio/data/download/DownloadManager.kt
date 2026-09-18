package com.samhith.aurio.data.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SongItem
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Represents the current state of a single download operation.
 */
data class DownloadState(
    val songId: String,
    val progress: Float = 0f,       // 0.0 to 1.0
    val status: DownloadStatus = DownloadStatus.ENQUEUED,
    val localPath: String = "",
    val song: SongItem? = null
)

enum class DownloadStatus {
    ENQUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

/**
 * Background Downloader Engine using OkHttpClient.
 *
 * Downloads audio stream files to app-private external storage:
 *   context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)/aurio_downloads/{song.id}.mp3
 *
 * Emits real-time progress via StateFlow<Map<String, DownloadState>>.
 * Persists downloaded song metadata in SharedPreferences.
 * Integrates with AudioPlayerManager for local file playback.
 */
class DownloadManager private constructor() {

    private val TAG = "DownloadManager"
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = mutableMapOf<String, Job>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    // ─── Download States ──────────────────────────────────────────

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // ─── Completed Downloads (persisted) ──────────────────────────

    private val _downloadedSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val downloadedSongs: StateFlow<List<SongItem>> = _downloadedSongs.asStateFlow()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadDownloadedSongs()
    }

    private fun getDownloadsDir(): File {
        val ctx = appContext ?: throw IllegalStateException("DownloadManager not initialized")
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "aurio_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getDownloadFile(songId: String): File {
        return File(getDownloadsDir(), "${songId}.mp3")
    }

    // ═══════════════════════════════════════════════════════════════
    // ─── Public API ───────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    /**
     * Starts downloading a song in the background.
     * Resolves stream URL if needed, then downloads the audio file.
     */
    fun downloadSong(song: SongItem) {
        if (isDownloaded(song.id)) {
            Log.d(TAG, "Song '${song.title}' already downloaded")
            return
        }

        // Cancel any existing download for this song
        activeJobs[song.id]?.cancel()

        // Set initial enqueued state
        updateState(song.id, DownloadState(
            songId = song.id,
            status = DownloadStatus.ENQUEUED,
            song = song
        ))

        val job = scope.launch {
            try {
                // Resolve stream URL if not available
                var downloadSong = song
                if (downloadSong.streamUrl.isNullOrBlank()) {
                    updateState(song.id, DownloadState(
                        songId = song.id,
                        progress = 0f,
                        status = DownloadStatus.DOWNLOADING,
                        song = song
                    ))
                    val repository = MusicRepository.getInstance()
                    downloadSong = repository.resolveStreamUrl(song)
                }

                val streamUrl = downloadSong.streamUrl
                if (streamUrl.isNullOrBlank()) {
                    updateState(song.id, DownloadState(
                        songId = song.id,
                        status = DownloadStatus.FAILED,
                        song = song
                    ))
                    Log.e(TAG, "Cannot download '${song.title}': no stream URL")
                    return@launch
                }

                // Start downloading
                updateState(song.id, DownloadState(
                    songId = song.id,
                    progress = 0f,
                    status = DownloadStatus.DOWNLOADING,
                    song = song
                ))

                val outputFile = getDownloadFile(song.id)
                val success = downloadFile(streamUrl, outputFile, song.id, song)

                if (success) {
                    val localPath = outputFile.absolutePath
                    updateState(song.id, DownloadState(
                        songId = song.id,
                        progress = 1f,
                        status = DownloadStatus.COMPLETED,
                        localPath = localPath,
                        song = song
                    ))

                    // Add to downloaded songs list
                    val current = _downloadedSongs.value.toMutableList()
                    if (current.none { it.id == song.id }) {
                        current.add(0, song)
                        _downloadedSongs.value = current
                    }
                    persistDownloadedSongs()
                    Log.d(TAG, "Successfully downloaded '${song.title}' to $localPath")
                } else {
                    updateState(song.id, DownloadState(
                        songId = song.id,
                        status = DownloadStatus.FAILED,
                        song = song
                    ))
                    // Clean up partial file
                    outputFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error for '${song.title}': ${e.message}")
                updateState(song.id, DownloadState(
                    songId = song.id,
                    status = DownloadStatus.FAILED,
                    song = song
                ))
            }
        }

        activeJobs[song.id] = job
    }

    /**
     * Downloads a file from URL to the output file, emitting progress updates.
     */
    private fun downloadFile(url: String, outputFile: File, songId: String, song: SongItem): Boolean {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()
            return false
        }

        val body = response.body ?: run {
            response.close()
            return false
        }

        val contentLength = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(outputFile)

        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                // Emit progress
                val progress = if (contentLength > 0) {
                    (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                } else {
                    // If content length unknown, use indeterminate progress
                    0.5f
                }

                updateState(songId, DownloadState(
                    songId = songId,
                    progress = progress,
                    status = DownloadStatus.DOWNLOADING,
                    song = song
                ))
            }

            outputStream.flush()
            return true
        } finally {
            inputStream.close()
            outputStream.close()
            response.close()
        }
    }

    /**
     * Cancels an active download.
     */
    fun cancelDownload(songId: String) {
        activeJobs[songId]?.cancel()
        activeJobs.remove(songId)
        val states = _downloadStates.value.toMutableMap()
        states.remove(songId)
        _downloadStates.value = states
        // Clean up partial file
        getDownloadFile(songId).delete()
    }

    /**
     * Removes a downloaded song from storage and records.
     */
    fun removeDownload(songId: String) {
        getDownloadFile(songId).delete()
        val current = _downloadedSongs.value.toMutableList()
        current.removeAll { it.id == songId }
        _downloadedSongs.value = current
        persistDownloadedSongs()

        val states = _downloadStates.value.toMutableMap()
        states.remove(songId)
        _downloadStates.value = states

        Log.d(TAG, "Removed download for song ID: $songId")
    }

    /**
     * Checks if a song is already downloaded and available offline.
     */
    fun isDownloaded(songId: String): Boolean {
        val file = getDownloadFile(songId)
        return file.exists() && file.length() > 0
    }

    /**
     * Gets the local file path for a downloaded song, or null if not downloaded.
     */
    fun getDownloadedFilePath(songId: String): String? {
        val file = getDownloadFile(songId)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /**
     * Returns the current download state for a song, or null if no download is active/completed.
     */
    fun getDownloadState(songId: String): DownloadState? {
        return _downloadStates.value[songId]
    }

    fun getDownloadedSongsCount(): Int = _downloadedSongs.value.size

    /**
     * Imports a local song file and optional thumbnail into the Aurio downloads storage.
     * Registers the song as a completed download so it appears in the Downloads section
     * and can be played offline immediately.
     */
    fun addLocalImport(
        title: String,
        artist: String,
        audioUri: android.net.Uri,
        thumbnailUri: android.net.Uri?,
        context: Context
    ): Boolean {
        try {
            val songId = "imported_${System.currentTimeMillis()}"
            val outputFile = getDownloadFile(songId)

            // Copy audio content
            context.contentResolver.openInputStream(audioUri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return false

            // Copy thumbnail if available
            var localThumbnailPath = ""
            if (thumbnailUri != null) {
                try {
                    val thumbFile = File(getDownloadsDir(), "${songId}_thumb.jpg")
                    context.contentResolver.openInputStream(thumbnailUri)?.use { input ->
                        FileOutputStream(thumbFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (thumbFile.exists() && thumbFile.length() > 0) {
                        localThumbnailPath = thumbFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy imported thumbnail: ${e.message}")
                }
            }

            // Estimate duration or extract if possible
            var durationSec = 0L
            var durationText = ""
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(outputFile.absolutePath)
                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    val durMs = durStr.toLongOrNull() ?: 0L
                    durationSec = durMs / 1000L
                    val mins = durationSec / 60
                    val secs = durationSec % 60
                    durationText = String.format("%d:%02d", mins, secs)
                }
                retriever.release()
            } catch (_: Exception) {}

            val song = SongItem(
                id = songId,
                title = title.trim().ifBlank { "Imported Track" },
                artist = artist.trim().ifBlank { "Local Artist" },
                album = "Imported",
                durationSeconds = durationSec,
                durationText = durationText,
                thumbnailUrl = localThumbnailPath
            )

            updateState(song.id, DownloadState(
                songId = song.id,
                progress = 1f,
                status = DownloadStatus.COMPLETED,
                localPath = outputFile.absolutePath,
                song = song
            ))

            val current = _downloadedSongs.value.toMutableList()
            if (current.none { it.id == song.id }) {
                current.add(0, song)
                _downloadedSongs.value = current
            }
            persistDownloadedSongs()
            Log.d(TAG, "Successfully registered local import '${song.title}' to ${outputFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing local song: ${e.message}", e)
            return false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ─── Internal State Management ────────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    private fun updateState(songId: String, state: DownloadState) {
        val states = _downloadStates.value.toMutableMap()
        states[songId] = state
        _downloadStates.value = states
    }

    // ═══════════════════════════════════════════════════════════════
    // ─── Persistence ──────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    private fun persistDownloadedSongs() {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val prefs = ctx.getSharedPreferences("aurio_download_prefs", Context.MODE_PRIVATE)
                val array = JSONArray()
                for (song in _downloadedSongs.value) {
                    val obj = JSONObject().apply {
                        put("id", song.id)
                        put("title", song.title)
                        put("artist", song.artist)
                        put("album", song.album)
                        put("durationSeconds", song.durationSeconds)
                        put("durationText", song.durationText)
                        put("thumbnailUrl", song.thumbnailUrl)
                        if (!song.encryptedMediaUrl.isNullOrBlank()) put("encryptedMediaUrl", song.encryptedMediaUrl)
                    }
                    array.put(obj)
                }
                prefs.edit().putString("downloaded_songs_data", array.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving downloaded songs: ${e.message}")
            }
        }
    }

    private fun loadDownloadedSongs() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("aurio_download_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("downloaded_songs_data", null) ?: return
            val array = JSONArray(raw)
            val songs = mutableListOf<SongItem>()
            val states = mutableMapOf<String, DownloadState>()

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
                    encryptedMediaUrl = if (obj.has("encryptedMediaUrl") && !obj.isNull("encryptedMediaUrl")) obj.getString("encryptedMediaUrl") else null
                )

                // Only keep if file still exists on disk
                val file = getDownloadFile(song.id)
                if (file.exists() && file.length() > 0) {
                    songs.add(song)
                    states[song.id] = DownloadState(
                        songId = song.id,
                        progress = 1f,
                        status = DownloadStatus.COMPLETED,
                        localPath = file.absolutePath,
                        song = song
                    )
                }
            }

            _downloadedSongs.value = songs
            _downloadStates.value = states
        } catch (e: Exception) {
            Log.e(TAG, "Error loading downloaded songs: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager().also { instance = it }
            }
        }
    }
}
