package com.samhith.aurio.data.ai

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.samhith.aurio.AppNavScreen
import com.samhith.aurio.data.download.DownloadManager
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.data.player.EqualizerPreset
import com.samhith.aurio.data.player.HapticIntensity
import com.samhith.aurio.data.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A screen/panel the assistant wants the UI to open or close. Routed by MainActivity, because
 * these live in Compose state rather than in a manager the engine can touch directly.
 */
enum class AssistantUiAction {
    OPEN_FULL_PLAYER,
    CLOSE_FULL_PLAYER,
    OPEN_LYRICS,
    OPEN_QUEUE,
    OPEN_EQUALIZER,
    OPEN_HAPTICS,
    OPEN_SLEEP_TIMER,
    OPEN_SPOTIFY_IMPORT
}

data class AiExecutionResult(
    val spokenResponse: String,
    val bannerMessage: String? = null,
    val targetScreen: AppNavScreen? = null,
    val searchQuery: String? = null,
    val playedSong: SongItem? = null,
    val uiAction: AssistantUiAction? = null
)

sealed class AssistantIntent {
    data class PlaySong(val query: String) : AssistantIntent()
    data class PlayMood(val moodQuery: String, val moodName: String) : AssistantIntent()
    data class SearchMusic(val query: String) : AssistantIntent()
    data class QueueSong(val query: String, val playNext: Boolean) : AssistantIntent()
    object DownloadCurrentSong : AssistantIntent()
    object PauseMusic : AssistantIntent()
    object ResumeMusic : AssistantIntent()
    object NextTrack : AssistantIntent()
    object PreviousTrack : AssistantIntent()
    object RestartSong : AssistantIntent()
    data class SeekRelative(val seconds: Int) : AssistantIntent()
    object LikeCurrentSong : AssistantIntent()
    object UnlikeCurrentSong : AssistantIntent()
    data class SetShuffle(val enable: Boolean?) : AssistantIntent()
    data class SetRepeat(val mode: RepeatMode?) : AssistantIntent()
    object VolumeUp : AssistantIntent()
    object VolumeDown : AssistantIntent()
    object VolumeMax : AssistantIntent()
    data class SetVolumePercent(val percent: Int) : AssistantIntent()
    object Mute : AssistantIntent()
    object Unmute : AssistantIntent()
    data class SetHaptics(val enable: Boolean) : AssistantIntent()
    data class SetHapticsIntensity(val intensity: HapticIntensity) : AssistantIntent()
    data class SetEqualizerPreset(val preset: EqualizerPreset) : AssistantIntent()
    data class SetBassBoost(val percent: Int) : AssistantIntent()
    data class SetSleepTimer(val minutes: Int) : AssistantIntent()
    object CancelSleepTimer : AssistantIntent()
    data class SetUnlimitedQueue(val enable: Boolean) : AssistantIntent()
    data class OpenPanel(val action: AssistantUiAction) : AssistantIntent()
    data class NavigateScreen(val target: AppNavScreen) : AssistantIntent()
    data class CreatePlaylist(val name: String) : AssistantIntent()
    data class AddCurrentToPlaylist(val playlistName: String) : AssistantIntent()
    object WhatIsPlaying : AssistantIntent()
    object StopAssistant : AssistantIntent()
    data class WakeWordGreeting(val prompt: String) : AssistantIntent()
    data class GeneralChat(val responseText: String) : AssistantIntent()
}

/**
 * Intelligent Siri-Style NLP & Action Engine for Aurio AI Assistant.
 * Parses spoken natural language into playback, library, player-panel, equalizer, haptics,
 * sleep-timer, navigation and search intents with robust phonetic tolerance.
 */
class AurioAiAssistantEngine(
    private val context: Context,
    private val playerManager: AudioPlayerManager,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository
) {

    private val TAG = "AurioAiEngine"

    /** First thing the assistant says after the wake word. */
    fun getRandomGreeting(): String = "What's the vibe?"

    /**
     * Intents whose side effects are applied synchronously the moment they are recognized
     * (already at partial-result time) because they need no network round-trip. Everything
     * here must be safe to run on the main thread in well under a frame.
     */
    fun isInstantIntent(intent: AssistantIntent): Boolean = when (intent) {
        is AssistantIntent.PauseMusic,
        is AssistantIntent.ResumeMusic,
        is AssistantIntent.NextTrack,
        is AssistantIntent.PreviousTrack,
        is AssistantIntent.RestartSong,
        is AssistantIntent.SeekRelative,
        is AssistantIntent.LikeCurrentSong,
        is AssistantIntent.UnlikeCurrentSong,
        is AssistantIntent.SetShuffle,
        is AssistantIntent.SetRepeat,
        is AssistantIntent.VolumeUp,
        is AssistantIntent.VolumeDown,
        is AssistantIntent.VolumeMax,
        is AssistantIntent.SetVolumePercent,
        is AssistantIntent.Mute,
        is AssistantIntent.Unmute,
        is AssistantIntent.SetHaptics,
        is AssistantIntent.SetHapticsIntensity,
        is AssistantIntent.SetEqualizerPreset,
        is AssistantIntent.SetBassBoost,
        is AssistantIntent.SetSleepTimer,
        is AssistantIntent.CancelSleepTimer,
        is AssistantIntent.SetUnlimitedQueue,
        is AssistantIntent.OpenPanel,
        is AssistantIntent.NavigateScreen,
        is AssistantIntent.WhatIsPlaying,
        is AssistantIntent.StopAssistant -> true

        else -> false
    }

    /** True for intents that need the app window on screen to make sense (navigation, panels). */
    fun requiresAppForeground(intent: AssistantIntent): Boolean = when (intent) {
        is AssistantIntent.NavigateScreen,
        is AssistantIntent.SearchMusic,
        is AssistantIntent.OpenPanel -> true

        else -> false
    }

    /**
     * Applies an instant intent's side effect immediately, synchronously, on the caller's thread
     * (always the main thread). Returns true when the side effect ran, so [execute] knows not to
     * run it a second time.
     */
    fun applyInstantSideEffect(intent: AssistantIntent): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        when (intent) {
            is AssistantIntent.PauseMusic -> playerManager.pause()
            is AssistantIntent.ResumeMusic -> if (playerManager.currentSong.value != null) playerManager.resume()
            is AssistantIntent.NextTrack -> if (playerManager.queue.value.isNotEmpty()) playerManager.playNext()
            is AssistantIntent.PreviousTrack -> if (playerManager.queue.value.isNotEmpty()) playerManager.playPrevious(forcePreviousTrack = true)
            is AssistantIntent.RestartSong -> playerManager.seekTo(0L)
            is AssistantIntent.SeekRelative -> {
                val target = (playerManager.playbackPositionMs.value + intent.seconds * 1000L)
                    .coerceIn(0L, playerManager.durationMs.value.coerceAtLeast(0L))
                playerManager.seekTo(target)
            }

            is AssistantIntent.LikeCurrentSong -> {
                val song = playerManager.currentSong.value
                if (song != null && !playerManager.likedSongIds.value.contains(song.id)) {
                    playerManager.toggleLikeSong(song)
                }
            }

            is AssistantIntent.UnlikeCurrentSong -> {
                val song = playerManager.currentSong.value
                if (song != null && playerManager.likedSongIds.value.contains(song.id)) {
                    playerManager.toggleLikeSong(song)
                }
            }

            is AssistantIntent.SetShuffle -> {
                val target = intent.enable ?: !playerManager.isShuffleEnabled.value
                if (playerManager.isShuffleEnabled.value != target) playerManager.toggleShuffle()
            }

            is AssistantIntent.SetRepeat -> {
                val target = intent.mode ?: nextRepeatMode(playerManager.repeatMode.value)
                // toggleRepeat cycles OFF -> ALL -> ONE -> OFF; step until the target is reached.
                var guard = 0
                while (playerManager.repeatMode.value != target && guard < 3) {
                    playerManager.toggleRepeat()
                    guard++
                }
            }

            is AssistantIntent.VolumeUp -> audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            is AssistantIntent.VolumeDown -> audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            is AssistantIntent.Mute -> audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            is AssistantIntent.Unmute -> audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            is AssistantIntent.VolumeMax -> setVolumePercent(audioManager, 100)
            is AssistantIntent.SetVolumePercent -> setVolumePercent(audioManager, intent.percent)

            is AssistantIntent.SetHaptics -> playerManager.musicHapticsManager.setEnabled(intent.enable)
            is AssistantIntent.SetHapticsIntensity -> {
                playerManager.musicHapticsManager.setEnabled(true)
                playerManager.musicHapticsManager.setIntensity(intent.intensity)
            }

            is AssistantIntent.SetEqualizerPreset -> {
                playerManager.equalizerManager.setEnabled(true)
                playerManager.equalizerManager.setPreset(intent.preset)
            }

            is AssistantIntent.SetBassBoost -> {
                playerManager.equalizerManager.setEnabled(true)
                playerManager.equalizerManager.setBassBoost(intent.percent)
            }

            is AssistantIntent.SetSleepTimer -> playerManager.setSleepTimer(intent.minutes)
            is AssistantIntent.CancelSleepTimer -> playerManager.cancelSleepTimer()
            is AssistantIntent.SetUnlimitedQueue -> {
                if (playerManager.isUnlimitedQueueEnabled.value != intent.enable) {
                    playerManager.toggleUnlimitedQueue()
                }
            }

            else -> return false
        }
        return true
    }

    private fun setVolumePercent(audioManager: AudioManager?, percent: Int) {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent.coerceIn(0, 100) / 100f).toInt().coerceIn(0, max)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
    }

    private fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    /**
     * Strips wake-words (custom name or "hey aurio") and conversational fillers from user input.
     */
    fun cleanQuery(rawInput: String, customName: String? = null): String {
        var clean = rawInput.trim().lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        clean = clean.replace(Regex("\\s+"), " ").trim()

        // Strip wake words at start
        val wakeWords = mutableListOf(
            "hey aurio", "heyy aurio", "hay aurio", "hi aurio", "hii aurio", "hello aurio", "ok aurio", "okay aurio", "yo aurio", "aurio", "audio", "orio"
        )
        if (!customName.isNullOrBlank()) {
            val name = customName.trim().lowercase()
            wakeWords.add(0, "hey $name")
            wakeWords.add(1, "heyy $name")
            wakeWords.add(2, "hay $name")
            wakeWords.add(3, "hi $name")
            wakeWords.add(4, "hii $name")
            wakeWords.add(5, "hello $name")
            wakeWords.add(6, "ok $name")
            wakeWords.add(7, "okay $name")
            wakeWords.add(8, "yo $name")
            wakeWords.add(9, name)
        }

        for (w in wakeWords) {
            if (clean.startsWith(w)) {
                clean = clean.removePrefix(w).trim()
            }
        }

        // Strip conversational polite prefixes
        val prefixes = listOf(
            "can you please", "could you please", "please", "can you", "could you",
            "i want to listen to", "i wanna listen to", "i wanna hear", "i want to hear",
            "put on", "drop the beat and play", "stream", "search and play", "play for me",
            "just", "tell me to play", "go and play"
        )
        for (p in prefixes) {
            if (clean.startsWith(p)) {
                clean = clean.removePrefix(p).trim()
            }
        }

        return clean
    }

    /**
     * Extracts clean search query and display title from song requests.
     */
    fun extractSongQuery(raw: String): Pair<String, String> {
        var query = raw.trim().lowercase()
        val prefixes = listOf(
            "play a ", "play the ", "play some ", "play any ", "play ",
            "search for ", "search and play ", "search a ", "search the ", "search ",
            "find a ", "find the ", "find ",
            "look for ", "listen to ", "start ", "hear "
        )
        for (p in prefixes) {
            if (query.startsWith(p)) {
                query = query.removePrefix(p).trim()
                break
            }
        }
        if (query.startsWith("a ")) query = query.removePrefix("a ").trim()
        if (query.startsWith("the ")) query = query.removePrefix("the ").trim()

        // Remove trailing "song", "songs", "track", "tracks", "music"
        val cleanSearch = query
            .removeSuffix(" song")
            .removeSuffix(" songs")
            .removeSuffix(" track")
            .removeSuffix(" tracks")
            .removeSuffix(" music")
            .trim()

        val searchQuery = if (cleanSearch.isNotBlank()) cleanSearch else query
        val displayTitle = searchQuery.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        return Pair(searchQuery, displayTitle)
    }

    /**
     * Short spoken acknowledgment, spoken the instant the command is recognized. Kept to one or
     * two words wherever possible so the TTS never trails the action.
     */
    fun getImmediateSpokenResponse(intent: AssistantIntent): String {
        return when (intent) {
            is AssistantIntent.WakeWordGreeting -> intent.prompt
            is AssistantIntent.PlaySong -> {
                val (_, displayTitle) = extractSongQuery(intent.query)
                "Playing $displayTitle."
            }

            is AssistantIntent.PlayMood -> "Playing ${intent.moodName} music."
            is AssistantIntent.SearchMusic -> {
                val (_, displayTitle) = extractSongQuery(intent.query)
                "Searching $displayTitle."
            }

            is AssistantIntent.QueueSong -> if (intent.playNext) "Up next." else "Added to queue."
            is AssistantIntent.DownloadCurrentSong -> "Downloading."
            is AssistantIntent.PauseMusic -> "Paused."
            is AssistantIntent.ResumeMusic -> "Playing."
            is AssistantIntent.NextTrack -> "Next."
            is AssistantIntent.PreviousTrack -> "Previous."
            is AssistantIntent.RestartSong -> "From the top."
            is AssistantIntent.SeekRelative -> if (intent.seconds >= 0) "Forward." else "Back."
            is AssistantIntent.LikeCurrentSong -> "Liked."
            is AssistantIntent.UnlikeCurrentSong -> "Unliked."
            is AssistantIntent.VolumeUp -> "Volume up."
            is AssistantIntent.VolumeDown -> "Volume down."
            is AssistantIntent.VolumeMax -> "Max volume."
            is AssistantIntent.SetVolumePercent -> "Volume ${intent.percent}."
            is AssistantIntent.Mute -> "Muted."
            is AssistantIntent.Unmute -> "Unmuted."
            is AssistantIntent.SetHaptics -> if (intent.enable) "Haptics on." else "Haptics off."
            is AssistantIntent.SetHapticsIntensity -> "${intent.intensity.displayName} haptics."
            is AssistantIntent.SetEqualizerPreset -> "${intent.preset.displayName} preset."
            is AssistantIntent.SetBassBoost -> "Bass boosted."
            is AssistantIntent.SetSleepTimer -> if (intent.minutes > 0) "Sleep timer set." else "Timer set."
            is AssistantIntent.CancelSleepTimer -> "Timer off."
            is AssistantIntent.SetUnlimitedQueue -> if (intent.enable) "Endless queue on." else "Endless queue off."
            is AssistantIntent.SetShuffle -> when (intent.enable) {
                true -> "Shuffle on."
                false -> "Shuffle off."
                null -> "Shuffle toggled."
            }

            is AssistantIntent.SetRepeat -> when (intent.mode) {
                RepeatMode.ONE -> "Repeating this song."
                RepeatMode.ALL -> "Repeat all."
                RepeatMode.OFF -> "Repeat off."
                null -> "Repeat toggled."
            }

            is AssistantIntent.OpenPanel -> when (intent.action) {
                AssistantUiAction.OPEN_LYRICS -> "Here are the lyrics."
                AssistantUiAction.OPEN_QUEUE -> "Here's the queue."
                AssistantUiAction.OPEN_EQUALIZER -> "Opening equalizer."
                AssistantUiAction.OPEN_HAPTICS -> "Opening haptics."
                AssistantUiAction.OPEN_SLEEP_TIMER -> "Opening sleep timer."
                AssistantUiAction.OPEN_FULL_PLAYER -> "Opening player."
                AssistantUiAction.CLOSE_FULL_PLAYER -> "Closing player."
                AssistantUiAction.OPEN_SPOTIFY_IMPORT -> "Opening Spotify import."
            }

            is AssistantIntent.NavigateScreen -> "Opening ${screenDisplayName(intent.target)}."
            is AssistantIntent.CreatePlaylist -> "Creating playlist."
            is AssistantIntent.AddCurrentToPlaylist -> "Adding to ${intent.playlistName}."
            is AssistantIntent.WhatIsPlaying -> {
                val current = playerManager.currentSong.value
                if (current != null) "This is ${current.title} by ${current.artist}." else "Nothing is playing."
            }

            is AssistantIntent.StopAssistant -> "Okay."
            is AssistantIntent.GeneralChat -> intent.responseText
        }
    }

    fun parseIntent(rawInput: String, customName: String? = null): AssistantIntent {
        val clean = cleanQuery(rawInput, customName)
        Log.d(TAG, "Parsing cleaned query: '$clean' from raw: '$rawInput', customName: $customName")

        // 1. Wake Greeting / Empty
        if (clean.isBlank()) {
            return AssistantIntent.WakeWordGreeting(getRandomGreeting())
        }

        // 2. Dismiss the assistant without doing anything else
        if (clean in listOf("nothing", "never mind", "nevermind", "cancel", "forget it", "go away", "dismiss", "close", "bye", "thanks", "thank you", "thats all", "that is all")) {
            return AssistantIntent.StopAssistant
        }

        // 3. Media Control: Pause / Stop (with phonetic / homophone tolerance)
        val pauseKeywords = listOf(
            "pause", "paws", "pass", "pos", "paus", "stop", "halt", "freeze", "mute",
            "shut up", "silence", "break", "hold on", "wait", "be quiet", "stop playing"
        )
        if (clean in pauseKeywords ||
            clean in listOf("pause music", "pause the music", "pause song", "pause the song", "stop music", "stop the music", "stop playing", "stop song", "stop playback", "pause playback") ||
            pauseKeywords.any { clean == it || clean.startsWith("$it ") || clean.endsWith(" $it") }
        ) {
            // Ensure not a song query containing pause like "pause by artist"
            if (clean.length < 20 && !clean.startsWith("play ")) {
                return AssistantIntent.PauseMusic
            }
        }

        // 4. Media Control: Previous / Back / Replay (with phonetic tolerance)
        val prevKeywords = listOf(
            "previous", "privious", "preveous", "preview", "previews", "back", "prev", "replay", "rewind"
        )
        if (clean in prevKeywords ||
            clean in listOf("previous song", "previous track", "play previous", "play previous song", "go back", "back song", "last song", "play last song", "previous one", "replay song") ||
            prevKeywords.any { clean == it || clean == "$it song" || clean == "$it track" || clean == "play $it" }
        ) {
            if (!clean.startsWith("play ") || clean in listOf("play previous", "play previous song", "play last song", "play back")) {
                return AssistantIntent.PreviousTrack
            }
        }

        // 5. Restart current song from the beginning
        if (clean in listOf("restart", "restart song", "restart this song", "restart the song", "start over", "from the beginning", "play from start", "play it again", "again")) {
            return AssistantIntent.RestartSong
        }

        // 6. Media Control: Next / Skip (with phonetic tolerance)
        val nextKeywords = listOf(
            "next", "skip", "nest", "neck", "forward", "nxt"
        )
        if (clean in nextKeywords ||
            clean in listOf("next song", "next track", "skip song", "skip track", "play next", "play next song", "next one", "skip this", "skip this song", "pass this song", "change song", "change the song", "another song", "switch song") ||
            nextKeywords.any { clean == it || clean == "$it song" || clean == "$it track" || clean == "play $it" }
        ) {
            if (!clean.startsWith("play ") || clean in listOf("play next", "play next song", "play another song")) {
                return AssistantIntent.NextTrack
            }
        }

        // 7. Relative seeking: "forward 30 seconds", "skip ahead", "go back 10 seconds"
        val seekIntent = parseSeekCommand(clean)
        if (seekIntent != null) {
            return seekIntent
        }

        // 8. Media Control: Resume / General Play ("play a song", "play music", "resume")
        val generalPlayPhrases = listOf(
            "resume", "resume music", "resume the music", "resume song", "continue", "continue music",
            "unpause", "keep playing", "start music", "start playing", "play again", "unmute",
            "play", "play music", "play the music", "play song", "play a song", "play the song",
            "play songs", "play some songs", "play some music", "play track", "play something",
            "play anything", "play any song", "play random song", "play random music", "play a track",
            "play another song", "hit me with some music", "surprise me"
        )
        if (clean in generalPlayPhrases) {
            return AssistantIntent.ResumeMusic
        }

        // 9. Volume Controls
        val volumePercentRegex = Regex("(?:set\\s+)?volume\\s+(?:to\\s+)?(\\d{1,3})\\s*(?:percent|%)?")
        val volumeMatch = volumePercentRegex.find(clean)
        if (volumeMatch != null) {
            val percent = volumeMatch.groupValues[1].toIntOrNull()
            if (percent != null) {
                return AssistantIntent.SetVolumePercent(percent.coerceIn(0, 100))
            }
        }
        if (clean in listOf("volume max", "max volume", "full volume", "volume full", "loudest", "maximum volume", "blast it")) {
            return AssistantIntent.VolumeMax
        }
        if (clean in listOf("volume up", "turn up", "turn it up", "louder", "increase volume", "higher volume", "sound up", "pump it up", "raise volume")) {
            return AssistantIntent.VolumeUp
        }
        if (clean in listOf("volume down", "turn down", "turn it down", "quieter", "decrease volume", "lower volume", "sound down", "reduce volume")) {
            return AssistantIntent.VolumeDown
        }
        if (clean in listOf("mute", "mute music", "mute sound", "mute volume", "silence it")) {
            return AssistantIntent.Mute
        }
        if (clean in listOf("unmute", "unmute music", "unmute sound", "sound on", "volume on")) {
            return AssistantIntent.Unmute
        }

        // 10. Like / Favorite current song
        if (clean in listOf("like", "like this", "like this song", "like song", "favorite", "favorite this", "favorite this song", "add to liked", "add to favorites", "i love this song", "love this song", "heart this") ||
            clean.contains("like this") || clean.contains("add to favorite") || clean.contains("love this song")
        ) {
            if (!clean.contains("unlike") && !clean.contains("dislike")) {
                return AssistantIntent.LikeCurrentSong
            }
        }

        // 11. Unlike / Remove favorite
        if (clean in listOf("unlike", "unlike this", "unlike song", "dislike", "remove from liked", "remove from favorite", "dont like") ||
            clean.contains("unlike") || clean.contains("dislike") || clean.contains("remove from liked")
        ) {
            return AssistantIntent.UnlikeCurrentSong
        }

        // 12. Download Current Song
        val downloadSongKeywords = listOf(
            "download this song", "download this", "download current song", "download song",
            "download track", "download music", "save offline", "save this song offline",
            "download the song", "download it", "save this song", "save this track", "download"
        )
        if (clean in downloadSongKeywords ||
            clean.contains("download this") || clean.contains("download current") ||
            clean.contains("download the song") || clean.contains("download song") ||
            clean.contains("save offline") || clean.contains("save this song")
        ) {
            if (playerManager.currentSong.value != null || clean != "download") {
                return AssistantIntent.DownloadCurrentSong
            }
        }

        // 13. Sleep Timer
        val sleepIntent = parseSleepTimerCommand(clean)
        if (sleepIntent != null) {
            return sleepIntent
        }

        // 14. Music Haptics / Bass Control
        if (clean.contains("gentle haptic") || clean.contains("light haptic") || clean.contains("soft haptic")) {
            return AssistantIntent.SetHapticsIntensity(HapticIntensity.GENTLE)
        }
        if (clean.contains("strong haptic") || clean.contains("heavy haptic") || clean.contains("max haptic")) {
            return AssistantIntent.SetHapticsIntensity(HapticIntensity.STRONG)
        }
        if (clean.contains("medium haptic") || clean.contains("standard haptic") || clean.contains("normal haptic")) {
            return AssistantIntent.SetHapticsIntensity(HapticIntensity.MEDIUM)
        }
        if (clean.contains("turn on haptic") || clean.contains("enable haptic") || clean.contains("haptics on") || clean.contains("haptic on") || clean.contains("start haptic") || clean.contains("vibration on")) {
            return AssistantIntent.SetHaptics(true)
        }
        if (clean.contains("turn off haptic") || clean.contains("disable haptic") || clean.contains("haptics off") || clean.contains("haptic off") || clean.contains("stop haptic") || clean.contains("vibration off")) {
            return AssistantIntent.SetHaptics(false)
        }

        // 15. Equalizer presets & bass boost
        val equalizerIntent = parseEqualizerCommand(clean)
        if (equalizerIntent != null) {
            return equalizerIntent
        }

        // 16. Player panels: lyrics, queue, equalizer, haptics, full player
        val panelIntent = parsePanelCommand(clean)
        if (panelIntent != null) {
            return panelIntent
        }

        // 17. Endless / unlimited queue
        if (clean.contains("endless queue") || clean.contains("unlimited queue") || clean.contains("infinite queue") || clean.contains("autoplay")) {
            val disable = clean.contains(" off") || clean.contains("disable") || clean.contains("turn off") || clean.contains("stop")
            return AssistantIntent.SetUnlimitedQueue(!disable)
        }

        // 18. Shuffle & Repeat (explicit on/off wins over a plain toggle)
        if (clean.contains("shuffle") || clean.contains("randomize") || clean.contains("random order")) {
            val enable = when {
                clean.contains("off") || clean.contains("stop") || clean.contains("disable") || clean.contains("no shuffle") -> false
                clean.contains("on") || clean.contains("enable") || clean.contains("start") -> true
                else -> null
            }
            return AssistantIntent.SetShuffle(enable)
        }
        if (clean.contains("repeat") || clean.contains("loop")) {
            val mode = when {
                clean.contains("off") || clean.contains("stop") || clean.contains("disable") || clean.contains("no repeat") -> RepeatMode.OFF
                clean.contains("one") || clean.contains("this song") || clean.contains("this track") || clean.contains("single") || clean.contains("current") -> RepeatMode.ONE
                clean.contains("all") || clean.contains("queue") || clean.contains("playlist") || clean.contains("everything") -> RepeatMode.ALL
                else -> null
            }
            return AssistantIntent.SetRepeat(mode)
        }

        // 19. Query Current Playing Song
        if (clean.contains("what is playing") || clean.contains("whats playing") || clean.contains("what song is this") || clean.contains("who is singing") || clean.contains("current song") || clean.contains("which song") || clean == "song name") {
            return AssistantIntent.WhatIsPlaying
        }

        // 20. Queue a song for later: "add x to queue", "play x next"
        val queueIntent = parseQueueCommand(clean)
        if (queueIntent != null) {
            return queueIntent
        }

        // 21. Add the current song to a named playlist
        val addToPlaylistRegex = Regex("^add\\s+(?:this|it|this\\s+song|current\\s+song|the\\s+song)\\s+to\\s+(?:my\\s+)?(?:playlist\\s+)?(.*)")
        val addToPlaylistMatch = addToPlaylistRegex.find(clean)
        if (addToPlaylistMatch != null) {
            val name = addToPlaylistMatch.groupValues[1].removeSuffix(" playlist").trim()
            if (name.isNotBlank() && name !in listOf("queue", "the queue", "liked", "liked songs", "favorites")) {
                return AssistantIntent.AddCurrentToPlaylist(name)
            }
        }

        // Create Playlist: "create playlist [name]". This runs BEFORE the mood and navigation
        // checks below, because a playlist name usually contains one of their keywords -
        // "create playlist workout" was matching the workout mood, and "create playlist favorites"
        // was opening Liked Songs.
        val createPlaylistRegex = Regex(
            "^(?:create|new|make|start)\\s+(?:a\\s+|an\\s+|my\\s+)?(?:new\\s+)?playlist" +
                    "(?:\\s+(?:called|named|for|with|titled))?\\s*(.*)",
            RegexOption.IGNORE_CASE
        )
        val playlistMatch = createPlaylistRegex.find(clean)
        if (playlistMatch != null) {
            val name = playlistMatch.groupValues[1].trim()
            val finalName = if (name.isNotBlank()) {
                name.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            } else {
                "My AI Playlist"
            }
            return AssistantIntent.CreatePlaylist(finalName)
        }

        // 22. Navigation Commands
        if (clean.contains("library") || clean.contains("my library")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.LIBRARY)
        }
        if (clean.contains("liked song") || clean.contains("favorites") || clean.contains("favorite songs") || clean.contains("my liked")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.LIKED_SONGS)
        }
        if (clean in listOf("downloads", "my downloads", "open downloads", "go to downloads", "show downloads", "offline songs", "downloaded songs", "downloaded music")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.DOWNLOADS)
        }
        if (clean.contains("go to download") || clean.contains("open download") || clean.contains("show download") || clean.contains("downloads screen")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.DOWNLOADS)
        }
        if (clean.contains("profile") || clean.contains("my account") || clean.contains("account settings")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.PROFILE)
        }
        if (clean.contains("my playlists") || clean.contains("all playlists") || clean.contains("show playlists")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.PLAYLISTS_SEE_ALL)
        }
        if (clean.contains("for you") || clean.contains("recommended") || clean.contains("recommendations")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.FOR_YOU_SEE_ALL)
        }
        if (clean.contains("recently played") || clean.contains("recent songs") || clean.contains("history")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.RECENTLY_PLAYED_SEE_ALL)
        }
        if (clean.contains("popular artist") || clean.contains("top artists") || clean.contains("artists")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.POPULAR_ARTISTS_SEE_ALL)
        }
        if (clean.contains("popular songs") || clean.contains("top charts") || clean.contains("charts")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.POPULAR_SEE_ALL)
        }
        if (clean.contains("import spotify") || clean.contains("spotify playlist") || clean.contains("import playlist")) {
            return AssistantIntent.OpenPanel(AssistantUiAction.OPEN_SPOTIFY_IMPORT)
        }
        if (clean.contains("home") || clean.contains("explore") || clean.contains("main screen")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.HOME)
        }
        if (clean.contains("listen together") || clean.contains("rooms") || clean.contains("party mode") || clean.contains("live room")) {
            return AssistantIntent.NavigateScreen(AppNavScreen.LISTEN_TOGETHER)
        }

        // 23. Mood & Vibe Queries
        val moodIntent = parseMoodQuery(clean)
        if (moodIntent != null) {
            return moodIntent
        }

        // 25. Search Explicit: "search x", "search for starboy", "find despacito"
        val searchRegex = Regex("^(?:search\\s+for|search\\s+and\\s+show|search\\s+song|search|find|look\\s+for)\\s+(.*)", RegexOption.IGNORE_CASE)
        val searchMatch = searchRegex.find(clean)
        if (searchMatch != null) {
            val rawSearchQuery = searchMatch.groupValues[1].trim()
            val (searchQuery, _) = extractSongQuery(rawSearchQuery)
            if (searchQuery.isNotBlank() && searchQuery !in listOf("music", "song", "songs", "something", "audio")) {
                return AssistantIntent.SearchMusic(searchQuery)
            } else {
                return AssistantIntent.NavigateScreen(AppNavScreen.SEARCH_SEE_ALL)
            }
        }

        // 26. Play Specific Song / Artist / Album ("play starboy", "play believer by imagine dragons")
        val playRegex = Regex("^(?:play|listen to|play some|start|hear)\\s+(.*)", RegexOption.IGNORE_CASE)
        val playMatch = playRegex.find(clean)
        if (playMatch != null) {
            val rawSongQuery = playMatch.groupValues[1].trim()
            val (searchQuery, _) = extractSongQuery(rawSongQuery)
            if (searchQuery.isNotBlank() && searchQuery !in listOf("music", "song", "songs", "something", "audio", "track", "a song", "any song")) {
                return AssistantIntent.PlaySong(searchQuery)
            } else {
                return AssistantIntent.ResumeMusic
            }
        }

        // 27. Conversational / Help
        val assistantDisplayName = if (!customName.isNullOrBlank()) customName.replaceFirstChar { it.uppercase() } else "Aurio"
        if (clean.contains("who are you") || clean.contains("what can you do") || clean.contains("help")) {
            return AssistantIntent.GeneralChat("I'm $assistantDisplayName, your music assistant. Try 'Play Starboy', 'Next song', 'Shuffle on', 'Show lyrics', or 'Sleep timer 20 minutes'.")
        }
        if (clean.contains("how are you") || clean.contains("whats up")) {
            return AssistantIntent.GeneralChat("Feeling great! What's the vibe?")
        }

        // 28. Fallback: Treat as direct song/artist play query
        val (fallbackQuery, _) = extractSongQuery(clean)
        return if (fallbackQuery.isNotBlank() && fallbackQuery !in listOf("music", "song", "something", "audio", "track")) {
            AssistantIntent.PlaySong(fallbackQuery)
        } else {
            AssistantIntent.ResumeMusic
        }
    }

    private fun parseSeekCommand(clean: String): AssistantIntent? {
        val forwardRegex = Regex("^(?:skip|seek|jump|go|fast\\s*forward|forward)\\s*(?:ahead|forward|ffwd)?\\s*(?:by\\s+)?(\\d{1,3})?\\s*(seconds?|secs?|minutes?|mins?)?$")
        val backwardRegex = Regex("^(?:rewind|go\\s+back|seek\\s+back|skip\\s+back|back)\\s*(?:by\\s+)?(\\d{1,3})?\\s*(seconds?|secs?|minutes?|mins?)?$")

        backwardRegex.find(clean)?.let { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@let
            val unit = match.groupValues[2]
            val seconds = if (unit.startsWith("min")) amount * 60 else amount
            return AssistantIntent.SeekRelative(-seconds)
        }
        forwardRegex.find(clean)?.let { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@let
            val unit = match.groupValues[2]
            val seconds = if (unit.startsWith("min")) amount * 60 else amount
            return AssistantIntent.SeekRelative(seconds)
        }
        if (clean in listOf("fast forward", "skip ahead", "jump ahead")) {
            return AssistantIntent.SeekRelative(15)
        }
        if (clean in listOf("rewind a bit", "go back a bit", "skip back")) {
            return AssistantIntent.SeekRelative(-15)
        }
        return null
    }

    private fun parseSleepTimerCommand(clean: String): AssistantIntent? {
        if (!clean.contains("sleep timer") && !clean.contains("sleep mode") &&
            !clean.contains("stop after") && !clean.contains("stop in") &&
            !clean.contains("turn off music in") && !clean.contains("timer")
        ) {
            return null
        }

        if (clean.contains("cancel") || clean.contains("turn off the timer") || clean.contains("timer off") ||
            clean.contains("stop timer") || clean.contains("remove timer") || clean.contains("disable timer")
        ) {
            return AssistantIntent.CancelSleepTimer
        }

        if (clean.contains("end of") || clean.contains("after this song") || clean.contains("this track ends")) {
            // AudioPlayerManager treats -1 as "stop when the current track finishes".
            return AssistantIntent.SetSleepTimer(-1)
        }

        val amountRegex = Regex("(\\d{1,3})\\s*(hours?|hrs?|minutes?|mins?|m\\b)?")
        val match = amountRegex.find(clean)
        val amount = match?.groupValues?.get(1)?.toIntOrNull()
        if (amount != null && amount > 0) {
            val unit = match.groupValues[2]
            val minutes = if (unit.startsWith("h")) amount * 60 else amount
            return AssistantIntent.SetSleepTimer(minutes.coerceIn(1, 480))
        }

        // "sleep timer" with no duration -> let the user pick in the dialog
        return AssistantIntent.OpenPanel(AssistantUiAction.OPEN_SLEEP_TIMER)
    }

    private fun parseEqualizerCommand(clean: String): AssistantIntent? {
        val bassRegex = Regex("bass\\s*(?:boost)?\\s*(?:to\\s+)?(\\d{1,3})\\s*(?:percent|%)?")
        bassRegex.find(clean)?.let { match ->
            val percent = match.groupValues[1].toIntOrNull()
            if (percent != null) return AssistantIntent.SetBassBoost(percent.coerceIn(0, 100))
        }
        if (clean.contains("bass boost") || clean.contains("boost the bass") || clean.contains("more bass") || clean.contains("bass up")) {
            val off = clean.contains("off") || clean.contains("no bass") || clean.contains("disable") || clean.contains("less bass")
            return if (off) AssistantIntent.SetBassBoost(0) else AssistantIntent.SetEqualizerPreset(EqualizerPreset.BASS_BOOST)
        }

        if (!clean.contains("equalizer") && !clean.contains("equaliser") && !clean.contains("eq ") && clean != "eq" && !clean.contains("preset") && !clean.contains("sound profile")) {
            return null
        }

        val preset = when {
            clean.contains("flat") || clean.contains("normal") || clean.contains("default") -> EqualizerPreset.FLAT
            clean.contains("pop") -> EqualizerPreset.POP
            clean.contains("rock") -> EqualizerPreset.ROCK
            clean.contains("hip hop") || clean.contains("hiphop") || clean.contains("rap") -> EqualizerPreset.HIPHOP
            clean.contains("dance") || clean.contains("edm") || clean.contains("electronic") -> EqualizerPreset.DANCE
            clean.contains("acoustic") || clean.contains("unplugged") -> EqualizerPreset.ACOUSTIC
            clean.contains("bass") -> EqualizerPreset.BASS_BOOST
            else -> null
        }
        return if (preset != null) {
            AssistantIntent.SetEqualizerPreset(preset)
        } else {
            AssistantIntent.OpenPanel(AssistantUiAction.OPEN_EQUALIZER)
        }
    }

    private fun parsePanelCommand(clean: String): AssistantIntent? {
        if (clean.contains("lyric") || clean.contains("words of this song") || clean.contains("sing along")) {
            return AssistantIntent.OpenPanel(AssistantUiAction.OPEN_LYRICS)
        }
        if (clean.contains("queue") && (clean.contains("show") || clean.contains("open") || clean.contains("see") || clean == "queue" || clean.contains("whats in the queue") || clean.contains("up next"))) {
            return AssistantIntent.OpenPanel(AssistantUiAction.OPEN_QUEUE)
        }
        if (clean.contains("haptic") && (clean.contains("open") || clean.contains("show") || clean.contains("settings"))) {
            return AssistantIntent.OpenPanel(AssistantUiAction.OPEN_HAPTICS)
        }
        if (clean in listOf("open player", "show player", "full player", "open full player", "show the player", "open now playing", "now playing screen", "expand player")) {
            return AssistantIntent.OpenPanel(AssistantUiAction.OPEN_FULL_PLAYER)
        }
        if (clean in listOf("close player", "hide player", "collapse player", "minimize player", "close the player")) {
            return AssistantIntent.OpenPanel(AssistantUiAction.CLOSE_FULL_PLAYER)
        }
        return null
    }

    private fun parseQueueCommand(clean: String): AssistantIntent? {
        val addToQueueRegex = Regex("^(?:add|queue)\\s+(.*?)\\s+(?:to\\s+)?(?:the\\s+)?queue$")
        addToQueueRegex.find(clean)?.let { match ->
            val (query, _) = extractSongQuery(match.groupValues[1].trim())
            if (query.isNotBlank()) return AssistantIntent.QueueSong(query, playNext = false)
        }
        val queuePrefixRegex = Regex("^(?:add\\s+to\\s+queue|queue\\s+up|queue)\\s+(.*)$")
        queuePrefixRegex.find(clean)?.let { match ->
            val (query, _) = extractSongQuery(match.groupValues[1].trim())
            if (query.isNotBlank()) return AssistantIntent.QueueSong(query, playNext = false)
        }
        val playNextRegex = Regex("^play\\s+(.*?)\\s+next$")
        playNextRegex.find(clean)?.let { match ->
            val (query, _) = extractSongQuery(match.groupValues[1].trim())
            if (query.isNotBlank()) return AssistantIntent.QueueSong(query, playNext = true)
        }
        return null
    }

    private fun parseMoodQuery(clean: String): AssistantIntent? {
        val moods = mapOf(
            "chill" to Pair("chill lofi acoustic soothing beats", "Chill"),
            "relax" to Pair("relaxing acoustic calm music", "Relaxing"),
            "workout" to Pair("workout gym high energy motivational music", "Workout"),
            "gym" to Pair("gym pump up hip hop edm hits", "Gym"),
            "party" to Pair("party club dance bangers hits", "Party"),
            "dance" to Pair("dance pop electronic club hits", "Dance"),
            "sad" to Pair("sad emotional heartbreak acoustic songs", "Sad"),
            "emotional" to Pair("emotional soulful melancholic songs", "Emotional"),
            "romantic" to Pair("romantic love acoustic songs", "Romantic"),
            "love" to Pair("best love songs romantic pop", "Love"),
            "rock" to Pair("classic rock alternative rock hits", "Rock"),
            "90s" to Pair("90s greatest hits pop rock", "90s Hits"),
            "retro" to Pair("retro 80s 90s vintage hits", "Retro"),
            "lofi" to Pair("lofi hip hop chill beats to study relax to", "Lofi"),
            "study" to Pair("study focus concentration instrumental beats", "Focus"),
            "focus" to Pair("deep focus instrumental concentration music", "Focus"),
            "sleep" to Pair("calm sleep ambient music", "Sleep"),
            "telugu" to Pair("trending telugu hits songs", "Telugu Hits"),
            "hindi" to Pair("latest hindi bollywood romantic hits", "Hindi Hits"),
            "punjabi" to Pair("top punjabi bangers party hits", "Punjabi Hits"),
            "kpop" to Pair("kpop trending girl boy groups hits", "K-Pop"),
            "surprise" to Pair("billboard global top trending hits", "Trending")
        )

        for ((key, pair) in moods) {
            if (clean.contains(key)) {
                return AssistantIntent.PlayMood(pair.first, pair.second)
            }
        }
        return null
    }

    private fun screenDisplayName(target: AppNavScreen): String = when (target) {
        AppNavScreen.HOME -> "Home"
        AppNavScreen.LIBRARY -> "Library"
        AppNavScreen.LIKED_SONGS -> "Liked Songs"
        AppNavScreen.DOWNLOADS -> "Downloads"
        AppNavScreen.PROFILE -> "Profile"
        AppNavScreen.LISTEN_TOGETHER -> "Listen Together"
        AppNavScreen.SEARCH_SEE_ALL -> "Search"
        AppNavScreen.PLAYLISTS_SEE_ALL -> "Playlists"
        AppNavScreen.FOR_YOU_SEE_ALL -> "For You"
        AppNavScreen.RECENTLY_PLAYED_SEE_ALL -> "Recently Played"
        AppNavScreen.POPULAR_SEE_ALL -> "Popular"
        AppNavScreen.POPULAR_ARTISTS_SEE_ALL -> "Artists"
        else -> "Screen"
    }

    /**
     * Runs the intent and returns what the UI should show.
     *
     * @param sideEffectsApplied true when [applyInstantSideEffect] already performed the action,
     * so the state change is reported without being executed twice (a second toggleLike would
     * undo the first, a second playNext would skip two tracks).
     */
    suspend fun execute(
        intent: AssistantIntent,
        sideEffectsApplied: Boolean = false
    ): AiExecutionResult = withContext(Dispatchers.Main) {
        if (!sideEffectsApplied && isInstantIntent(intent)) {
            applyInstantSideEffect(intent)
        }

        when (intent) {
            is AssistantIntent.WakeWordGreeting -> {
                AiExecutionResult(
                    spokenResponse = intent.prompt,
                    bannerMessage = "Listening 🎙️"
                )
            }

            is AssistantIntent.PlaySong -> {
                try {
                    val query = intent.query
                    val searchResults = withContext(Dispatchers.IO) {
                        musicRepository.search(query)
                    }

                    if (searchResults.isNotEmpty()) {
                        val topSong = searchResults.first()
                        playerManager.playQueue(
                            songs = searchResults,
                            startIndex = 0,
                            source = "AI: $query"
                        )
                        AiExecutionResult(
                            spokenResponse = "Playing ${topSong.title}.",
                            bannerMessage = "Playing: ${topSong.title} 🎵",
                            playedSong = topSong
                        )
                    } else {
                        AiExecutionResult(
                            spokenResponse = "Couldn't find '${intent.query}'.",
                            bannerMessage = "No results for '${intent.query}'"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing PlaySong: ${e.message}")
                    AiExecutionResult(
                        spokenResponse = "Couldn't play that song.",
                        bannerMessage = "Playback error"
                    )
                }
            }

            is AssistantIntent.SearchMusic -> {
                val (_, displayTitle) = extractSongQuery(intent.query)
                AiExecutionResult(
                    spokenResponse = "Searching for $displayTitle.",
                    bannerMessage = "Searching: $displayTitle 🔍",
                    targetScreen = AppNavScreen.SEARCH_SEE_ALL,
                    searchQuery = intent.query
                )
            }

            is AssistantIntent.PlayMood -> {
                try {
                    val searchResults = withContext(Dispatchers.IO) {
                        musicRepository.search(intent.moodQuery)
                    }

                    if (searchResults.isNotEmpty()) {
                        val topSong = searchResults.first()
                        playerManager.playQueue(
                            songs = searchResults,
                            startIndex = 0,
                            source = "Vibe: ${intent.moodName}"
                        )
                        AiExecutionResult(
                            spokenResponse = "Playing ${intent.moodName} music.",
                            bannerMessage = "Vibe: ${intent.moodName} 🎶",
                            playedSong = topSong
                        )
                    } else {
                        AiExecutionResult(
                            spokenResponse = "Playing trending hits.",
                            bannerMessage = "Playing Hits 🎶"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing PlayMood: ${e.message}")
                    AiExecutionResult(
                        spokenResponse = "Couldn't load that vibe.",
                        bannerMessage = "Vibe error"
                    )
                }
            }

            is AssistantIntent.QueueSong -> {
                try {
                    val results = withContext(Dispatchers.IO) {
                        musicRepository.search(intent.query)
                    }
                    val song = results.firstOrNull()
                    if (song != null) {
                        if (intent.playNext) {
                            playerManager.playNextInQueue(song)
                        } else {
                            playerManager.addToQueue(song)
                        }
                        AiExecutionResult(
                            spokenResponse = if (intent.playNext) "${song.title} is up next." else "Added ${song.title} to the queue.",
                            bannerMessage = if (intent.playNext) "Up next: ${song.title} ⏭️" else "Queued: ${song.title} ➕"
                        )
                    } else {
                        AiExecutionResult(
                            spokenResponse = "Couldn't find '${intent.query}'.",
                            bannerMessage = "No results for '${intent.query}'"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error queueing song: ${e.message}")
                    AiExecutionResult(
                        spokenResponse = "Couldn't queue that.",
                        bannerMessage = "Queue error"
                    )
                }
            }

            is AssistantIntent.DownloadCurrentSong -> {
                val current = playerManager.currentSong.value
                if (current != null) {
                    try {
                        val downloadManager = DownloadManager.getInstance()
                        downloadManager.initialize(context)
                        downloadManager.downloadSong(current)
                        AiExecutionResult(
                            spokenResponse = "Downloading ${current.title}.",
                            bannerMessage = "Downloading: ${current.title} ⬇️"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error triggering download: ${e.message}")
                        AiExecutionResult(
                            spokenResponse = "Couldn't download track.",
                            bannerMessage = "Download failed"
                        )
                    }
                } else {
                    AiExecutionResult(
                        spokenResponse = "No song playing to download.",
                        bannerMessage = "No track playing"
                    )
                }
            }

            is AssistantIntent.PauseMusic -> AiExecutionResult(
                spokenResponse = "Paused.",
                bannerMessage = "Paused ⏸️"
            )

            is AssistantIntent.ResumeMusic -> {
                if (playerManager.currentSong.value != null) {
                    val current = playerManager.currentSong.value
                    AiExecutionResult(
                        spokenResponse = "Playing ${current?.title ?: "music"}.",
                        bannerMessage = "Playing: ${current?.title ?: "Music"} ▶️"
                    )
                } else {
                    // Queue is empty -> fetch and play trending global hits automatically
                    try {
                        val trending = withContext(Dispatchers.IO) {
                            musicRepository.search("trending global hits")
                        }
                        if (trending.isNotEmpty()) {
                            val topSong = trending.first()
                            playerManager.playQueue(trending, 0, "AI: Trending Hits")
                            AiExecutionResult(
                                spokenResponse = "Playing trending hits for you.",
                                bannerMessage = "Playing: ${topSong.title} 🎵",
                                playedSong = topSong
                            )
                        } else {
                            AiExecutionResult(
                                spokenResponse = "Resuming.",
                                bannerMessage = "Ready to play ▶️"
                            )
                        }
                    } catch (e: Exception) {
                        AiExecutionResult(
                            spokenResponse = "Ready to play.",
                            bannerMessage = "Ready to play ▶️"
                        )
                    }
                }
            }

            is AssistantIntent.NextTrack -> {
                if (playerManager.queue.value.isNotEmpty()) {
                    val next = playerManager.currentSong.value
                    AiExecutionResult(
                        spokenResponse = if (next != null) "Playing ${next.title}." else "Skipped.",
                        bannerMessage = "Next Track ⏭️",
                        playedSong = next
                    )
                } else {
                    // Queue empty -> play trending
                    try {
                        val trending = withContext(Dispatchers.IO) {
                            musicRepository.search("trending global hits")
                        }
                        if (trending.isNotEmpty()) {
                            playerManager.playQueue(trending, 0, "AI: Trending Hits")
                            AiExecutionResult(
                                spokenResponse = "Playing trending hits.",
                                bannerMessage = "Playing: ${trending.first().title} 🎵",
                                playedSong = trending.first()
                            )
                        } else {
                            AiExecutionResult(
                                spokenResponse = "No upcoming tracks.",
                                bannerMessage = "Queue is empty"
                            )
                        }
                    } catch (_: Exception) {
                        AiExecutionResult(
                            spokenResponse = "No upcoming tracks.",
                            bannerMessage = "Queue is empty"
                        )
                    }
                }
            }

            is AssistantIntent.PreviousTrack -> {
                if (playerManager.queue.value.isNotEmpty()) {
                    val prev = playerManager.currentSong.value
                    AiExecutionResult(
                        spokenResponse = if (prev != null) "Playing ${prev.title}." else "Previous track.",
                        bannerMessage = "Previous Track ⏮️",
                        playedSong = prev
                    )
                } else {
                    AiExecutionResult(
                        spokenResponse = "No previous tracks.",
                        bannerMessage = "Queue is empty"
                    )
                }
            }

            is AssistantIntent.RestartSong -> AiExecutionResult(
                spokenResponse = "Starting over.",
                bannerMessage = "Restarted ⏮️"
            )

            is AssistantIntent.SeekRelative -> {
                val seconds = kotlin.math.abs(intent.seconds)
                AiExecutionResult(
                    spokenResponse = if (intent.seconds >= 0) "Skipped $seconds seconds." else "Back $seconds seconds.",
                    bannerMessage = if (intent.seconds >= 0) "Forward ${seconds}s ⏩" else "Back ${seconds}s ⏪"
                )
            }

            is AssistantIntent.LikeCurrentSong -> {
                if (playerManager.currentSong.value != null) {
                    AiExecutionResult(
                        spokenResponse = "Liked.",
                        bannerMessage = "Added to Liked Songs ❤️"
                    )
                } else {
                    AiExecutionResult(
                        spokenResponse = "No song playing.",
                        bannerMessage = "No track playing"
                    )
                }
            }

            is AssistantIntent.UnlikeCurrentSong -> {
                if (playerManager.currentSong.value != null) {
                    AiExecutionResult(
                        spokenResponse = "Removed from liked.",
                        bannerMessage = "Removed from Liked Songs 💔"
                    )
                } else {
                    AiExecutionResult(
                        spokenResponse = "No song playing.",
                        bannerMessage = "No track playing"
                    )
                }
            }

            is AssistantIntent.VolumeUp -> AiExecutionResult("Volume raised.", "Volume Up 🔊")
            is AssistantIntent.VolumeDown -> AiExecutionResult("Volume lowered.", "Volume Down 🔉")
            is AssistantIntent.VolumeMax -> AiExecutionResult("Max volume.", "Max Volume 🔊")
            is AssistantIntent.SetVolumePercent -> AiExecutionResult(
                spokenResponse = "Volume ${intent.percent} percent.",
                bannerMessage = "Volume ${intent.percent}% 🔊"
            )

            is AssistantIntent.Mute -> AiExecutionResult("Muted.", "Muted 🔇")
            is AssistantIntent.Unmute -> AiExecutionResult("Unmuted.", "Sound On 🔊")

            is AssistantIntent.SetHaptics -> AiExecutionResult(
                spokenResponse = if (intent.enable) "Haptics on." else "Haptics off.",
                bannerMessage = if (intent.enable) "Music Haptics ON ⚡" else "Music Haptics OFF"
            )

            is AssistantIntent.SetHapticsIntensity -> AiExecutionResult(
                spokenResponse = "${intent.intensity.displayName} haptics.",
                bannerMessage = "Haptics: ${intent.intensity.displayName} ⚡"
            )

            is AssistantIntent.SetEqualizerPreset -> AiExecutionResult(
                spokenResponse = "${intent.preset.displayName} equalizer.",
                bannerMessage = "Equalizer: ${intent.preset.displayName} 🎚️"
            )

            is AssistantIntent.SetBassBoost -> AiExecutionResult(
                spokenResponse = if (intent.percent > 0) "Bass at ${intent.percent} percent." else "Bass boost off.",
                bannerMessage = "Bass Boost ${intent.percent}% 🔊"
            )

            is AssistantIntent.SetSleepTimer -> AiExecutionResult(
                spokenResponse = if (intent.minutes > 0) "Sleep timer set for ${intent.minutes} minutes." else "Stopping after this song.",
                bannerMessage = if (intent.minutes > 0) "Sleep Timer: ${intent.minutes}m 😴" else "Stops after this track 😴"
            )

            is AssistantIntent.CancelSleepTimer -> AiExecutionResult(
                spokenResponse = "Sleep timer cancelled.",
                bannerMessage = "Sleep Timer Off"
            )

            is AssistantIntent.SetUnlimitedQueue -> AiExecutionResult(
                spokenResponse = if (intent.enable) "Endless queue on." else "Endless queue off.",
                bannerMessage = if (intent.enable) "Endless Queue ON ♾️" else "Endless Queue OFF"
            )

            is AssistantIntent.SetShuffle -> {
                val isShuffled = playerManager.isShuffleEnabled.value
                AiExecutionResult(
                    spokenResponse = if (isShuffled) "Shuffle on." else "Shuffle off.",
                    bannerMessage = if (isShuffled) "Shuffle ON 🔀" else "Shuffle OFF"
                )
            }

            is AssistantIntent.SetRepeat -> {
                val mode = playerManager.repeatMode.value
                AiExecutionResult(
                    spokenResponse = when (mode) {
                        RepeatMode.ONE -> "Repeating this song."
                        RepeatMode.ALL -> "Repeating the queue."
                        RepeatMode.OFF -> "Repeat off."
                    },
                    bannerMessage = "Repeat: ${mode.name} 🔁"
                )
            }

            is AssistantIntent.OpenPanel -> {
                val label = when (intent.action) {
                    AssistantUiAction.OPEN_LYRICS -> "Lyrics"
                    AssistantUiAction.OPEN_QUEUE -> "Queue"
                    AssistantUiAction.OPEN_EQUALIZER -> "Equalizer"
                    AssistantUiAction.OPEN_HAPTICS -> "Music Haptics"
                    AssistantUiAction.OPEN_SLEEP_TIMER -> "Sleep Timer"
                    AssistantUiAction.OPEN_FULL_PLAYER -> "Now Playing"
                    AssistantUiAction.CLOSE_FULL_PLAYER -> "Player"
                    AssistantUiAction.OPEN_SPOTIFY_IMPORT -> "Spotify Import"
                }
                AiExecutionResult(
                    spokenResponse = if (intent.action == AssistantUiAction.CLOSE_FULL_PLAYER) "Closing." else "Opening $label.",
                    bannerMessage = if (intent.action == AssistantUiAction.CLOSE_FULL_PLAYER) null else "Opening $label ✨",
                    uiAction = intent.action
                )
            }

            is AssistantIntent.NavigateScreen -> {
                val screenName = screenDisplayName(intent.target)
                AiExecutionResult(
                    spokenResponse = "Opening $screenName.",
                    bannerMessage = "Opening $screenName 🧭",
                    targetScreen = intent.target
                )
            }

            is AssistantIntent.CreatePlaylist -> {
                val newPlaylist = libraryRepository.createPlaylist(intent.name)
                AiExecutionResult(
                    spokenResponse = "Playlist created.",
                    bannerMessage = "Playlist '${newPlaylist.name}' created! 📁",
                    targetScreen = AppNavScreen.LIBRARY
                )
            }

            is AssistantIntent.AddCurrentToPlaylist -> {
                val current = playerManager.currentSong.value
                if (current == null) {
                    AiExecutionResult(
                        spokenResponse = "No song playing.",
                        bannerMessage = "No track playing"
                    )
                } else {
                    val existing = libraryRepository.playlists.value.firstOrNull {
                        it.name.equals(intent.playlistName, ignoreCase = true)
                    }
                    val playlist = existing ?: libraryRepository.createPlaylist(
                        intent.playlistName.replaceFirstChar { it.uppercase() }
                    )
                    libraryRepository.addSongToPlaylist(playlist.id, current)
                    AiExecutionResult(
                        spokenResponse = "Added to ${playlist.name}.",
                        bannerMessage = "Added to '${playlist.name}' 📁"
                    )
                }
            }

            is AssistantIntent.WhatIsPlaying -> {
                val current = playerManager.currentSong.value
                if (current != null) {
                    AiExecutionResult(
                        spokenResponse = "This is ${current.title} by ${current.artist}.",
                        bannerMessage = "Now Playing: ${current.title} 🎶"
                    )
                } else {
                    AiExecutionResult(
                        spokenResponse = "Nothing is playing.",
                        bannerMessage = "Player is idle"
                    )
                }
            }

            is AssistantIntent.StopAssistant -> AiExecutionResult(
                spokenResponse = "Okay.",
                bannerMessage = null
            )

            is AssistantIntent.GeneralChat -> AiExecutionResult(
                spokenResponse = intent.responseText,
                bannerMessage = null
            )
        }
    }
}
