package com.samhith.aurio.data.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Text-to-Speech manager for Aurio AI Assistant.
 *
 * Speaks with a soft, sweet young-female voice: the engine's installed voices are scanned at
 * initialization and the best local female English voice is selected, with a slightly raised
 * pitch for a light, friendly tone. Speech rate stays brisk so spoken confirmations
 * ("Paused.", "Next.") never add perceivable latency to a command.
 *
 * Utterances requested before the engine finishes initializing are queued and flushed on init.
 */
class AurioTextToSpeech(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "AurioTextToSpeech"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Utterance id -> completion callback. A single persistent progress listener drains this. */
    private val completionCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private var pendingSpeakText: String? = null
    private var pendingOnComplete: (() -> Unit)? = null
    private var initTimeoutRunnable: Runnable? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        initTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        initTimeoutRunnable = null

        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }

            applySweetFemaleVoice()

            // Sweet, light tone: pitch above neutral, rate brisk to stay low latency.
            tts?.setPitch(SWEET_PITCH)
            tts?.setSpeechRate(SWEET_RATE)
            isInitialized = true
            Log.d(TAG, "TextToSpeech initialized (voice=${tts?.voice?.name})")

            tts?.setOnUtteranceProgressListener(progressListener)

            // Flush any pending utterance queued while waiting for init
            val text = pendingSpeakText
            val callback = pendingOnComplete
            pendingSpeakText = null
            pendingOnComplete = null
            if (text != null) {
                mainHandler.post { speak(text, callback) }
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
            val callback = pendingOnComplete
            pendingSpeakText = null
            pendingOnComplete = null
            callback?.invoke()
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _isSpeaking.value = true
        }

        override fun onDone(utteranceId: String?) {
            finishUtterance(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            finishUtterance(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            finishUtterance(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            finishUtterance(utteranceId)
        }
    }

    private fun finishUtterance(utteranceId: String?) {
        _isSpeaking.value = false
        val callback = utteranceId?.let { completionCallbacks.remove(it) }
        if (callback != null) {
            mainHandler.post { callback.invoke() }
        }
    }

    /**
     * Picks the sweetest available female voice: local (no network round-trip), English preferred,
     * highest quality, matching one of the known female voice identifiers used by the Google and
     * Samsung TTS engines. Falls back to the engine default when nothing scores as female.
     */
    private fun applySweetFemaleVoice() {
        try {
            val voices: Set<Voice> = tts?.voices ?: return
            if (voices.isEmpty()) return

            val installedEnglish = voices.filter { voice ->
                val isEnglish = voice.locale?.language?.lowercase() == "en"
                val isInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
                isEnglish && isInstalled
            }
            val candidates = if (installedEnglish.isNotEmpty()) installedEnglish else voices.toList()

            val best = candidates.maxByOrNull { scoreVoice(it) }
            if (best != null && scoreVoice(best) > 0) {
                tts?.voice = best
                Log.d(TAG, "Selected assistant voice: ${best.name} (${best.locale})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not select a female voice, using engine default: ${e.message}")
        }
    }

    private fun scoreVoice(voice: Voice): Int {
        val name = voice.name?.lowercase().orEmpty()
        var score = 0

        // Explicitly female / male voice identifiers across common Android TTS engines.
        // "female" also contains "male", so the female match wins outright when both hit.
        val isFemale = FEMALE_VOICE_HINTS.any { name.contains(it) }
        if (isFemale) {
            score += 100
        } else if (MALE_VOICE_HINTS.any { name.contains(it) }) {
            score -= 200
        }

        // Prefer US English, then any English variant.
        val localeTag = voice.locale?.toString()?.lowercase().orEmpty()
        if (localeTag.startsWith("en_us")) score += 30
        else if (voice.locale?.language?.lowercase() == "en") score += 10

        // Prefer higher synthesis quality and offline playback.
        score += when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> 25
            voice.quality >= Voice.QUALITY_HIGH -> 18
            voice.quality >= Voice.QUALITY_NORMAL -> 8
            else -> 0
        }
        if (!voice.isNetworkConnectionRequired) score += 15
        // Low latency keeps spoken confirmations instant.
        if (voice.latency <= Voice.LATENCY_LOW) score += 10

        return score
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            Log.d(TAG, "TTS not ready yet, queuing utterance: '$text'")
            pendingSpeakText = text
            pendingOnComplete = onComplete

            // Fallback timeout: if TTS engine doesn't initialize within 3s, proceed without audio
            if (initTimeoutRunnable == null) {
                initTimeoutRunnable = Runnable {
                    if (!isInitialized) {
                        Log.w(TAG, "TTS initialization timed out")
                        val cb = pendingOnComplete
                        pendingSpeakText = null
                        pendingOnComplete = null
                        cb?.invoke()
                    }
                }
                mainHandler.postDelayed(initTimeoutRunnable!!, 3000)
            }
            return
        }

        try {
            val utteranceId = "aurio_tts_${System.nanoTime()}"
            if (onComplete != null) {
                completionCallbacks[utteranceId] = onComplete
            }

            _isSpeaking.value = true
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                completionCallbacks.remove(utteranceId)
                _isSpeaking.value = false
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text: ${e.message}")
            _isSpeaking.value = false
            onComplete?.invoke()
        }
    }

    /**
     * Stops any in-flight speech. Pending completion callbacks are dropped, so callers that need
     * follow-up work after a cancelled utterance must drive it themselves.
     */
    fun stop() {
        try {
            completionCallbacks.clear()
            tts?.stop()
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            initTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            initTimeoutRunnable = null
            completionCallbacks.clear()
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}")
        }
    }

    companion object {
        private const val SWEET_PITCH = 1.28f
        private const val SWEET_RATE = 1.14f

        /** Known female voice identifiers (Google TTS, Samsung, and generic naming schemes). */
        private val FEMALE_VOICE_HINTS = listOf(
            "female", "-tpf-", "-sfg-", "#female", "_female",
            "en-us-x-tpf", "en-us-x-sfg", "en-us-x-iob", "en-us-x-iol",
            "en-gb-x-fis", "en-gb-x-gba", "en-in-x-ene", "en-in-x-cxx",
            "samantha", "karen", "moira", "tessa", "salli", "joanna", "kendra", "ivy"
        )

        private val MALE_VOICE_HINTS = listOf(
            "male", "-tpd-", "-sfb-", "#male", "_male",
            "en-us-x-iom", "en-us-x-tpd", "en-gb-x-gbd", "en-in-x-cxc"
        )
    }
}
