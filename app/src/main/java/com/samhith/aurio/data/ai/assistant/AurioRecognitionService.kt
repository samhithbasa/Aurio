package com.samhith.aurio.data.ai.assistant

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Speech recognition service Android requires every digital assistant to ship.
 *
 * When the user picks Aurio as the phone's digital assistant, Android also makes this service the
 * device-wide default speech recognizer - every app that calls SpeechRecognizer without naming a
 * specific engine lands here. A do-nothing stub would silently break voice input in those apps, so
 * this forwards each request to a real engine: the platform's on-device recognizer when present,
 * otherwise another installed recognition service (never this one).
 */
class AurioRecognitionService : RecognitionService() {

    private val TAG = "AurioRecognitionService"
    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        releaseDelegate()
        val recognizer = createDelegate()
        if (recognizer == null) {
            Log.e(TAG, "No speech recognition engine available to forward to.")
            safely { listener.error(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        delegate = recognizer
        recognizer.setRecognitionListener(ForwardingListener(listener))
        recognizer.startListening(recognizerIntent)
    }

    override fun onStopListening(listener: Callback) {
        delegate?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        releaseDelegate()
    }

    override fun onDestroy() {
        releaseDelegate()
        super.onDestroy()
    }

    private fun createDelegate(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        }
        val engine = findOtherRecognitionService() ?: return null
        return SpeechRecognizer.createSpeechRecognizer(this, engine)
    }

    /** Another app's recognition service, preferring Google's. Never Aurio's own, which would loop. */
    private fun findOtherRecognitionService(): ComponentName? {
        val services = packageManager.queryIntentServices(Intent(SERVICE_INTERFACE), 0)
            .map { it.serviceInfo }
            .filter { it.packageName != packageName }
        val preferred = services.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" }
            ?: services.firstOrNull { it.packageName == "com.google.android.as" }
            ?: services.firstOrNull()
        return preferred?.let { ComponentName(it.packageName, it.name) }
    }

    private fun releaseDelegate() {
        try {
            delegate?.cancel()
            delegate?.destroy()
        } catch (_: Exception) {
        }
        delegate = null
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Recognition client went away: ${e.message}")
        }
    }

    private inner class ForwardingListener(private val client: Callback) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = safely { client.readyForSpeech(params ?: Bundle()) }
        override fun onBeginningOfSpeech() = safely { client.beginningOfSpeech() }
        override fun onRmsChanged(rmsdB: Float) = safely { client.rmsChanged(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) = safely { if (buffer != null) client.bufferReceived(buffer) }
        override fun onEndOfSpeech() = safely { client.endOfSpeech() }
        override fun onPartialResults(partialResults: Bundle?) = safely { client.partialResults(partialResults ?: Bundle()) }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            safely { client.error(error) }
            releaseDelegate()
        }

        override fun onResults(results: Bundle?) {
            safely { client.results(results ?: Bundle()) }
            releaseDelegate()
        }
    }
}
