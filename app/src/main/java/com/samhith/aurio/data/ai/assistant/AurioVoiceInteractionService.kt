package com.samhith.aurio.data.ai.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Registers Aurio as a selectable digital assistant (Settings -> Default apps -> Digital assistant).
 *
 * Once chosen, holding the power button (or the swipe-from-corner assist gesture) opens an Aurio
 * session from anywhere - other apps, the home screen, even the lock screen - with the microphone
 * off until that moment. The actual work happens in [AurioVoiceInteractionSession].
 */
class AurioVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.d("AurioVoiceInteraction", "Aurio is the active digital assistant.")
    }
}
