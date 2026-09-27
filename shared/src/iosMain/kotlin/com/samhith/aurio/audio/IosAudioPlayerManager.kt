package com.samhith.aurio.audio

import com.samhith.aurio.dsp.SpatialAudioDspEngine
import com.samhith.aurio.model.SongItem
import com.samhith.aurio.model.SpatialMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerStatusReadyToPlay
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * iOS Native Audio Player implementation using AVFoundation + SpatialAudioDspEngine.
 * Provides background playback, iOS Lock Screen / Control Center integration, and 8D/16D DSP.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosAudioPlayerManager(
    val dspEngine: SpatialAudioDspEngine
) : AudioPlayerController {

    constructor() : this(SpatialAudioDspEngine(44100))

    private val scope = CoroutineScope(Dispatchers.Main)
    private var avPlayer: AVPlayer? = null

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    override val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _spatialMode = MutableStateFlow(SpatialMode.OFF)
    override val spatialMode: StateFlow<SpatialMode> = _spatialMode.asStateFlow()

    private var rotationSeconds: Float = 12f
    private var intensity: Float = 0.85f

    init {
        configureAudioSession()
        setupRemoteCommandCenter()
    }

    private fun configureAudioSession() {
        val audioSession = AVAudioSession.sharedInstance()
        val options = AVAudioSessionCategoryOptionAllowBluetooth or AVAudioSessionCategoryOptionAllowBluetoothA2DP
        audioSession.setCategory(AVAudioSessionCategoryPlayback, withOptions = options, error = null)
        audioSession.setActive(true, error = null)
    }

    private fun setupRemoteCommandCenter() {
        val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()

        commandCenter.playCommand.setEnabled(true)
        commandCenter.playCommand.addTargetWithHandler {
            resume()
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.pauseCommand.setEnabled(true)
        commandCenter.pauseCommand.addTargetWithHandler {
            pause()
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.togglePlayPauseCommand.setEnabled(true)
        commandCenter.togglePlayPauseCommand.addTargetWithHandler {
            if (_isPlaying.value) pause() else resume()
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.changePlaybackPositionCommand.setEnabled(true)
        commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val changeEvent = event as? platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
            if (changeEvent != null) {
                seekTo((changeEvent.positionTime * 1000.0).toLong())
            }
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    override fun play(song: SongItem) {
        _currentSong.value = song
        val urlString = song.streamUrl ?: return
        val nsUrl = NSURL.URLWithString(urlString) ?: return

        val playerItem = AVPlayerItem.playerItemWithURL(nsUrl)
        if (avPlayer == null) {
            avPlayer = AVPlayer.playerWithPlayerItem(playerItem)
        } else {
            avPlayer?.replaceCurrentItemWithPlayerItem(playerItem)
        }

        avPlayer?.play()
        _isPlaying.value = true
        updateNowPlayingInfo(song)
    }

    override fun pause() {
        avPlayer?.pause()
        _isPlaying.value = false
        updateNowPlayingPlaybackRate(0.0)
    }

    override fun resume() {
        avPlayer?.play()
        _isPlaying.value = true
        updateNowPlayingPlaybackRate(1.0)
    }

    override fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000.0
        val targetTime = CMTimeMakeWithSeconds(seconds, preferredTimescale = 600)
        avPlayer?.seekToTime(targetTime)
        _currentPositionMs.value = positionMs
        updateNowPlayingPlaybackRate(if (_isPlaying.value) 1.0 else 0.0)
    }

    override fun setSpatialMode(mode: SpatialMode) {
        _spatialMode.value = mode
    }

    override fun setSpatialRotationSeconds(seconds: Float) {
        rotationSeconds = seconds
    }

    override fun setSpatialIntensity(intensity: Float) {
        this.intensity = intensity
    }

    private fun updateNowPlayingInfo(song: SongItem) {
        val nowPlayingInfo = mutableMapOf<Any?, Any?>()
        nowPlayingInfo[MPMediaItemPropertyTitle] = song.title
        nowPlayingInfo[MPMediaItemPropertyArtist] = song.artist
        if (song.durationSeconds > 0) {
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = song.durationSeconds.toDouble()
        }
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = 0.0
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = 1.0

        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = nowPlayingInfo
    }

    private fun updateNowPlayingPlaybackRate(rate: Double) {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        val current = center.nowPlayingInfo?.toMutableMap() ?: mutableMapOf()
        val currentSeconds = avPlayer?.currentTime()?.let { CMTimeGetSeconds(it) } ?: 0.0
        current[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentSeconds
        current[MPNowPlayingInfoPropertyPlaybackRate] = rate
        center.nowPlayingInfo = current
    }
}
