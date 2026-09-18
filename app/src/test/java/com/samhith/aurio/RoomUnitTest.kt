package com.samhith.aurio

import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.data.room.MockRoomData
import com.samhith.aurio.data.room.Room
import com.samhith.aurio.data.room.RoomMessage
import com.samhith.aurio.data.room.RoomParticipant
import com.samhith.aurio.data.room.RoomRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying Listen Together data models, JSON serialization,
 * room code generation, and RoomRepository operations.
 */
class RoomUnitTest {

    @Test
    fun testRoomCodeGeneration() {
        val code = MockRoomData.generateRoomCode()
        assertEquals(4, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testRoomJsonSerialization() {
        val room = Room(
            id = "room_test_123",
            name = "Late Night Vibes",
            isPublic = true,
            hostName = "Samhith",
            hostId = "user_456",
            listenerCount = 5,
            tags = listOf("Chill", "Lo-fi"),
            code = "9876",
            currentSongId = "song_yt_001",
            currentSongTitle = "Die With A Smile",
            currentSongArtist = "Lady Gaga & Bruno Mars",
            isPlaying = true,
            playbackPositionMs = 45000L
        )

        val json = room.toJson()
        val deserialized = Room.fromJson(json)

        assertEquals("room_test_123", deserialized.id)
        assertEquals("Late Night Vibes", deserialized.name)
        assertTrue(deserialized.isPublic)
        assertEquals("Samhith", deserialized.hostName)
        assertEquals("user_456", deserialized.hostId)
        assertEquals(5, deserialized.listenerCount)
        assertEquals(listOf("Chill", "Lo-fi"), deserialized.tags)
        assertEquals("9876", deserialized.code)
        assertEquals("song_yt_001", deserialized.currentSongId)
        assertEquals("Die With A Smile", deserialized.currentSongTitle)
        assertEquals("Lady Gaga & Bruno Mars", deserialized.currentSongArtist)
        assertTrue(deserialized.isPlaying)
        assertEquals(45000L, deserialized.playbackPositionMs)
    }

    @Test
    fun testRoomParticipantSerialization() {
        val participant = RoomParticipant(
            id = "room1_user2",
            roomId = "room1",
            userId = "user2",
            name = "Aarav",
            avatarUrl = "https://example.com/avatar.jpg",
            isHost = true,
            isOnline = true
        )

        val json = participant.toJson()
        val deserialized = RoomParticipant.fromJson(json)

        assertEquals("room1", deserialized.roomId)
        assertEquals("user2", deserialized.userId)
        assertEquals("Aarav", deserialized.name)
        assertTrue(deserialized.isHost)
        assertTrue(deserialized.isOnline)
    }

    @Test
    fun testRoomMessageSerialization() {
        val message = RoomMessage(
            id = "msg_001",
            roomId = "room1",
            senderId = "user_1",
            senderName = "Sneha",
            text = "Awesome song! 🔥"
        )

        val json = message.toJson()
        val deserialized = RoomMessage.fromJson(json)

        assertEquals("room1", deserialized.roomId)
        assertEquals("user_1", deserialized.senderId)
        assertEquals("Sneha", deserialized.senderName)
        assertEquals("Awesome song! 🔥", deserialized.text)
    }

    @Test
    fun testRoomRepositoryLocalFallbackCRUD() = runBlocking {
        val repository = RoomRepository.getInstance()
        val mockUser = UserAccount(
            email = "test@aurio.app",
            displayName = "Tester",
            supabaseId = "usr_test_999"
        )

        // 1. Create Room
        val createResult = repository.createRoom(
            name = "Test Room",
            isPublic = true,
            host = mockUser,
            tags = listOf("Test", "Live")
        )
        assertTrue(createResult.isSuccess)
        val room = createResult.getOrThrow()
        assertNotNull(room.id)
        assertEquals("Test Room", room.name)

        // 2. Query Room by Code
        val foundRoom = repository.getRoomByCode(room.code)
        assertNotNull(foundRoom)
        assertEquals(room.id, foundRoom?.id)

        // 3. Send Message
        val msgResult = repository.sendMessage(room.id, mockUser, "Hello Room!")
        assertTrue(msgResult.isSuccess)
        val messages = repository.getMessages(room.id)
        assertTrue(messages.any { it.text == "Hello Room!" })

        // 4. Update Playback
        val updateResult = repository.updateRoomPlayback(
            roomId = room.id,
            songId = "song_123",
            songTitle = "Starboy",
            songArtist = "The Weeknd",
            songThumbnail = "https://thumb.url",
            isPlaying = true,
            positionMs = 12000L
        )
        assertTrue(updateResult.isSuccess)

        // 5. Query updated room
        val updatedRoom = repository.getRoomById(room.id)
        assertEquals("song_123", updatedRoom?.currentSongId)
        assertEquals("Starboy", updatedRoom?.currentSongTitle)
        assertTrue(updatedRoom?.isPlaying == true)
    }
}
