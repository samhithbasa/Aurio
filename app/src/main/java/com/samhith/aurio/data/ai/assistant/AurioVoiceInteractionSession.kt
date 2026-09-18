package com.samhith.aurio.data.ai.assistant

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.samhith.aurio.data.ai.AssistantMode
import com.samhith.aurio.data.ai.AurioAssistantService
import com.samhith.aurio.data.ai.AurioWakeWordManager
import com.samhith.aurio.ui.ai.SiriWaveAssistantOverlay
import com.samhith.aurio.ui.theme.AurioTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the user sees after holding the power button (or using the assist gesture).
 *
 * If Aurio is already on screen, its own in-app overlay presents the conversation, so this window
 * starts the session and steps aside at once. Everywhere else - other apps, the home screen, the
 * lock screen - it draws the same Siri-style panel over whatever is showing, and closes itself the
 * moment the assistant finishes. The session window counts as visible UI, which is what lets the
 * microphone open while Aurio is otherwise in the background.
 */
class AurioVoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private var scope: CoroutineScope? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val wakeWordManager: AurioWakeWordManager
        get() = AurioWakeWordManager.getInstance(context)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        // Transparent window: only the panel is drawn, the app underneath stays visible
        window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@AurioVoiceInteractionSession)
            setViewTreeSavedStateRegistryOwner(this@AurioVoiceInteractionSession)
            setContent {
                AurioTheme {
                    AssistantPanel()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AssistantPanel() {
        val manager = remember { wakeWordManager }
        val isVisible by manager.isOverlayVisible.collectAsState()
        val state by manager.state.collectAsState()
        val assistantName by manager.customAssistantName.collectAsState()
        val trainingState by manager.trainingState.collectAsState()
        val liveTranscript by manager.liveTranscript.collectAsState()
        val responseMessage by manager.responseMessage.collectAsState()
        val rmsVolume by manager.rmsVolume.collectAsState()

        // Tapping anywhere outside the panel dismisses, like Siri
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { manager.dismissOverlay() }
                )
        ) {
            SiriWaveAssistantOverlay(
                isVisible = isVisible,
                state = state,
                assistantName = assistantName,
                trainingState = trainingState,
                liveTranscript = liveTranscript,
                responseMessage = responseMessage,
                rmsVolume = rmsVolume,
                onDismiss = { manager.dismissOverlay() },
                onChipClick = { query -> manager.submitTextCommand(query) },
                onConfirmCustomName = { name -> manager.submitCustomNameFromUi(name) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // Keeps microphone access available for the command while Aurio is in the background
        AurioAssistantService.start(context)

        val manager = wakeWordManager
        if (manager.isAppOnScreen()) {
            // Aurio's own overlay presents the session; this window is not needed
            manager.triggerManualAwake(AssistantMode.IN_APP)
            hide()
            return
        }

        manager.triggerManualAwake(AssistantMode.FLOATING)
        closeWhenAssistantFinishes()
    }

    /** Hides this window once the conversation ends (or after a safety timeout). */
    private fun closeWhenAssistantFinishes() {
        scope?.cancel()
        val sessionScope = CoroutineScope(Dispatchers.Main + Job())
        scope = sessionScope
        sessionScope.launch {
            withTimeoutOrNull(SESSION_WINDOW_TIMEOUT_MS) {
                val overlay = wakeWordManager.isOverlayVisible
                overlay.first { it } // wait for the session to open
                overlay.first { !it } // then for it to close
            }
            hide()
        }
    }

    override fun onHide() {
        scope?.cancel()
        scope = null
        // Closing the window (back, home, tap outside) ends the assistant session too
        if (wakeWordManager.isOverlayVisible.value && !wakeWordManager.isAppOnScreen()) {
            wakeWordManager.dismissOverlay()
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onHide()
    }

    override fun onDestroy() {
        scope?.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private companion object {
        /** Longest a session window may stay up, even if the assistant never reports it is done. */
        const val SESSION_WINDOW_TIMEOUT_MS = 45_000L
    }
}
