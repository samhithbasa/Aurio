package com.samhith.aurio.data.ai

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.samhith.aurio.MainActivity
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.player.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class AssistantWakeState {
    IDLE,
    LISTENING_FOR_HOTWORD,
    AWAKE_LISTENING,
    PROCESSING,
    SPEAKING,
    TRAINING_NAME
}

/**
 * How the current assistant session was started.
 *
 * [IN_APP]    started from the in-app assistant bubble (or the hotword, when hands-free is on).
 * [FLOATING]  a tap on the system-wide floating bubble while the user is outside the app.
 */
enum class AssistantMode {
    IN_APP,
    FLOATING
}

/**
 * Represents the 3-step voice repetition training progress for naming the AI assistant.
 */
data class AssistantTrainingState(
    val isTraining: Boolean = false,
    val step: Int = 0, // 0 = initial prompt, 1 = repeat 1, 2 = repeat 2, 3 = repeat 3, 4 = complete
    val proposedName: String = "",
    val promptText: String = ""
)

/**
 * Wake-word and command manager for the Aurio assistant (singleton).
 *
 * Default behaviour: the microphone opens only for a session the user starts, and closes the
 * moment that session's command is done. Nothing listens again until the assistant is called.
 *
 * Hands-free is opt-in ([setHandsFreeHotword]). When it is on, [AurioHotwordDetector] runs an
 * offline openWakeWord model trained on the user's own wake phrase, and nothing else, then
 * opens exactly the same session the bubble does. That is the one configuration in which the mic
 * stays open, which is why the user has to choose it.
 *
 *  - IN_APP: tapping the in-app assistant bubble opens a session - greet with "What's the vibe?",
 *    listen, execute, release the mic.
 *
 *  - FLOATING: outside the app, a tap on the system floating bubble does exactly the same,
 *    without bringing the app forward.
 *
 * Hands-free "Hey [CustomName]" detection is the one thing that needs a permanently open mic, so
 * it is off by default and lives behind [setHandsFreeHotword]. The wake phrase is still understood
 * inside an open session ("Hey Nova, play Starboy").
 *
 * Recognized control commands are applied synchronously the instant they are detected - already
 * at partial-result time for unambiguous ones - so playback reacts before the spoken confirmation
 * even starts.
 */
class AurioWakeWordManager private constructor(
    private val context: Context,
    private val aiEngine: AurioAiAssistantEngine,
    private val textToSpeech: AurioTextToSpeech,
    private val playerManager: AudioPlayerManager = AudioPlayerManager.getInstance(context)
) {

    private val TAG = "AurioWakeWord"
    private val prefs: SharedPreferences = context.getSharedPreferences("aurio_assistant_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isRestarting = false
    private var wasPlayingBeforeAwake = false
    private var isAppInForeground = true
    private var isHandsFreeEnabled = prefs.getBoolean(PREF_HANDS_FREE, false)

    /** Which recognizer backend is in use, so a failure can fall back to the other one. */
    private var isUsingOnDeviceRecognizer = false
    private var hasFallenBackToSystemRecognizer = false

    /**
     * Offline wake-word listener. Only created when hands-free is switched on - with it off,
     * nothing in this class touches the microphone until the user calls the assistant.
     */
    private var hotwordDetector: AurioHotwordDetector? = null

    /** Pending re-arm of the wake-word detector; replaced whenever a new re-arm is requested. */
    private var hotwordRearmJob: Job? = null

    /** Pending switch of the detector back to relaxed mode once music has stopped. */
    private var relaxListeningJob: Job? = null

    /** Non-null while an explicit (bubble-tap or compound-command) session holds the microphone. */
    private var sessionMode: AssistantMode? = null
    private var sessionTimeoutRunnable: Runnable? = null

    /** Dedupe guard: the same phrase recognized twice (partial then final) must act only once. */
    private var lastCommandText: String = ""
    private var lastCommandAtMs: Long = 0L

    private val _customAssistantName = MutableStateFlow(prefs.getString("custom_assistant_name", "Aurio") ?: "Aurio")
    val customAssistantName: StateFlow<String> = _customAssistantName.asStateFlow()

    private val _isNameTrained = MutableStateFlow(prefs.getBoolean("is_name_trained", false))
    val isNameTrained: StateFlow<Boolean> = _isNameTrained.asStateFlow()

    private val _trainingState = MutableStateFlow(AssistantTrainingState())
    val trainingState: StateFlow<AssistantTrainingState> = _trainingState.asStateFlow()

    private val _state = MutableStateFlow(AssistantWakeState.IDLE)
    val state: StateFlow<AssistantWakeState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(AssistantMode.IN_APP)
    val mode: StateFlow<AssistantMode> = _mode.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _responseMessage = MutableStateFlow<String?>(null)
    val responseMessage: StateFlow<String?> = _responseMessage.asStateFlow()

    private val _rmsVolume = MutableStateFlow(0f)
    val rmsVolume: StateFlow<Float> = _rmsVolume.asStateFlow()

    var onExecutionResult: ((AiExecutionResult) -> Unit)? = null

    init {
        // The recognizer is created lazily, on the first activation: constructing the assistant
        // must not bind anything to the microphone.
        observePlaybackState()
    }

    fun saveCustomAssistantName(name: String) {
        val cleanName = name.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        if (cleanName.isNotBlank()) {
            prefs.edit()
                .putString("custom_assistant_name", cleanName)
                .putBoolean("is_name_trained", true)
                .apply()
            _customAssistantName.value = cleanName
            _isNameTrained.value = true
            hotwordDetector?.setAssistantName(cleanName)
            Log.d(TAG, "Custom Assistant Name trained and saved: $cleanName")
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (!inForeground) {
            // Leaving the app closes any in-app session and hands the command recognizer's
            // microphone back, so calls, voice notes and the camera are never disturbed.
            // With hands-free off, nothing listens until the floating bubble is tapped; with it
            // on, endSession() re-arms the offline wake word so "Hey [Name]" still works here.
            if (sessionMode != AssistantMode.FLOATING) {
                endSession(releaseOverlay = true)
            }
        } else {
            // Back on screen: re-arm the wake word if the user opted into hands-free.
            if (isHandsFreeEnabled && isRunning && sessionMode == null) {
                startHotwordDetector()
            }
        }
    }

    /**
     * Retired. Wake-word duty now belongs entirely to [AurioHotwordDetector] (openWakeWord), which
     * is a far better fit than looping SpeechRecognizer: local, grammar-restricted, no restart
     * storm and no exclusive claim on the speech service.
     *
     * This stays as a hard `false` so every legacy re-arm path in this class is inert and the two
     * engines can never end up fighting over the microphone. SpeechRecognizer is now used only
     * inside a session, to transcribe the actual command.
     */
    private fun canArmHotword(): Boolean = false

    /**
     * Enables always-listening "Hey [Name]" detection while the app is in the foreground.
     * Disabled by default - turning it on means the microphone stays open the whole time the app
     * is on screen and music is paused.
     */
    fun setHandsFreeHotword(enabled: Boolean) {
        isHandsFreeEnabled = enabled
        prefs.edit().putBoolean(PREF_HANDS_FREE, enabled).apply()
        if (enabled) {
            startHotwordDetector()
        } else {
            stopHotwordDetector()
            if (sessionMode == null) {
                releaseMicrophone()
                _state.value = AssistantWakeState.IDLE
            }
        }
    }

    fun isHandsFreeHotwordEnabled(): Boolean = isHandsFreeEnabled

    /**
     * Arms the offline wake-word listener. This is the one path that keeps the microphone open,
     * which is why it only runs when the user has explicitly turned hands-free on.
     */
    private fun startHotwordDetector(forceStrict: Boolean = false) {
        if (!isHandsFreeEnabled || !isRunning || sessionMode != null) return

        val detector = hotwordDetector ?: AurioHotwordDetector(context) {
            // Wake word heard: run the exact same session the bubble opens.
            triggerManualAwake(if (isAppInForeground) AssistantMode.IN_APP else AssistantMode.FLOATING)
        }.also { hotwordDetector = it }

        detector.setAssistantName(_customAssistantName.value)
        val strict = forceStrict || playerManager.isPlaying.value || playerManager.isBuffering.value
        detector.setStrictMode(strict)
        if (strict) {
            scheduleRelaxWhenQuiet()
        }
        detector.start()
        _state.value = AssistantWakeState.LISTENING_FOR_HOTWORD
    }

    private fun stopHotwordDetector() {
        hotwordDetector?.stop()
        if (_state.value == AssistantWakeState.LISTENING_FOR_HOTWORD) {
            _state.value = AssistantWakeState.IDLE
        }
    }

    private fun observePlaybackState() {
        scope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    // Music is coming out of the speaker. Keep listening, but strictly: the echo
                    // canceller removes the song from the mic, and strict mode demands a higher
                    // wake-word score, so a song is far less likely to wake the assistant.
                    relaxListeningJob?.cancel()
                    hotwordDetector?.setStrictMode(true)
                } else {
                    // Paused, stopped or between tracks: stay strict until things are quiet
                    scheduleRelaxWhenQuiet()
                }
                if (sessionMode == null && isHandsFreeEnabled && isRunning && hotwordDetector?.isActive != true) {
                    scheduleHotwordRearm(PLAYBACK_CHANGE_REARM_DELAY_MS)
                }
            }
        }
    }

    /**
     * Re-arms the wake word after [delayMs]. Runs on a coroutine rather than [mainHandler], because
     * releasing the microphone clears every pending callback on that handler.
     *
     * @param startStrict begin in strict mode even though nothing plays yet - used after a play
     * command, when the song is still being searched for and would otherwise start under the
     * sensitive relaxed mode.
     */
    private fun scheduleHotwordRearm(delayMs: Long, startStrict: Boolean = false) {
        hotwordRearmJob?.cancel()
        if (!isHandsFreeEnabled || !isRunning) return
        hotwordRearmJob = scope.launch {
            delay(delayMs)
            if (sessionMode == null) {
                startHotwordDetector(startStrict)
            }
        }
    }

    /**
     * Returns the detector to relaxed mode once no song is playing or loading, after a grace period
     * that covers a song search, a track change or the tail of a song that just stopped.
     */
    private fun scheduleRelaxWhenQuiet() {
        relaxListeningJob?.cancel()
        relaxListeningJob = scope.launch {
            delay(RELAX_LISTENING_DELAY_MS)
            while (playerManager.isPlaying.value || playerManager.isBuffering.value) {
                delay(500)
            }
            hotwordDetector?.setStrictMode(false)
        }
    }

    /**
     * Builds the command recognizer.
     *
     * Two things commonly go wrong here and both are handled:
     *  - Package visibility: without the <queries> declaration for RecognitionService the bind
     *    fails with "not connected to the recognition service". The manifest now declares it.
     *  - Some vendor ROMs ship no usable RecognitionService at all. On Android 13+ we fall back to
     *    the platform's on-device recognizer, which does not depend on a third-party app.
     */
    private fun initRecognizer() {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        Log.d(TAG, "Speech recognition available=$available")

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null

        // Prefer the platform on-device recognizer. Many vendor ROMs (this was found on a vivo
        // FuntouchOS device) point the default voice_recognition_service at a stub that binds but
        // never answers - isRecognitionAvailable() still reports true, so the only symptom is
        // silence plus "not connected to the recognition service". The on-device recognizer talks
        // to Android System Intelligence directly and sidesteps that broken default.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            try {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
                isUsingOnDeviceRecognizer = true
                // No triggerModelDownload() here: it pops a system "Download English (US) update"
                // dialog on every wake. If the language pack is ever genuinely missing, onError
                // gets ERROR_LANGUAGE_UNAVAILABLE and the session falls back to the system service.
                Log.d(TAG, "Using the on-device speech recognizer.")
                return
            } catch (e: Exception) {
                Log.w(TAG, "On-device recognizer unavailable: ${e.message}")
            }
        }

        if (available) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
                isUsingOnDeviceRecognizer = false
                Log.d(TAG, "Using the system speech recognition service.")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error creating system SpeechRecognizer: ${e.message}")
            }
        }

        Log.e(TAG, "No speech recognition service on this device: voice commands cannot run.")
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isRestarting = false
            if (_state.value != AssistantWakeState.AWAKE_LISTENING &&
                _state.value != AssistantWakeState.SPEAKING &&
                _state.value != AssistantWakeState.TRAINING_NAME
            ) {
                _state.value = AssistantWakeState.LISTENING_FOR_HOTWORD
            }
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
            _rmsVolume.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _rmsVolume.value = 0f
            if (_state.value == AssistantWakeState.AWAKE_LISTENING) {
                _state.value = AssistantWakeState.PROCESSING
            }
        }

        override fun onError(error: Int) {
            _rmsVolume.value = 0f
            isRestarting = false
            Log.d(TAG, "Recognizer error code=$error (onDevice=$isUsingOnDeviceRecognizer)")

            // If the chosen engine cannot serve this language or is simply broken on this ROM,
            // rebuild on the other backend once and retry the same session rather than dying quiet.
            val engineUnusable = error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ||
                    error == SpeechRecognizer.ERROR_CLIENT
            if (engineUnusable && isUsingOnDeviceRecognizer && !hasFallenBackToSystemRecognizer) {
                hasFallenBackToSystemRecognizer = true
                Log.w(TAG, "On-device recognizer failed (code=$error): switching to the system service.")
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {
                }
                speechRecognizer = null
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                    isUsingOnDeviceRecognizer = false
                    if (sessionMode != null) {
                        startListeningInternal()
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback recognizer creation failed: ${e.message}")
                }
            }

            if (_trainingState.value.isTraining) {
                // In training mode, retry listening after a brief pause
                mainHandler.postDelayed({
                    if (_trainingState.value.isTraining && _state.value != AssistantWakeState.SPEAKING) {
                        startListeningInternal()
                    }
                }, 500)
                return
            }

            if (sessionMode != null) {
                // An explicit session heard nothing usable: close it and free the microphone.
                if (_state.value == AssistantWakeState.AWAKE_LISTENING || _state.value == AssistantWakeState.PROCESSING) {
                    endSession(releaseOverlay = true)
                }
                return
            }

            // Hotword mode: restart seamlessly, but only under the mode 1 microphone policy.
            if (canArmHotword() && _state.value != AssistantWakeState.SPEAKING) {
                scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 800 else 250)
            }
        }

        override fun onResults(results: Bundle?) {
            _rmsVolume.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (!matches.isNullOrEmpty()) {
                handleRecognizedSpeechCandidates(matches)
            } else if (sessionMode != null) {
                endSession(releaseOverlay = true)
            } else if (canArmHotword() && _state.value != AssistantWakeState.SPEAKING) {
                scheduleRestart(250)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim().orEmpty()
            if (partial.isBlank() || _trainingState.value.isTraining) return

            val clean = partial.lowercase()

            if (_state.value == AssistantWakeState.LISTENING_FOR_HOTWORD) {
                if (containsWakeWord(clean)) {
                    val remaining = aiEngine.cleanQuery(clean, _customAssistantName.value)
                    if (remaining.isBlank()) {
                        // "Hey [Name]" on its own -> greet immediately with "What's the vibe?"
                        triggerWakeUpGreeting(AssistantMode.IN_APP)
                    } else {
                        // Compound command ("Hey [Name] pause") -> run it without waiting
                        beginSession(AssistantMode.IN_APP, pauseMusic = false)
                        _liveTranscript.value = remaining
                        executeVoiceCommand(remaining)
                    }
                }
            } else if (_state.value == AssistantWakeState.AWAKE_LISTENING) {
                val cmd = aiEngine.cleanQuery(partial, _customAssistantName.value)
                _liveTranscript.value = cmd

                // Sub-100ms path: fire unambiguous control commands straight off the partial result
                val intent = aiEngine.parseIntent(cmd, _customAssistantName.value)
                if (isPartialSafe(cmd, intent)) {
                    executeVoiceCommand(cmd)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Whether a command recognized mid-utterance can be executed before the user stops speaking.
     *
     * Only short, self-contained control phrases qualify. Words that commonly start a longer
     * phrase ("play ...", "back to black", "skip 30 seconds") wait for the final result so a
     * song request is never truncated into the wrong action.
     */
    private fun isPartialSafe(cleanText: String, intent: AssistantIntent): Boolean {
        if (cleanText.isBlank() || cleanText in AMBIGUOUS_PARTIAL_PHRASES) return false
        if (cleanText.split(" ").size > 3) return false
        return when (intent) {
            is AssistantIntent.PauseMusic,
            is AssistantIntent.NextTrack,
            is AssistantIntent.PreviousTrack,
            is AssistantIntent.VolumeUp,
            is AssistantIntent.VolumeDown,
            is AssistantIntent.VolumeMax,
            is AssistantIntent.Mute,
            is AssistantIntent.Unmute,
            is AssistantIntent.LikeCurrentSong,
            is AssistantIntent.UnlikeCurrentSong,
            is AssistantIntent.SetShuffle,
            is AssistantIntent.SetRepeat -> true

            else -> false
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        val clean = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        val custom = _customAssistantName.value.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").trim()

        // Check custom trained assistant name
        if (custom.isNotBlank() && custom != "aurio") {
            val customPatterns = listOf(
                "hey $custom", "heyy $custom", "hay $custom", "hi $custom", "hii $custom",
                "hello $custom", "ok $custom", "okay $custom", "yo $custom", custom
            )
            for (pattern in customPatterns) {
                if (clean == pattern || clean.startsWith("$pattern ") || clean.endsWith(" $pattern") || clean.contains(" $pattern ") || clean.contains(pattern)) {
                    return true
                }
            }
        }

        // Standard fallback wake words for "Aurio"
        val aurioPatterns = listOf(
            "hey aurio", "heyy aurio", "hay aurio", "hi aurio", "hii aurio",
            "hello aurio", "ok aurio", "okay aurio", "yo aurio", "aurio", "audio", "orio"
        )
        for (pattern in aurioPatterns) {
            if (clean == pattern || clean.startsWith("$pattern ") || clean.endsWith(" $pattern") || clean.contains(" $pattern ") || clean.contains(pattern)) {
                return true
            }
        }

        return false
    }

    private fun extractCandidateName(rawSpeech: String): String {
        var text = rawSpeech.trim().lowercase()
        val prefixes = listOf(
            "my name is", "name is", "call me", "call it", "i want", "name it",
            "let's call it", "suggest", "name", "hey", "heyy", "hi", "ok", "okay"
        )
        for (p in prefixes) {
            if (text.startsWith(p)) {
                text = text.removePrefix(p).trim()
            }
        }
        val words = text.split(" ").filter { it.isNotBlank() && it !in listOf("the", "a", "an", "assistant", "ai", "please") }
        val candidate = words.firstOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Aurio"
        return candidate
    }

    private fun handleTrainingSpeech(text: String) {
        stopListeningInternal()
        val currentStep = _trainingState.value.step

        when (currentStep) {
            0 -> {
                // Step 0: User provided initial name suggestion
                val candidate = extractCandidateName(text)
                _trainingState.value = AssistantTrainingState(
                    isTraining = true,
                    step = 1,
                    proposedName = candidate,
                    promptText = "Please say '$candidate' again (1 of 3)"
                )
                _responseMessage.value = "Please say '$candidate' again (1 of 3)"
                _state.value = AssistantWakeState.SPEAKING
                textToSpeech.speak("Please say $candidate again.") {
                    _state.value = AssistantWakeState.AWAKE_LISTENING
                    startListeningInternal()
                }
            }

            1 -> {
                // Step 1: First repetition
                val candidate = _trainingState.value.proposedName.ifBlank { extractCandidateName(text) }
                _trainingState.value = AssistantTrainingState(
                    isTraining = true,
                    step = 2,
                    proposedName = candidate,
                    promptText = "Say '$candidate' one more time (2 of 3)"
                )
                _responseMessage.value = "Say '$candidate' one more time (2 of 3)"
                _state.value = AssistantWakeState.SPEAKING
                textToSpeech.speak("Say $candidate one more time.") {
                    _state.value = AssistantWakeState.AWAKE_LISTENING
                    startListeningInternal()
                }
            }

            2, 3 -> {
                // Step 2/3: Final repetition -> Confirmed and locked in!
                val finalName = _trainingState.value.proposedName.ifBlank { extractCandidateName(text) }
                saveCustomAssistantName(finalName)
                _trainingState.value = AssistantTrainingState(
                    isTraining = false,
                    step = 3,
                    proposedName = finalName,
                    promptText = "Great! You can now call me 'Hey $finalName'."
                )
                _state.value = AssistantWakeState.SPEAKING
                _responseMessage.value = "Awesome! Call me 'Hey $finalName'. What's the vibe?"
                val confirmSpoken = "Awesome! From now on, you can call me Hey $finalName. What's the vibe?"
                textToSpeech.speak(confirmSpoken) {
                    // Smooth transition to listening for music commands
                    _state.value = AssistantWakeState.AWAKE_LISTENING
                    _responseMessage.value = null
                    startListeningInternal()
                    armSessionTimeout()
                }
            }
        }
    }

    fun submitCustomNameFromUi(name: String) {
        if (name.isBlank()) return
        val clean = name.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        saveCustomAssistantName(clean)
        _trainingState.value = AssistantTrainingState(
            isTraining = false,
            step = 3,
            proposedName = clean,
            promptText = "Great! You can now call me 'Hey $clean'."
        )
        _state.value = AssistantWakeState.SPEAKING
        _responseMessage.value = "Awesome! Call me 'Hey $clean'. What's the vibe?"
        val confirmSpoken = "Awesome! From now on, you can call me Hey $clean. What's the vibe?"
        textToSpeech.speak(confirmSpoken) {
            _state.value = AssistantWakeState.AWAKE_LISTENING
            _responseMessage.value = null
            startListeningInternal()
            armSessionTimeout()
        }
    }

    private fun handleRecognizedSpeechCandidates(matches: List<String>) {
        if (_state.value == AssistantWakeState.SPEAKING) {
            // Ignore speech results while TTS is speaking a greeting or acknowledgment
            return
        }

        // If in Name Training flow, delegate to training processor with top candidate
        if (_trainingState.value.isTraining) {
            val topText = matches.firstOrNull()?.trim().orEmpty()
            if (topText.isNotBlank()) {
                handleTrainingSpeech(topText)
            }
            return
        }

        Log.d(TAG, "Speech candidates recognized: $matches, current state: ${_state.value}")

        if (_state.value == AssistantWakeState.LISTENING_FOR_HOTWORD) {
            // Check if ANY match contains the wake word
            val matchingCandidate = matches.firstOrNull { containsWakeWord(it.lowercase()) }
            if (matchingCandidate != null) {
                val command = aiEngine.cleanQuery(matchingCandidate.lowercase(), _customAssistantName.value)
                if (command.isNotBlank()) {
                    beginSession(AssistantMode.IN_APP, pauseMusic = false)
                    _liveTranscript.value = command
                    executeVoiceCommand(command)
                } else {
                    triggerWakeUpGreeting(AssistantMode.IN_APP)
                }
            } else if (canArmHotword()) {
                scheduleRestart(200)
            }
        } else if (_state.value == AssistantWakeState.AWAKE_LISTENING || _state.value == AssistantWakeState.PROCESSING) {
            // Prefer candidates that map to a direct command over the song-search fallback
            var chosenCommand = matches.first()
            for (candidate in matches) {
                val intent = aiEngine.parseIntent(candidate, _customAssistantName.value)
                if (intent !is AssistantIntent.PlaySong && intent !is AssistantIntent.GeneralChat) {
                    chosenCommand = candidate
                    break
                }
            }

            val clean = aiEngine.cleanQuery(chosenCommand, _customAssistantName.value)
            _liveTranscript.value = if (clean.isNotBlank()) clean else chosenCommand
            executeVoiceCommand(chosenCommand)
        }
    }

    fun startNameTrainingFlow() {
        beginSession(_mode.value, pauseMusic = true)
        _state.value = AssistantWakeState.TRAINING_NAME
        _trainingState.value = AssistantTrainingState(
            isTraining = true,
            step = 0,
            proposedName = "",
            promptText = "Suggest a name for your AI assistant"
        )
        _responseMessage.value = "Suggest a name for your AI assistant"
        _liveTranscript.value = ""

        textToSpeech.speak("Suggest a name for your AI assistant.") {
            _state.value = AssistantWakeState.AWAKE_LISTENING
            startListeningInternal()
        }
    }

    /**
     * Opens an explicit assistant session: the hotword listener stands down, playback is paused so
     * the mic and the voice are clear, and the overlay/bubble shows that Aurio is awake.
     */
    private fun beginSession(mode: AssistantMode, pauseMusic: Boolean) {
        cancelSessionTimeout()
        // The wake-word detector must let go of the mic before the command recognizer asks for it
        hotwordDetector?.stop()
        stopListeningInternal()
        sessionMode = mode
        _mode.value = mode
        if (pauseMusic) {
            wasPlayingBeforeAwake = playerManager.isPlaying.value
            if (wasPlayingBeforeAwake) {
                playerManager.pause()
            }
        }
        _isOverlayVisible.value = true
    }

    /**
     * Ends the current session and releases the microphone. Mode 1 goes back to hands-free hotword
     * listening when the app is still on screen; mode 2 goes fully silent until the next bubble tap.
     */
    private fun endSession(
        releaseOverlay: Boolean,
        resumePlayback: Boolean = true,
        rearmDelayMs: Long = SESSION_END_REARM_DELAY_MS,
        rearmStrict: Boolean = false
    ) {
        cancelSessionTimeout()
        sessionMode = null
        // The command is done: the microphone goes off here and stays off until the user calls
        // the assistant again (in-app bubble, or the floating bubble outside the app).
        releaseMicrophone()
        if (releaseOverlay) {
            _isOverlayVisible.value = false
            _responseMessage.value = null
            _liveTranscript.value = ""
        }

        if (resumePlayback && wasPlayingBeforeAwake && !playerManager.isPlaying.value) {
            playerManager.resume()
        }
        wasPlayingBeforeAwake = false

        _state.value = AssistantWakeState.IDLE
        // Back to waiting for "Hey [Name]" - but never in the seconds while a requested song is
        // still being searched for and loaded.
        scheduleHotwordRearm(rearmDelayMs, rearmStrict)
    }

    /** Closes a session that the user opened but never spoke into, so the mic is never left hot. */
    private fun armSessionTimeout(timeoutMs: Long = SESSION_TIMEOUT_MS) {
        cancelSessionTimeout()
        val runnable = Runnable {
            if (sessionMode != null &&
                (_state.value == AssistantWakeState.AWAKE_LISTENING ||
                        _state.value == AssistantWakeState.PROCESSING ||
                        _state.value == AssistantWakeState.TRAINING_NAME)
            ) {
                Log.d(TAG, "Assistant session timed out with no command: releasing microphone.")
                if (_trainingState.value.isTraining) {
                    // An abandoned naming flow must not leave its UI behind either
                    _trainingState.value = AssistantTrainingState()
                }
                endSession(releaseOverlay = true)
            }
        }
        sessionTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    private fun cancelSessionTimeout() {
        sessionTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        sessionTimeoutRunnable = null
    }

    private fun triggerWakeUpGreeting(mode: AssistantMode) {
        if (_state.value == AssistantWakeState.SPEAKING) return

        beginSession(mode, pauseMusic = true)
        _state.value = AssistantWakeState.SPEAKING
        val greeting = aiEngine.getRandomGreeting() // "What's the vibe?"
        _responseMessage.value = greeting
        _liveTranscript.value = ""

        textToSpeech.speak(greeting) {
            // As soon as the greeting ends, listen for the command
            _state.value = AssistantWakeState.AWAKE_LISTENING
            _responseMessage.value = null
            startListeningInternal()
            armSessionTimeout()
        }
    }

    /**
     * Mode 2 entry point (and the in-app bubble): wakes the assistant on demand.
     * Safe to call while the app is in the background - this is the only path that may take the
     * microphone outside the app, and it closes itself as soon as the command is handled.
     */
    /**
     * Words the command recognizer should favour: Aurio's commands, the assistant's name, and the
     * songs and artists the user actually plays (queue, recently played, liked), most relevant first.
     */
    private fun buildBiasingStrings(): ArrayList<String> {
        val phrases = LinkedHashSet<String>()
        phrases.add(_customAssistantName.value)
        phrases.addAll(BIASING_COMMANDS)

        val songs = playerManager.queue.value +
                MusicRepository.getInstance().recentlyPlayed.value +
                LibraryRepository.getInstance().likedSongs.value
        for (song in songs) {
            if (phrases.size >= MAX_BIASING_STRINGS) break
            // 'Singari (From "Dude (Telugu)")' is spoken as just "Singari"
            val spokenTitle = song.title.substringBefore(" (").substringBefore(" [").trim()
            if (spokenTitle.isNotBlank()) phrases.add(spokenTitle)
            if (song.artist.isNotBlank()) phrases.add(song.artist.substringBefore(",").trim())
        }
        return ArrayList(phrases.take(MAX_BIASING_STRINGS))
    }

    /** Whether Aurio's own screen is visible, so its in-app overlay can present a session. */
    fun isAppOnScreen(): Boolean = isAppInForeground

    fun triggerManualAwake(mode: AssistantMode = AssistantMode.IN_APP) {
        _mode.value = mode
        if (!_isNameTrained.value) {
            startNameTrainingFlow()
        } else {
            triggerWakeUpGreeting(mode)
        }
    }

    fun submitTextCommand(query: String) {
        if (query.isBlank()) return
        beginSession(_mode.value, pauseMusic = false)
        _liveTranscript.value = query
        executeVoiceCommand(query)
    }

    private fun bringAppToForeground() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening MainActivity: ${e.message}")
        }
    }

    private fun executeVoiceCommand(rawCommand: String) {
        val normalized = rawCommand.trim().lowercase()
        val now = System.currentTimeMillis()
        if (normalized == lastCommandText && now - lastCommandAtMs < COMMAND_DEDUPE_WINDOW_MS) {
            // Same phrase already handled from a partial result - don't skip two tracks.
            return
        }
        lastCommandText = normalized
        lastCommandAtMs = now

        // Free the microphone before playback starts so audio focus transfers cleanly
        cancelSessionTimeout()
        stopListeningInternal()

        val intent = aiEngine.parseIntent(rawCommand, _customAssistantName.value)

        // 1. Apply the action NOW, synchronously on the main thread - zero latency for player
        //    controls, equalizer, haptics, sleep timer and the rest of the instant intents.
        val sideEffectsApplied = if (aiEngine.isInstantIntent(intent)) {
            aiEngine.applyInstantSideEffect(intent)
        } else {
            false
        }

        val isPlaybackIntent = intent is AssistantIntent.PlaySong ||
                intent is AssistantIntent.PlayMood ||
                intent is AssistantIntent.ResumeMusic ||
                intent is AssistantIntent.NextTrack ||
                intent is AssistantIntent.PreviousTrack

        val isPauseOrStopIntent = intent is AssistantIntent.PauseMusic

        // An explicit pause must not be undone by the overlay's auto-resume
        if (isPauseOrStopIntent || isPlaybackIntent) {
            wasPlayingBeforeAwake = false
        }

        // 2. Short spoken confirmation ("Paused.", "Next.") - queued after the action already ran
        val immediateSpokenResponse = aiEngine.getImmediateSpokenResponse(intent)
        _responseMessage.value = immediateSpokenResponse
        _isOverlayVisible.value = true
        _state.value = AssistantWakeState.SPEAKING

        // 3. Only pull the app forward for things that need the UI (navigation, search, panels)
        if (aiEngine.requiresAppForeground(intent) && !isAppInForeground) {
            bringAppToForeground()
        }

        // 4. Finish the work that needs I/O (song search, downloads) off the critical path
        scope.launch {
            try {
                val result = aiEngine.execute(intent, sideEffectsApplied = sideEffectsApplied)
                onExecutionResult?.invoke(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing AI command: ${e.message}")
            }
        }

        // 5. Speak, then close the session quickly and hand the mic back
        textToSpeech.speak(immediateSpokenResponse) {
            scope.launch {
                delay(POST_COMMAND_DISMISS_MS)
                endSession(
                    releaseOverlay = true,
                    resumePlayback = !isPauseOrStopIntent && !isPlaybackIntent,
                    // The song search is still running when the confirmation finishes speaking
                    rearmDelayMs = if (isPlaybackIntent) PLAYBACK_COMMAND_REARM_DELAY_MS else SESSION_END_REARM_DELAY_MS,
                    rearmStrict = isPlaybackIntent
                )
            }
        }
    }

    /**
     * Arms the assistant so it can be called. This does NOT take the microphone: with hands-free
     * hotword detection off (the default) the mic stays closed until the user taps the bubble.
     */
    fun startContinuousListening() {
        isRunning = true
        if (isHandsFreeEnabled) {
            startHotwordDetector()
        } else if (sessionMode == null) {
            _state.value = AssistantWakeState.IDLE
        }
    }

    private fun startListeningInternal() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 400L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 400L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 250L)
                    // Android 13+: prime the recognizer with what the user is likely to say, so song
                    // titles and command words come back spelled right ("singari", not "singare").
                    // Engines that don't support biasing simply ignore it.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, buildBiasingStrings())
                    }
                }

                speechRecognizer?.startListening(intent)

                // Watchdog: every time the microphone opens inside a session, make sure a timeout is
                // running. Some paths used to listen with none - the naming flow retried on every
                // error forever, keeping the mic indicator on for as long as that screen stayed up.
                // Only armed when missing, so error retries cannot keep pushing the deadline back.
                if (sessionMode != null && sessionTimeoutRunnable == null) {
                    armSessionTimeout(
                        if (_trainingState.value.isTraining) TRAINING_TIMEOUT_MS else SESSION_TIMEOUT_MS
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting internal listening: ${e.message}")
                if (canArmHotword()) {
                    scheduleRestart(1000)
                }
            }
        }
    }

    /** Stops capturing, but keeps the recognizer instance for the next leg of the same session. */
    private fun stopListeningInternal() {
        mainHandler.removeCallbacksAndMessages(null)
        sessionTimeoutRunnable = null
        try {
            speechRecognizer?.cancel()
            _rmsVolume.value = 0f
        } catch (_: Exception) {
        }
    }

    /**
     * Hands the microphone back to the system for good.
     *
     * cancel() alone leaves the recognizer bound, and on many devices the OS microphone indicator
     * stays lit; destroying it is what actually frees the mic for phone calls, voice notes and the
     * camera. The recognizer is rebuilt lazily on the next activation, while the greeting plays,
     * so this costs nothing the user can feel.
     */
    private fun releaseMicrophone() {
        mainHandler.removeCallbacksAndMessages(null)
        sessionTimeoutRunnable = null
        // cancel() ends the recognition session on the service side so the speech service lets
        // go of the audio it was holding; destroy() then unbinds and frees the mic. Skipping
        // cancel() can leave the service holding audio focus and keep music paused. On an idle
        // recognizer the framework may log "not connected to the recognition service" - harmless.
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        _rmsVolume.value = 0f
        Log.d(TAG, "Microphone released: nothing is listening until the assistant is called again.")
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isRestarting || !canArmHotword() || _state.value == AssistantWakeState.SPEAKING) return
        isRestarting = true
        mainHandler.postDelayed({
            if (canArmHotword() && _state.value != AssistantWakeState.SPEAKING) {
                try {
                    speechRecognizer?.cancel()
                    startListeningInternal()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during restart: ${e.message}")
                }
            }
            isRestarting = false
        }, delayMs)
    }

    fun dismissOverlay() {
        textToSpeech.stop()
        _trainingState.value = AssistantTrainingState()
        endSession(releaseOverlay = true)
    }

    fun stopContinuousListening() {
        isRunning = false
        cancelSessionTimeout()
        sessionMode = null
        stopHotwordDetector()
        releaseMicrophone()
        _state.value = AssistantWakeState.IDLE
        _isOverlayVisible.value = false
    }

    fun destroy() {
        stopContinuousListening()
        hotwordDetector?.shutdown()
        hotwordDetector = null
        textToSpeech.stop()
    }

    companion object {
        private const val PREF_HANDS_FREE = "hands_free_hotword_enabled"

        /** Upper bound on biasing phrases; long lists slow recognizers down and dilute the bias. */
        private const val MAX_BIASING_STRINGS = 100

        private val BIASING_COMMANDS = listOf(
            "play", "pause", "resume", "next song", "previous song", "shuffle on", "shuffle off",
            "repeat", "volume up", "volume down", "mute", "like this song", "show lyrics",
            "open queue", "equalizer", "sleep timer", "download this song", "what's playing"
        )

        /** Wake-word re-arm delay after an ordinary command. */
        private const val SESSION_END_REARM_DELAY_MS = 800L

        /** After a play command. The detector starts in strict mode, so this can be short. */
        private const val PLAYBACK_COMMAND_REARM_DELAY_MS = 1500L

        /** When playback starts or stops and the detector is not listening. */
        private const val PLAYBACK_CHANGE_REARM_DELAY_MS = 1500L

        /** Strict mode outlives the music this long: covers song search, skips and a song's tail. */
        private const val RELAX_LISTENING_DELAY_MS = 8000L

        /** A session that hears nothing releases the mic after this long. */
        private const val SESSION_TIMEOUT_MS = 8000L

        /** The naming flow gets longer, since the user may be typing a name instead of saying it. */
        private const val TRAINING_TIMEOUT_MS = 20000L

        /** How long the confirmation stays on screen after a command completes. */
        private const val POST_COMMAND_DISMISS_MS = 250L

        /**
         * A phrase recognized twice (partial, then final ~1s later) within this window acts once.
         * Kept short so deliberately repeating a command ("next", "next") still skips twice.
         */
        private const val COMMAND_DEDUPE_WINDOW_MS = 1500L

        /**
         * Words that usually open a longer phrase. Executing them off a partial result would cut
         * off requests like "play back to black" or "skip 30 seconds".
         */
        private val AMBIGUOUS_PARTIAL_PHRASES = setOf(
            "play", "back", "skip", "forward", "stop", "repeat", "shuffle", "next to", "go"
        )

        @Volatile
        private var instance: AurioWakeWordManager? = null

        fun getInstance(context: Context): AurioWakeWordManager {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val playerManager = AudioPlayerManager.getInstance(appContext)
                    val musicRepo = MusicRepository.getInstance().apply { initialize(appContext) }
                    val libRepo = LibraryRepository.getInstance().apply { initialize(appContext) }
                    val tts = AurioTextToSpeech(appContext)
                    val engine = AurioAiAssistantEngine(appContext, playerManager, musicRepo, libRepo)
                    AurioWakeWordManager(appContext, engine, tts, playerManager).also {
                        instance = it
                    }
                }
            }
        }
    }
}
