package com.samhith.aurio.model

/**
 * Cross-platform data model representing a playable song/track in Aurio.
 */
data class SongItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val durationText: String = "",
    val thumbnailUrl: String = "",
    val streamUrl: String? = null,
    val encryptedMediaUrl: String? = null
)
