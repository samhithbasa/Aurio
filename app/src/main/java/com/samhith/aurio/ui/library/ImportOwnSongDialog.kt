package com.samhith.aurio.ui.library

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.samhith.aurio.data.download.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.samhith.aurio.ui.theme.AppleLabel
import com.samhith.aurio.ui.theme.AppleGray
import com.samhith.aurio.ui.theme.AppleBlue
import com.samhith.aurio.ui.theme.AppleSurface
import com.samhith.aurio.ui.theme.AppleFill
import com.samhith.aurio.ui.theme.AppleSeparator
import com.samhith.aurio.ui.theme.AppleSecondaryLabel
import com.samhith.aurio.ui.theme.AppleOnAccent
import com.samhith.aurio.ui.theme.ApplePrimaryGradient

/**
 * Dialog for importing user's own local audio files and optional thumbnail artworks.
 * Automatically adds the track into the Downloads collection for full offline playback.
 */
@Composable
fun ImportOwnSongDialog(
    onDismiss: () -> Unit,
    onImportSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { DownloadManager.getInstance().apply { initialize(context) } }

    var songTitle by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioFileName by remember { mutableStateOf("") }
    var selectedThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // Audio picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAudioUri = uri
            // Extract display name from URI
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        audioFileName = name
                        if (songTitle.isBlank()) {
                            songTitle = name.substringBeforeLast(".")
                        }
                    }
                }
            } catch (_: Exception) {
                audioFileName = "Audio Selected"
            }
        }
    }

    // Thumbnail image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedThumbnailUri = uri
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .background(AppleSurface)
                .border(1.2.dp, AppleSeparator, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ApplePrimaryGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = AppleOnAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import Song",
                            color = AppleLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppleSecondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Add audio and cover art to your offline downloads",
                    color = AppleSecondaryLabel,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Song Title Input
                OutlinedTextField(
                    value = songTitle,
                    onValueChange = { songTitle = it },
                    label = { Text("Song Title *", color = AppleSecondaryLabel) },
                    placeholder = { Text("e.g. My Favorite Melody", color = AppleGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppleLabel,
                        unfocusedTextColor = AppleLabel,
                        focusedBorderColor = AppleBlue,
                        unfocusedBorderColor = AppleSeparator,
                        focusedContainerColor = AppleFill,
                        unfocusedContainerColor = AppleFill,
                        cursorColor = AppleBlue
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Artist Name Input
                OutlinedTextField(
                    value = artistName,
                    onValueChange = { artistName = it },
                    label = { Text("Artist Name (Optional)", color = AppleSecondaryLabel) },
                    placeholder = { Text("e.g. Local Artist", color = AppleGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppleLabel,
                        unfocusedTextColor = AppleLabel,
                        focusedBorderColor = AppleBlue,
                        unfocusedBorderColor = AppleSeparator,
                        focusedContainerColor = AppleFill,
                        unfocusedContainerColor = AppleFill,
                        cursorColor = AppleBlue
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Audio File Picker Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedAudioUri != null) AppleBlue.copy(alpha = 0.1f) else AppleFill)
                        .border(
                            1.dp,
                            if (selectedAudioUri != null) AppleBlue else AppleSeparator,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { audioPickerLauncher.launch("audio/*") }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selectedAudioUri != null) Icons.Default.CheckCircle else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = if (selectedAudioUri != null) AppleBlue else AppleSecondaryLabel,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedAudioUri != null) "Audio File Selected" else "Select Audio File *",
                            color = AppleLabel,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = if (audioFileName.isNotBlank()) audioFileName else "Tap to choose .mp3, .wav, .m4a, .aac",
                            color = if (selectedAudioUri != null) AppleBlue else AppleSecondaryLabel,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Thumbnail Image Picker Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleFill)
                        .border(
                            1.dp,
                            if (selectedThumbnailUri != null) AppleBlue.copy(alpha = 0.5f) else AppleSeparator,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { imagePickerLauncher.launch("image/*") }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedThumbnailUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(selectedThumbnailUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover preview",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppleFill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedThumbnailUri != null) "Artwork Selected" else "Select Cover Artwork (Optional)",
                            color = AppleLabel,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = if (selectedThumbnailUri != null) "Tap to change cover image" else "Choose JPG or PNG for album art",
                            color = AppleSecondaryLabel,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Import Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (songTitle.isNotBlank() && selectedAudioUri != null && !isImporting)
                                ApplePrimaryGradient
                            else
                                Brush.horizontalGradient(listOf(AppleFill, AppleFill))
                        )
                        .clickable(
                            enabled = songTitle.isNotBlank() && selectedAudioUri != null && !isImporting
                        ) {
                            if (songTitle.isNotBlank() && selectedAudioUri != null) {
                                isImporting = true
                                scope.launch(Dispatchers.IO) {
                                    val success = downloadManager.addLocalImport(
                                        title = songTitle.trim(),
                                        artist = artistName.trim().ifBlank { "Local Artist" },
                                        audioUri = selectedAudioUri!!,
                                        thumbnailUri = selectedThumbnailUri,
                                        context = context
                                    )
                                    withContext(Dispatchers.Main) {
                                        isImporting = false
                                        if (success) {
                                            Toast.makeText(
                                                context,
                                                "Imported '${songTitle.trim()}' to Downloads! \uD83C\uDFB5",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onImportSuccess()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Failed to import audio file",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            color = AppleOnAccent,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = if (songTitle.isNotBlank() && selectedAudioUri != null) AppleOnAccent else AppleSecondaryLabel,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Import to Downloads",
                                color = if (songTitle.isNotBlank() && selectedAudioUri != null) AppleOnAccent else AppleSecondaryLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    }
}
