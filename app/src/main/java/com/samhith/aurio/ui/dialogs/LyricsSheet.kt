package com.samhith.aurio.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samhith.aurio.data.lyrics.LyricLine
import com.samhith.aurio.data.lyrics.LyricsRepository
import com.samhith.aurio.data.lyrics.SongLyrics
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.ui.auth.aurioGlow
import kotlinx.coroutines.launch
import com.samhith.aurio.ui.theme.AppleBackground
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel

/**
 * Full-height blurred frosted glass sheet displaying synchronized or plain lyrics
 * for the currently playing track, with real-time active line highlighting and tap-to-seek.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    song: SongItem?,
    playbackPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lyricsRepo = remember { LyricsRepository.getInstance() }
    val scope = rememberCoroutineScope()

    var lyricsState by remember { mutableStateOf<SongLyrics?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Fetch lyrics whenever current song changes
    LaunchedEffect(song?.id) {
        if (song != null) {
            isLoading = true
            hasError = false
            val result = lyricsRepo.getLyrics(
                title = song.title,
                artist = song.artist,
                durationSeconds = song.durationSeconds
            )
            lyricsState = result
            isLoading = false
            hasError = result == null || !result.hasLyrics
        } else {
            lyricsState = null
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppleSurface.copy(alpha = 0.97f),
        dragHandle = null,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppleSurface.copy(alpha = 0.96f),
                            AppleBackground.copy(alpha = 0.98f),
                            AppleBackground
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Collapse Button (Down Chevron)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Lyrics",
                            tint = AppleLabel,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Centered Song Title & Artist
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = song?.title ?: "No Track Playing",
                            color = AppleLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song?.artist ?: "Aurio Music",
                            color = AppleSecondaryLabel,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // "LYRICS" Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppleFill)
                            .border(1.dp, AppleBlue.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LYRICS",
                            color = AppleBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Body Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = AppleBlue,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Fetching lyrics...",
                                color = AppleSecondaryLabel,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    } else if (lyricsState != null && lyricsState!!.syncedLines.isNotEmpty()) {
                        // Synced Lyrics List with Active Line Highlighting & Auto Scroll
                        SyncedLyricsView(
                            lines = lyricsState!!.syncedLines,
                            playbackPositionMs = playbackPositionMs,
                            onSeekTo = onSeekTo
                        )
                    } else if (lyricsState != null && !lyricsState!!.plainLyrics.isNullOrBlank()) {
                        // Plain Text Lyrics
                        PlainLyricsView(lyrics = lyricsState!!.plainLyrics!!)
                    } else {
                        // Empty / Instrumental State
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(AppleSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (lyricsState?.isInstrumental == true) "Instrumental Track 🎶" else "No lyrics available for this song",
                                color = AppleLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Enjoy the music vibe on Aurio",
                                color = AppleSecondaryLabel,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center
                            )

                            if (hasError) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppleSurface)
                                        .border(1.dp, AppleSeparator, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (song != null) {
                                                scope.launch {
                                                    isLoading = true
                                                    lyricsState = lyricsRepo.getLyrics(
                                                        title = song.title,
                                                        artist = song.artist,
                                                        durationSeconds = song.durationSeconds
                                                    )
                                                    isLoading = false
                                                }
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = AppleBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Retry",
                                        color = AppleBlue,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsView(
    lines: List<LyricLine>,
    playbackPositionMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    // Find the currently active lyric line based on playback time
    val activeIndex = remember(playbackPositionMs, lines) {
        var found = 0
        for (i in lines.indices) {
            if (playbackPositionMs >= lines[i].timestampMs) {
                found = i
            } else {
                break
            }
        }
        found
    }

    // Auto-scroll to center the active line
    LaunchedEffect(activeIndex) {
        if (lines.isNotEmpty() && activeIndex in lines.indices) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(lines, key = { index, line -> "${line.timestampMs}_$index" }) { index, line ->
            val isActive = index == activeIndex
            val textColor = if (isActive) AppleLabel else AppleGray
            val fontSize = if (isActive) 21.sp else 16.5.sp
            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium

            Text(
                text = line.text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = FontFamily.SansSerif,
                lineHeight = if (isActive) 28.sp else 24.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSeekTo(line.timestampMs) }
                    )
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PlainLyricsView(lyrics: String) {
    val lines = remember(lyrics) { lyrics.lines() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(lines) { _, line ->
            Text(
                text = line.ifBlank { " " },
                color = AppleLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 26.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
