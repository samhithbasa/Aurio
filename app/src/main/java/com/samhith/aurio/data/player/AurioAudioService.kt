package com.samhith.aurio.data.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.samhith.aurio.MainActivity
import com.samhith.aurio.R

/**
 * Foreground MediaSessionService enabling system notification controls,
 * lockscreen integration, and persistent background playback for Aurio.
 */
class AurioAudioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val TAG = "AurioAudioService"

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "aurio_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AurioAudioService onCreate")
        createNotificationChannel()

        val playerManager = AudioPlayerManager.getInstance(applicationContext)
        val player = playerManager.exoPlayer

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val session = MediaSession.Builder(this, player)
            .setId("AurioMediaSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        mediaSession = session
        addSession(session)

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_aurio_app_logo)
            }
        setMediaNotificationProvider(notificationProvider)
        Log.d(TAG, "MediaSession created, added to service, and notification provider set with Aurio logo")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Aurio Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media controls in the notification drawer while music is playing"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with startId: $startId")
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        Log.d(TAG, "onUpdateNotification: startInForegroundRequired=$startInForegroundRequired, isPlaying=${session.player.isPlaying}")
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            Log.d(TAG, "onTaskRemoved: player idle or null, stopping service")
            stopSelf()
        } else {
            Log.d(TAG, "onTaskRemoved: playback in progress, keeping service alive in background")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "AurioAudioService onDestroy")
        val player = mediaSession?.player
        mediaSession?.run {
            removeSession(this)
            release()
            mediaSession = null
        }
        // Only release the underlying ExoPlayer if it's actually stopped/idle — onTaskRemoved
        // already keeps the service (and thus this destroy path) from firing while playback is
        // still active, but this guards against onDestroy being triggered by other system paths
        // (e.g. low-memory kill) while something is still playing.
        if (player == null || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED || !player.playWhenReady) {
            AudioPlayerManager.getInstance(applicationContext).release()
        } else {
            Log.d(TAG, "onDestroy: player still active, skipping AudioPlayerManager.release()")
        }
        super.onDestroy()
    }
}
