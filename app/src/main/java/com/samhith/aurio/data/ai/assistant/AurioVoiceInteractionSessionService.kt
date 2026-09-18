package com.samhith.aurio.data.ai.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Creates the session Android shows when the user invokes the assistant (e.g. holds power). */
class AurioVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AurioVoiceInteractionSession(this)
}
