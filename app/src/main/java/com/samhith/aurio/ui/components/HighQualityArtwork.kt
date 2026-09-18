package com.samhith.aurio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Rewrites an artwork URL to ask the source for its largest version.
 *
 * Search results hand back small covers - YouTube's 480x360 "hqdefault", Saavn's 150x150 - which
 * look soft on a full-screen player. Every service exposes bigger sizes through the same URL.
 */
fun highResArtworkUrl(url: String): String {
    if (url.isBlank()) return url
    return when {
        // YouTube: hqdefault (480x360) -> maxresdefault (1280x720). Not every video has one,
        // which is why [HighQualityArtwork] keeps the original as a fallback.
        url.contains("ytimg.com") -> Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg")
            .replace(url) { "/maxresdefault.jpg" }

        // Saavn and similar CDNs encode the size in the file name
        url.contains("saavncdn.com") -> url
            .replace("50x50", "500x500")
            .replace("150x150", "500x500")

        // Google's image resizer: ...=w120-h120-l90-rj
        url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
            Regex("=w\\d+-h\\d+").replace(url, "=w720-h720")

        else -> url
    }
}

/**
 * Album art at the best resolution the source has.
 *
 * Tries the large version first and quietly falls back to the original URL when it does not exist
 * (YouTube only generates "maxresdefault" for some videos), so art never ends up blank.
 */
@Composable
fun HighQualityArtwork(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var model by remember(url) { mutableStateOf(highResArtworkUrl(url)) }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = {
            // The larger size does not exist for this track: show the original instead
            if (model != url) model = url
        }
    )
}
