package com.samhith.aurio.data.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Wake-word detector: an openWakeWord model trained on the user's own phrase, fed by an
 * echo-cancelled microphone stream.
 *
 * Microphone manners, which matter as much as the detection itself:
 *  - the recording is declared NOT privacy-sensitive, so calls, the camera, voice notes and any
 *    foreground app take the mic first; Aurio is the one that yields;
 *  - when Android silences us because another app took the mic, detection pauses and resumes by
 *    itself afterwards;
 *  - the platform echo canceller runs on the capture path, so the song Aurio itself is playing is
 *    largely removed before the model ever sees the audio.
 *
 * The detector is stopped for the duration of every assistant session, so it and the command
 * recognizer never compete for the microphone.
 */
class AurioHotwordDetector(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    private val TAG = "AurioHotword"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var model: OpenWakeWordModel? = null
    private var captureThread: CaptureThread? = null

    @Volatile
    private var isStarting = false

    @Volatile
    private var isListening = false

    /** What the caller last asked for; lets stop() win over a start() still loading the models. */
    @Volatile
    private var wantListening = false

    /** Raised threshold while music plays, so a song is less likely to score as the wake word. */
    @Volatile
    private var strictMode = false

    /** True while Android is giving our recorder silence because another app holds the mic. */
    @Volatile
    private var silencedByOtherApp = false

    /** Logs the score whenever it rises above a whisper. Useful for tuning the threshold. */
    var logScores = true

    val isActive: Boolean
        get() = isListening

    /**
     * The wake phrase is baked into the trained model file, so renaming the assistant in settings
     * cannot change what it listens for. Kept so callers don't need to know that.
     */
    fun setAssistantName(name: String) {
        Log.d(TAG, "Assistant name is '$name'; wake phrase comes from $WAKE_WORD_MODEL")
    }

    /** Strict while music plays or loads, relaxed in silence. Takes effect on the next chunk. */
    fun setStrictMode(strict: Boolean) {
        if (strictMode != strict) {
            strictMode = strict
            Log.d(TAG, "Listening mode: ${if (strict) "strict (music)" else "relaxed (silence)"}")
        }
    }

    fun start() {
        wantListening = true
        if (isListening || isStarting) return
        isStarting = true

        scope.launch {
            try {
                val loaded = model ?: withContext(Dispatchers.IO) {
                    OpenWakeWordModel.load(context, WAKE_WORD_MODEL)
                }
                if (loaded == null) {
                    Log.e(TAG, "Wake word unavailable; the assistant still works by tapping the bubble.")
                    return@launch
                }
                model = loaded

                // stop() was called while the models loaded (e.g. a session started): stay silent
                if (!wantListening) return@launch

                loaded.reset()
                val thread = CaptureThread(loaded, resolveAudioSource())
                captureThread = thread
                isListening = true
                thread.start()
                Log.d(TAG, "Wake-word detector listening for the trained phrase.")
            } catch (e: Exception) {
                Log.e(TAG, "Could not start wake-word detector: ${e.message}")
                isListening = false
            } finally {
                isStarting = false
            }
        }
    }

    /**
     * Stops capturing. The capture thread releases the microphone itself within one audio chunk,
     * so this never blocks the caller. The loaded models are kept for a fast restart.
     */
    fun stop() {
        wantListening = false
        isListening = false
        captureThread?.requestStop()
        captureThread = null
    }

    fun shutdown() {
        stop()
        model = null
    }

    /**
     * VOICE_COMMUNICATION is the capture path on which vendor audio stacks run their echo
     * cancellation. VOICE_RECOGNITION is kept as a switchable fallback for devices where
     * communication capture behaves badly (pref "hotword_audio_source" = "recognition").
     */
    private fun resolveAudioSource(): Int {
        val pref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_AUDIO_SOURCE, "communication")
        return if (pref == "recognition") {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
    }

    private fun onCaptureFailed(reason: String) {
        Log.w(TAG, "Wake-word audio stopped: $reason")
        // Another app most likely took the microphone (a call, the camera, a voice note).
        // Stand down rather than fight for it; the next session end re-arms us.
        isListening = false
        captureThread = null
    }

    private fun onScore(score: Float) {
        if (!isListening) return
        val threshold = if (strictMode) MUSIC_THRESHOLD else QUIET_THRESHOLD
        if (score < threshold) return

        // Nobody can have said the wake word if the mic heard nothing. Speech at arm's length
        // measures around -40 dBFS, while silence sits near -90, so this costs no real detections
        // and removes the model's habit of scoring highly on an empty room.
        if (recentPeakDbfs < SPEECH_FLOOR_DBFS) {
            Log.d(TAG, "Ignored score ${"%.2f".format(score)}: no speech in the last second (${"%.0f".format(recentPeakDbfs)} dBFS)")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTriggerAtMs < TRIGGER_DEBOUNCE_MS) return
        lastTriggerAtMs = now

        Log.d(TAG, "Wake word detected (score ${"%.2f".format(score)}${if (strictMode) ", over music" else ""})")
        // Release the mic before the session's recognizer asks for it
        stop()
        onWakeWord()
    }

    private var lastTriggerAtMs = 0L

    /** Loudest chunk heard in roughly the last second, in dBFS. Drives the speech gate above. */
    @Volatile
    private var recentPeakDbfs = -120.0

    /**
     * Reads 16 kHz mono audio from an echo-cancelled [AudioRecord] and feeds it to the model in
     * 80 ms steps. Owns the recorder and the audio effects, and releases them when it exits.
     */
    private inner class CaptureThread(
        private val wakeWordModel: OpenWakeWordModel,
        private val audioSource: Int
    ) : Thread("AurioHotwordCapture") {

        @Volatile
        private var running = true

        fun requestStop() {
            running = false
        }

        @SuppressLint("MissingPermission") // RECORD_AUDIO is granted before hands-free can start
        override fun run() {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL, ENCODING)
            val record = try {
                AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setChannelMask(CHANNEL)
                            .setEncoding(ENCODING)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuffer, CHUNK_SAMPLES * 4) * 2)
                    .apply {
                        // VOICE_COMMUNICATION is "privacy-sensitive" by default, and Android gives a
                        // privacy-sensitive recorder the mic over ordinary apps - which let a background
                        // wake word take audio away from voice notes, recorders or other assistants.
                        // Opting out keeps the echo-cancelled path but makes the detector the one that
                        // yields: every foreground app, call and camera recording now wins the mic.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setPrivacySensitive(false)
                        }
                    }
                    .build()
            } catch (e: Exception) {
                mainHandler.post { onCaptureFailed("recorder could not be created: ${e.message}") }
                return
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                mainHandler.post { onCaptureFailed("recorder failed to initialize") }
                return
            }

            val echoCanceller = attachEchoCanceller(record.audioSessionId)
            val noiseSuppressor = attachNoiseSuppressor(record.audioSessionId)
            Log.d(
                TAG,
                "Mic source=${if (audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION) "VOICE_COMMUNICATION" else "VOICE_RECOGNITION"}, " +
                        "echoCanceller=${echoCanceller?.enabled ?: "unavailable"}, " +
                        "noiseSuppressor=${noiseSuppressor?.enabled ?: "unavailable"}"
            )

            val buffer = ShortArray(CHUNK_SAMPLES)
            val recentLevels = ArrayDeque<Double>()
            var levelSum = 0.0
            var levelChunks = 0
            var wasSilenced = false
            val recordingCallback = watchForSilencing(record.audioSessionId)

            try {
                record.startRecording()
                while (running) {
                    var filled = 0
                    while (filled < CHUNK_SAMPLES && running) {
                        val read = record.read(buffer, filled, CHUNK_SAMPLES - filled)
                        if (read < 0) {
                            mainHandler.post { onCaptureFailed("read error $read") }
                            return
                        }
                        filled += read
                    }
                    if (!running) break

                    // Another app has the microphone: Android now hands us silence. Keep the
                    // recorder alive so we get the mic back automatically, but don't score it.
                    if (silencedByOtherApp) {
                        wasSilenced = true
                        continue
                    }
                    if (wasSilenced) {
                        // Drop the audio history from before the interruption
                        wakeWordModel.reset()
                        wasSilenced = false
                    }

                    val chunkDbfs = 20 * log10(rms(buffer, filled).coerceAtLeast(1.0) / 32768.0)
                    recentLevels.addLast(chunkDbfs)
                    while (recentLevels.size > SPEECH_WINDOW_CHUNKS) recentLevels.removeFirst()
                    recentPeakDbfs = recentLevels.max()

                    if (logScores) {
                        levelSum += rms(buffer, filled)
                        levelChunks++
                        if (levelChunks == LEVEL_LOG_CHUNKS) {
                            val dbfs = 20 * log10((levelSum / levelChunks).coerceAtLeast(1.0) / 32768.0)
                            Log.d(TAG, "mic level ${"%.1f".format(dbfs)} dBFS (${if (strictMode) "strict" else "relaxed"})")
                            levelSum = 0.0
                            levelChunks = 0
                        }
                    }

                    val score = wakeWordModel.accept(buffer, filled) ?: continue
                    if (logScores && score >= SCORE_LOG_THRESHOLD) {
                        Log.d(TAG, "wake score ${"%.2f".format(score)}")
                    }
                    mainHandler.post { onScore(score) }
                }
            } catch (e: Exception) {
                mainHandler.post { onCaptureFailed("capture error: ${e.message}") }
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {
                }
                record.release()
                echoCanceller?.release()
                noiseSuppressor?.release()
                recordingCallback?.let { audioManager.unregisterAudioRecordingCallback(it) }
                silencedByOtherApp = false
            }
        }

        /**
         * Android silences a background recorder instead of stopping it when a call, the camera or a
         * foreground app needs the mic. This watches our own recording session for that, so the
         * detector pauses politely and resumes on its own when the mic comes back.
         */
        private fun watchForSilencing(sessionId: Int): AudioManager.AudioRecordingCallback? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            val callback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                    val mine = configs.firstOrNull { it.clientAudioSessionId == sessionId } ?: return
                    val silenced = mine.isClientSilenced
                    if (silenced != silencedByOtherApp) {
                        silencedByOtherApp = silenced
                        Log.d(
                            TAG,
                            if (silenced) "Mic handed to another app (call, camera, voice note): wake word paused"
                            else "Mic returned: wake word listening again"
                        )
                    }
                }
            }
            audioManager.registerAudioRecordingCallback(callback, mainHandler)
            return callback
        }

        private fun attachEchoCanceller(sessionId: Int): AcousticEchoCanceler? {
            if (!AcousticEchoCanceler.isAvailable()) return null
            return try {
                AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            } catch (e: Exception) {
                Log.w(TAG, "Echo canceller could not be attached: ${e.message}")
                null
            }
        }

        private fun attachNoiseSuppressor(sessionId: Int): NoiseSuppressor? {
            if (!NoiseSuppressor.isAvailable()) return null
            return try {
                NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            } catch (e: Exception) {
                Log.w(TAG, "Noise suppressor could not be attached: ${e.message}")
                null
            }
        }

        private fun rms(samples: ShortArray, count: Int): Double {
            var sum = 0.0
            for (i in 0 until count) {
                val s = samples[i].toDouble()
                sum += s * s
            }
            return sqrt(sum / count)
        }
    }

    companion object {
        /** The trained wake-word model in assets. Swap this to change the wake phrase. */
        const val WAKE_WORD_MODEL = "hey_buddy.tflite"

        private const val SAMPLE_RATE_HZ = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = OpenWakeWordModel.CHUNK_SAMPLES

        /**
         * Score needed to wake. This model's own test run detected the phrase about 67% of the time
         * at the usual 0.5, so quiet listening is a little more eager, and music a little stricter.
         */
        private const val QUIET_THRESHOLD = 0.45f
        private const val MUSIC_THRESHOLD = 0.6f

        /** Loudness the mic must have heard recently for a score to count as a real wake. */
        private const val SPEECH_FLOOR_DBFS = -60.0

        /** How far back the speech gate looks: ~1 s, about the length of the wake phrase. */
        private const val SPEECH_WINDOW_CHUNKS = 13

        /** Only scores above this are logged, so the log shows near-misses without flooding. */
        private const val SCORE_LOG_THRESHOLD = 0.2f

        /** Mic level is logged once per this many chunks (~2 s). */
        private const val LEVEL_LOG_CHUNKS = 25

        private const val TRIGGER_DEBOUNCE_MS = 2000L

        private const val PREFS_NAME = "aurio_assistant_prefs"
        private const val PREF_AUDIO_SOURCE = "hotword_audio_source"
    }
}
