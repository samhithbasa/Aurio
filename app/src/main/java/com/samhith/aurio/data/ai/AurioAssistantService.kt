package com.samhith.aurio.data.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.samhith.aurio.MainActivity
import com.samhith.aurio.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service backing the Aurio voice assistant.
 *
 * It owns the system-wide floating bubble (mode 2) and keeps the singleton wake-word manager
 * alive so a bubble tap can open a microphone session while the user is outside the app.
 * It does NOT keep the microphone open on its own: hands-free hotword listening belongs to
 * mode 1 and is armed only while MainActivity is in the foreground.
 */
class AurioAssistantService : Service() {

    private val TAG = "AurioAssistantService"
    private val CHANNEL_ID = "aurio_voice_assistant_channel"
    private val NOTIFICATION_ID = 2002

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initWakeWordEngine()
        initSystemOverlay()
        observeAssistantStateForBubble()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aurio Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating assistant bubble and hands-free music commands"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aurio AI Assistant")
            .setContentText("Tap the floating bubble to give a command")
            .setSmallIcon(R.drawable.ic_aurio_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initWakeWordEngine() {
        try {
            val wakeWordManager = AurioWakeWordManager.getInstance(applicationContext)
            wakeWordManager.onExecutionResult = { result ->
                Log.d(TAG, "Background AI Command executed: ${result.spokenResponse}")
            }
            wakeWordManager.startContinuousListening()
            Log.d(TAG, "AurioAssistantService assistant engine ready (singleton).")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake-word engine in service: ${e.message}")
        }
    }

    private fun initSystemOverlay() {
        val overlayManager = AurioSystemOverlayManager.getInstance(applicationContext)
        overlayManager.onBubbleClick = {
            // Mode 2: an explicit, user-initiated session outside the app.
            AurioWakeWordManager.getInstance(applicationContext)
                .triggerManualAwake(AssistantMode.FLOATING)
        }
        if (overlayManager.hasOverlayPermission()) {
            overlayManager.showFloatingBubble()
        }
    }

    /**
     * Mirrors the assistant onto the floating bubble so mode 2 gives visible feedback: the bubble
     * pulses while listening and shows the live transcript and the reply in its status pill.
     */
    private fun observeAssistantStateForBubble() {
        val wakeWordManager = AurioWakeWordManager.getInstance(applicationContext)
        val overlayManager = AurioSystemOverlayManager.getInstance(applicationContext)

        serviceScope.launch {
            wakeWordManager.state.collect { state ->
                overlayManager.updateAssistantState(state)
            }
        }
        serviceScope.launch {
            wakeWordManager.liveTranscript.collect { transcript ->
                if (transcript.isNotBlank()) {
                    overlayManager.setStatusText(transcript)
                }
            }
        }
        serviceScope.launch {
            wakeWordManager.responseMessage.collect { response ->
                overlayManager.setStatusText(response)
            }
        }
        serviceScope.launch {
            wakeWordManager.rmsVolume.collect { rms ->
                overlayManager.updateVoiceLevel(rms)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val overlayManager = AurioSystemOverlayManager.getInstance(applicationContext)
        if (overlayManager.hasOverlayPermission()) {
            overlayManager.showFloatingBubble()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // When app is swiped off from recent tasks, guarantee the microphone is released
        AurioWakeWordManager.getInstance(applicationContext).setAppInForeground(false)
        Log.d(TAG, "App swiped off: Microphone released, bubble still available for mode 2.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        AurioWakeWordManager.getInstance(applicationContext).setAppInForeground(false)
        AurioSystemOverlayManager.getInstance(applicationContext).hideFloatingBubble()
        Log.d(TAG, "AurioAssistantService destroyed.")
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, AurioAssistantService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("AurioAssistantService", "Failed to start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, AurioAssistantService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
