package com.samhith.aurio.data.room

import android.util.Log
import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.data.player.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Real-time coordinator for an active listening room session.
 * Synchronizes playback state between Host and Listeners, manages live participants,
 * and handles in-room chat message streams.
 */
class RoomSessionManager private constructor() {

    private val TAG = "RoomSessionManager"
    private val repository = RoomRepository.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _activeRoom = MutableStateFlow<Room?>(null)
    val activeRoom: StateFlow<Room?> = _activeRoom.asStateFlow()

    private val _participants = MutableStateFlow<List<RoomParticipant>>(emptyList())
    val participants: StateFlow<List<RoomParticipant>> = _participants.asStateFlow()

    private val _messages = MutableStateFlow<List<RoomMessage>>(emptyList())
    val messages: StateFlow<List<RoomMessage>> = _messages.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<SongRequest>>(emptyList())
    val pendingRequests: StateFlow<List<SongRequest>> = _pendingRequests.asStateFlow()

    private val _requestStatuses = MutableStateFlow<Map<String, SongRequestStatus>>(emptyMap())
    val requestStatuses: StateFlow<Map<String, SongRequestStatus>> = _requestStatuses.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var syncJob: Job? = null
    private var currentPlayerManager: AudioPlayerManager? = null
    private var currentUser: UserAccount? = null

    /**
     * Extracts and updates song requests and their accepted/declined statuses from room messages.
     */
    private fun processIncomingMessages(messagesList: List<RoomMessage>) {
        val currentStatusMap = _requestStatuses.value.toMutableMap()
        for (msg in messagesList) {
            val statusUpdate = SongRequest.parseStatusUpdate(msg.text)
            if (statusUpdate != null) {
                currentStatusMap[statusUpdate.first] = statusUpdate.second
            }
        }
        _requestStatuses.value = currentStatusMap

        if (_isHost.value) {
            val requestsInChat = messagesList.mapNotNull { SongRequest.fromMessageText(it.text) }
            val currentPending = _pendingRequests.value.filter { req ->
                val status = currentStatusMap[req.id] ?: req.status
                status == SongRequestStatus.PENDING
            }.toMutableList()

            for (req in requestsInChat) {
                val status = currentStatusMap[req.id] ?: req.status
                if (status == SongRequestStatus.PENDING && currentPending.none { it.id == req.id }) {
                    currentPending.add(req)
                }
            }
            _pendingRequests.value = currentPending
        }
    }

    /**
     * Enters a room and starts the live real-time sync cycle.
     */
    fun enterRoom(
        room: Room,
        user: UserAccount?,
        playerManager: AudioPlayerManager
    ) {
        syncJob?.cancel()
        _activeRoom.value = room
        currentPlayerManager = playerManager
        currentUser = user

        val userId = user?.supabaseId?.ifBlank { user.email }?.ifBlank { "local_user" } ?: "local_user"
        val isUserHost = room.hostId == userId || room.hostName == "You" || room.hostName == user?.displayName

        _isHost.value = isUserHost
        _participants.value = listOf(
            RoomParticipant(
                id = "${room.id}_$userId",
                roomId = room.id,
                userId = userId,
                name = user?.displayName?.ifBlank { "You" } ?: "You",
                avatarUrl = user?.photoUrl ?: "",
                isHost = isUserHost,
                isOnline = true
            )
        )

        // Initial background join registration
        scope.launch(Dispatchers.IO) {
            repository.joinRoom(room.id, user, isHost = isUserHost)
            val initialParticipants = repository.getParticipants(room.id)
            val initialMessages = repository.getMessages(room.id)
            scope.launch(Dispatchers.Main) {
                if (initialParticipants.isNotEmpty()) _participants.value = initialParticipants
                if (initialMessages.isNotEmpty()) {
                    _messages.value = initialMessages
                    processIncomingMessages(initialMessages)
                }
            }
        }

        // Start continuous real-time sync loop (every 1.5 seconds)
        syncJob = scope.launch(Dispatchers.IO) {
            var tickCount = 0
            while (isActive) {
                try {
                    val roomId = _activeRoom.value?.id ?: break
                    val currentRoomData = repository.getRoomById(roomId)

                    if (currentRoomData == null && !_isHost.value) {
                        // Room was ended/deleted by the host!
                        Log.d(TAG, "Room $roomId was ended by host, auto-terminating listener session.")
                        scope.launch(Dispatchers.Main) {
                            leaveRoom()
                        }
                        break
                    }

                    if (currentRoomData != null) {
                        _activeRoom.value = currentRoomData

                        if (_isHost.value) {
                            // HOST BROADCAST: Push ExoPlayer status to Supabase
                            val currentSong = playerManager.currentSong.value
                            val isPlaying = playerManager.isPlaying.value
                            val positionMs = playerManager.playbackPositionMs.value

                            if (currentSong != null) {
                                repository.updateRoomPlayback(
                                    roomId = roomId,
                                    songId = currentSong.id,
                                    songTitle = currentSong.title,
                                    songArtist = currentSong.artist,
                                    songThumbnail = currentSong.thumbnailUrl,
                                    isPlaying = isPlaying,
                                    positionMs = positionMs
                                )
                            }
                        } else {
                            // LISTENER SYNC: Match Host's playback state
                            if (currentRoomData.currentSongId.isNotBlank()) {
                                scope.launch(Dispatchers.Main) {
                                    syncListenerPlayback(currentRoomData, playerManager)
                                }
                            }
                        }
                    }

                    // Refresh participants & messages every 2 ticks (~3 sec) or on new activity
                    tickCount++
                    if (tickCount % 2 == 0) {
                        val freshParticipants = repository.getParticipants(roomId)
                        val freshMessages = repository.getMessages(roomId)
                        scope.launch(Dispatchers.Main) {
                            if (freshParticipants.isNotEmpty()) _participants.value = freshParticipants
                            if (freshMessages.isNotEmpty()) {
                                _messages.value = freshMessages
                                processIncomingMessages(freshMessages)
                            }
                        }

                        // For Host: poll pending song requests
                        if (_isHost.value) {
                            val freshReqs = repository.getPendingRequests(roomId)
                            scope.launch(Dispatchers.Main) {
                                if (freshReqs.isNotEmpty()) {
                                    val current = _pendingRequests.value.toMutableList()
                                    for (req in freshReqs) {
                                        val status = _requestStatuses.value[req.id] ?: req.status
                                        if (status == SongRequestStatus.PENDING && current.none { it.id == req.id }) {
                                            current.add(req)
                                        }
                                    }
                                    _pendingRequests.value = current
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error in room sync loop", e)
                }

                delay(1500)
            }
        }
    }

    /**
     * Synchronizes playback state for a listener following the room host.
     */
    private fun syncListenerPlayback(room: Room, playerManager: AudioPlayerManager) {
        val currentSong = playerManager.currentSong.value

        // 1. Song track synchronization
        if (currentSong?.id != room.currentSongId) {
            Log.d(TAG, "Listener syncing new track from room: ${room.currentSongTitle}")
            val targetSong = SongItem(
                id = room.currentSongId,
                title = room.currentSongTitle,
                artist = room.currentSongArtist,
                thumbnailUrl = room.currentSongThumbnail
            )
            playerManager.playSong(targetSong)
        }

        // 2. Play / Pause state synchronization
        if (room.isPlaying && !playerManager.isPlaying.value) {
            playerManager.exoPlayer.play()
        } else if (!room.isPlaying && playerManager.isPlaying.value) {
            playerManager.exoPlayer.pause()
        }

        // 3. Time drift synchronization (auto-align if drift exceeds 2.5s)
        val elapsedSinceSync = if (room.isPlaying && room.lastSyncTimestamp > 0) {
            (System.currentTimeMillis() - room.lastSyncTimestamp).coerceAtLeast(0L)
        } else {
            0L
        }
        val expectedPosition = (room.playbackPositionMs + elapsedSinceSync).coerceAtLeast(0L)
        val currentPosition = playerManager.exoPlayer.currentPosition

        if (room.isPlaying && abs(currentPosition - expectedPosition) > 2500L) {
            Log.d(TAG, "Drift detected (${abs(currentPosition - expectedPosition)}ms), syncing position to ${expectedPosition}ms")
            playerManager.exoPlayer.seekTo(expectedPosition)
        }
    }

    /**
     * Sends a chat message in the active room.
     */
    fun sendMessage(text: String) {
        val room = _activeRoom.value ?: return
        val user = currentUser

        scope.launch(Dispatchers.IO) {
            val result = repository.sendMessage(room.id, user, text)
            result.onSuccess { newMsg ->
                scope.launch(Dispatchers.Main) {
                    _messages.value = _messages.value + newMsg
                }
            }
        }
    }

    /**
     * Submits a song request from a listener in the active room.
     */
    fun submitSongRequest(
        song: SongItem,
        type: SongRequestType,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val room = _activeRoom.value ?: return
        val user = currentUser
        val requesterId = user?.supabaseId?.ifBlank { user.email }?.ifBlank { "listener_${System.currentTimeMillis()}" } ?: "listener_${System.currentTimeMillis()}"
        val requesterName = user?.displayName?.ifBlank { "Listener" } ?: "Listener"

        val request = SongRequest(
            id = "req_${System.currentTimeMillis()}_${(1000..9999).random()}",
            roomId = room.id,
            song = song,
            requesterId = requesterId,
            requesterName = requesterName,
            requestType = type,
            status = SongRequestStatus.PENDING
        )

        scope.launch(Dispatchers.IO) {
            val result = repository.submitSongRequest(request)
            if (result.isSuccess) {
                // If local user is host (e.g. testing with 1 device), add to pendingRequests immediately
                if (_isHost.value) {
                    scope.launch(Dispatchers.Main) {
                        _pendingRequests.value = _pendingRequests.value + request
                    }
                }
                // Send the request embedded in a chat message so it appears as a rich card
                repository.sendMessage(
                    roomId = room.id,
                    sender = user,
                    text = request.toMessageText()
                ).onSuccess { newMsg ->
                    scope.launch(Dispatchers.Main) {
                        _messages.value = _messages.value + newMsg
                        processIncomingMessages(_messages.value)
                    }
                }
                scope.launch(Dispatchers.Main) { onComplete?.invoke(true) }
            } else {
                scope.launch(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    /**
     * Responds to a pending song request (Host accept or decline).
     */
    fun respondToSongRequest(request: SongRequest, accept: Boolean) {
        val room = _activeRoom.value ?: return
        val newStatus = if (accept) SongRequestStatus.ACCEPTED else SongRequestStatus.DECLINED

        // Remove from local pending requests list and track the status
        _pendingRequests.value = _pendingRequests.value.filter { it.id != request.id }
        _requestStatuses.value = _requestStatuses.value + (request.id to newStatus)

        scope.launch(Dispatchers.IO) {
            repository.updateRequestStatus(request.id, room.id, newStatus)

            if (accept) {
                scope.launch(Dispatchers.Main) {
                    val player = currentPlayerManager
                    if (player != null) {
                        if (request.requestType == SongRequestType.PLAY_NEXT) {
                            player.playNextInQueue(request.song)
                        } else {
                            player.addToQueue(request.song)
                        }
                    }
                }
                repository.sendMessage(
                    roomId = room.id,
                    sender = currentUser,
                    text = "✅ Host accepted '${request.song.title}' requested by ${request.requesterName}! [STATUS_UPDATE:${newStatus.name}:${request.id}]"
                ).onSuccess { newMsg ->
                    scope.launch(Dispatchers.Main) {
                        _messages.value = _messages.value + newMsg
                        processIncomingMessages(_messages.value)
                    }
                }
            } else {
                repository.sendMessage(
                    roomId = room.id,
                    sender = currentUser,
                    text = "❌ Host declined '${request.song.title}' requested by ${request.requesterName}. [STATUS_UPDATE:${newStatus.name}:${request.id}]"
                ).onSuccess { newMsg ->
                    scope.launch(Dispatchers.Main) {
                        _messages.value = _messages.value + newMsg
                        processIncomingMessages(_messages.value)
                    }
                }
            }
        }
    }

    /**
     * Leaves the active room session and cleans up background sync.
     * If user is the Host, the room is permanently closed and deleted.
     */
    fun leaveRoom() {
        syncJob?.cancel()
        syncJob = null

        val room = _activeRoom.value
        val user = currentUser
        val userId = user?.supabaseId?.ifBlank { user.email }?.ifBlank { "local_user" } ?: "local_user"
        val wasHost = _isHost.value

        if (room != null) {
            scope.launch(Dispatchers.IO) {
                if (wasHost) {
                    repository.deleteRoom(room.id)
                } else {
                    repository.leaveRoom(room.id, userId)
                }
            }
        }

        _activeRoom.value = null
        _participants.value = emptyList()
        _messages.value = emptyList()
        _pendingRequests.value = emptyList()
        _requestStatuses.value = emptyMap()
        _isHost.value = false
        currentPlayerManager = null
        currentUser = null
    }

    companion object {
        @Volatile
        private var instance: RoomSessionManager? = null

        fun getInstance(): RoomSessionManager {
            return instance ?: synchronized(this) {
                instance ?: RoomSessionManager().also { instance = it }
            }
        }
    }
}
