package com.samhith.aurio.data.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AiVoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

/**
 * Robust Speech Recognition controller for Aurio AI Assistant.
 * Wraps Android SpeechRecognizer, emits live transcript flows and normalized volume dB
 * for audio-reactive glowing visualizer animations.
 */
class AurioVoiceRecognizer(private val context: Context) {

    private val TAG = "AurioVoiceRecognizer"
    private var speechRecognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow(AiVoiceState.IDLE)
    val voiceState: StateFlow<AiVoiceState> = _voiceState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _finalTranscript = MutableStateFlow("")
    val finalTranscript: StateFlow<String> = _finalTranscript.asStateFlow()

    private val _rmsVolume = MutableStateFlow(0f)
    val rmsVolume: StateFlow<Float> = _rmsVolume.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var onSpeechRecognized: ((String) -> Unit)? = null

    init {
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechRecognizer: ${e.message}")
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _voiceState.value = AiVoiceState.LISTENING
            _errorMessage.value = null
        }

        override fun onBeginningOfSpeech() {
            _voiceState.value = AiVoiceState.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize -2dB..12dB to 0.0f..1.0f for wave visualization
            val normalized = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
            _rmsVolume.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _voiceState.value = AiVoiceState.PROCESSING
            _rmsVolume.value = 0f
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy"
                SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                else -> "Recognition error ($error)"
            }
            Log.w(TAG, "SpeechRecognizer error: $message ($error)")
            _rmsVolume.value = 0f

            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                _voiceState.value = AiVoiceState.IDLE
            } else {
                _errorMessage.value = message
                _voiceState.value = AiVoiceState.ERROR
            }
        }

        override fun onResults(results: Bundle?) {
            _rmsVolume.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()

            if (recognizedText.isNotBlank()) {
                _finalTranscript.value = recognizedText
                _partialTranscript.value = recognizedText
                _voiceState.value = AiVoiceState.PROCESSING
                onSpeechRecognized?.invoke(recognizedText)
            } else {
                _voiceState.value = AiVoiceState.IDLE
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim().orEmpty()
            if (partial.isNotBlank()) {
                _partialTranscript.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        try {
            if (speechRecognizer == null) {
                initSpeechRecognizer()
            }
            _partialTranscript.value = ""
            _finalTranscript.value = ""
            _errorMessage.value = null

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            _voiceState.value = AiVoiceState.LISTENING
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recognition: ${e.message}")
            _errorMessage.value = "Failed to start listening"
            _voiceState.value = AiVoiceState.ERROR
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _rmsVolume.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice recognition: ${e.message}")
        }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
            _voiceState.value = AiVoiceState.IDLE
            _rmsVolume.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition: ${e.message}")
        }
    }

    fun setSpeakingState(speaking: Boolean) {
        if (speaking) {
            _voiceState.value = AiVoiceState.SPEAKING
        } else if (_voiceState.value == AiVoiceState.SPEAKING || _voiceState.value == AiVoiceState.PROCESSING) {
            _voiceState.value = AiVoiceState.IDLE
        }
    }

    fun reset() {
        cancel()
        _partialTranscript.value = ""
        _finalTranscript.value = ""
        _errorMessage.value = null
        _voiceState.value = AiVoiceState.IDLE
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
        }
    }
}
