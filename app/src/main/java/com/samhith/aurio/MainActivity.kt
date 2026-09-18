package com.samhith.aurio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

import com.samhith.aurio.data.ai.AssistantMode
import com.samhith.aurio.data.ai.AssistantUiAction
import com.samhith.aurio.data.ai.AurioAssistantService
import com.samhith.aurio.data.ai.AurioSystemOverlayManager
import com.samhith.aurio.data.ai.AurioWakeWordManager
import com.samhith.aurio.data.auth.AuthRepository
import com.samhith.aurio.data.auth.UserAccount
import com.samhith.aurio.data.music.MusicRepository
import com.samhith.aurio.data.music.SearchCategory
import com.samhith.aurio.data.network.NetworkMonitor
import com.samhith.aurio.data.player.AudioPlayerManager
import com.samhith.aurio.ui.ai.FloatingAiBubble
import com.samhith.aurio.ui.ai.SiriWaveAssistantOverlay
import com.samhith.aurio.ui.auth.AuthScreen
import com.samhith.aurio.ui.components.AurioBottomBanner
import com.samhith.aurio.ui.artists.ArtistDetailItem
import com.samhith.aurio.ui.artists.ArtistDetailScreen
import com.samhith.aurio.ui.artists.PopularArtistsSeeAllScreen
import com.samhith.aurio.ui.foryou.ForYouSeeAllScreen
import com.samhith.aurio.ui.home.HomeScreen
import com.samhith.aurio.ui.network.NoNetworkScreen
import com.samhith.aurio.ui.player.FullPlayerScreen
import com.samhith.aurio.ui.popular.PopularSeeAllScreen
import com.samhith.aurio.ui.recent.RecentlyPlayedSeeAllScreen
import com.samhith.aurio.ui.search.SearchSeeAllScreen
import com.samhith.aurio.ui.listentogether.ListenTogetherScreen
import com.samhith.aurio.ui.listentogether.PopularRoomsSeeAllScreen
import com.samhith.aurio.ui.listentogether.RoomScreen
import com.samhith.aurio.data.download.DownloadManager
import com.samhith.aurio.data.library.LibraryRepository
import com.samhith.aurio.data.room.Room
import com.samhith.aurio.data.room.RoomSessionManager
import com.samhith.aurio.ui.components.HomeTab
import com.samhith.aurio.ui.library.DownloadsScreen
import com.samhith.aurio.ui.library.LibraryScreen
import com.samhith.aurio.ui.library.LikedSongsScreen
import com.samhith.aurio.ui.library.PlaylistsSeeAllScreen
import com.samhith.aurio.ui.library.SpotifyImportDialog
import com.samhith.aurio.ui.profile.ProfileScreen
import com.samhith.aurio.ui.splash.SplashScreen
import com.samhith.aurio.ui.theme.AurioTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.samhith.aurio.ui.theme.AppleBackground

enum class AppNavScreen {
    SPLASH,
    AUTH,
    HOME,
    FOR_YOU_SEE_ALL,
    RECENTLY_PLAYED_SEE_ALL,
    POPULAR_SEE_ALL,
    POPULAR_ARTISTS_SEE_ALL,
    ARTIST_DETAIL,
    SEARCH_SEE_ALL,
    LISTEN_TOGETHER,
    ROOM,
    POPULAR_ROOMS_SEE_ALL,
    LIBRARY,
    LIKED_SONGS,
    DOWNLOADS,
    PLAYLISTS_SEE_ALL,
    PROFILE
}

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // Mode 1: app is on screen, so hide the system bubble. The mic stays closed until the
        // user taps the in-app assistant bubble.
        AurioSystemOverlayManager.getInstance(this).setAppInForeground(true)
        val wakeManager = AurioWakeWordManager.getInstance(this)
        wakeManager.setAppInForeground(true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            wakeManager.startContinuousListening()
        }
    }

    override fun onStop() {
        super.onStop()
        // Mode 2: show the floating bubble and RELEASE the microphone completely, so other apps
        // (calls, camera, voice notes) are never disturbed. Nothing listens until the bubble is
        // tapped.
        AurioSystemOverlayManager.getInstance(this).setAppInForeground(false)
        AurioWakeWordManager.getInstance(this).setAppInForeground(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        AurioWakeWordManager.getInstance(this).setAppInForeground(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light Apple theme: always dark status/navigation icons, even when the phone is in dark mode
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            AurioTheme {
                val context = LocalContext.current
                val authRepository = remember { AuthRepository.getInstance(context) }
                val networkMonitor = remember { NetworkMonitor.getInstance(context) }
                val playerManager = remember { AudioPlayerManager.getInstance(context) }
                val musicRepository = remember { MusicRepository.getInstance().apply { initialize(context) } }
                val libraryRepository = remember { LibraryRepository.getInstance().apply { initialize(context) } }
                val downloadManager = remember { DownloadManager.getInstance().apply { initialize(context) } }
                val isOnline by networkMonitor.isOnline.collectAsState()
                val scope = rememberCoroutineScope()

                // AI Assistant: Use singleton WakeWordManager (shared with AurioAssistantService)
                val wakeWordManager = remember { AurioWakeWordManager.getInstance(context) }

                val wakeState by wakeWordManager.state.collectAsState()
                val customAssistantName by wakeWordManager.customAssistantName.collectAsState()
                val trainingState by wakeWordManager.trainingState.collectAsState()
                val isSiriOverlayVisible by wakeWordManager.isOverlayVisible.collectAsState()
                val liveTranscript by wakeWordManager.liveTranscript.collectAsState()
                val responseMessage by wakeWordManager.responseMessage.collectAsState()
                val rmsVolume by wakeWordManager.rmsVolume.collectAsState()

                // Request Notification Permission on Android 13+ (API 33+)
                // Mic and notifications are requested TOGETHER. Android allows only one permission
                // dialog at a time - firing two separate requests meant the second was silently
                // dropped ("Can request only one set of permissions at a time"), which left
                // RECORD_AUDIO ungranted and the whole assistant mute.
                val assistantPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    if (results[Manifest.permission.RECORD_AUDIO] == true) {
                        wakeWordManager.startContinuousListening()
                        AurioAssistantService.start(context)
                    }
                }

                // Overlay Permission Launcher for System-Wide Floating Bubble
                val overlayPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
                        AurioAssistantService.start(context)
                    }
                }

                fun checkOverlayPermission() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        } catch (_: Exception) {}
                    }
                }

                fun checkAudioPermissionAndStart() {
                    val missing = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        missing.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        missing.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    if (missing.isEmpty()) {
                        wakeWordManager.startContinuousListening()
                        AurioAssistantService.start(context)
                    } else {
                        // One dialog for both, so neither request gets dropped
                        assistantPermissionLauncher.launch(missing.toTypedArray())
                    }
                }

                // Load Logo once
                val logoBitmap: ImageBitmap? = remember {
                    try {
                        context.assets.open("Aurio_Logo.png").use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                var currentScreen by remember { mutableStateOf(AppNavScreen.SPLASH) }
                val currentUser by authRepository.currentUserFlow.collectAsState()

                var isFullPlayerVisible by remember { mutableStateOf(false) }
                var selectedArtist by remember { mutableStateOf<ArtistDetailItem?>(null) }
                var previousArtistScreen by remember { mutableStateOf(AppNavScreen.POPULAR_ARTISTS_SEE_ALL) }
                var activeSearchQuery by remember { mutableStateOf("") }
                var activeSearchCategory by remember { mutableStateOf(SearchCategory.ALL) }
                var activeRoom by remember { mutableStateOf<Room?>(null) }
                var showSpotifyImportDialog by remember { mutableStateOf(false) }
                // Player panel the assistant asked for by voice, consumed by FullPlayerScreen
                var assistantPanelRequest by remember { mutableStateOf<AssistantUiAction?>(null) }

                // Floating Bottom Banner notification state
                var bannerMessage by remember { mutableStateOf("") }
                var isBannerVisible by remember { mutableStateOf(false) }
                var bannerJob by remember { mutableStateOf<Job?>(null) }

                fun showBanner(message: String) {
                    bannerJob?.cancel()
                    bannerMessage = message
                    isBannerVisible = true
                    bannerJob = scope.launch {
                        delay(3500)
                        isBannerVisible = false
                    }
                }

                LaunchedEffect(wakeWordManager) {
                    wakeWordManager.onExecutionResult = { result ->
                        if (result.bannerMessage != null) {
                            showBanner(result.bannerMessage)
                        }
                        if (result.searchQuery != null) {
                            activeSearchQuery = result.searchQuery
                            activeSearchCategory = SearchCategory.ALL
                        }
                        if (result.targetScreen != null) {
                            currentScreen = result.targetScreen
                        }
                        // Voice-driven UI: "show lyrics", "open the queue", "equalizer", …
                        when (result.uiAction) {
                            AssistantUiAction.OPEN_FULL_PLAYER -> isFullPlayerVisible = true
                            AssistantUiAction.CLOSE_FULL_PLAYER -> isFullPlayerVisible = false
                            AssistantUiAction.OPEN_SPOTIFY_IMPORT -> showSpotifyImportDialog = true
                            AssistantUiAction.OPEN_LYRICS,
                            AssistantUiAction.OPEN_QUEUE,
                            AssistantUiAction.OPEN_EQUALIZER,
                            AssistantUiAction.OPEN_HAPTICS,
                            AssistantUiAction.OPEN_SLEEP_TIMER -> {
                                // These panels live inside the full player, so open it first.
                                isFullPlayerVisible = true
                                assistantPanelRequest = result.uiAction
                            }

                            null -> Unit
                        }
                    }
                }

                // Start continuous hands-free "Hey Aurio" listening when user is logged in
                LaunchedEffect(currentUser) {
                    if (currentUser != null) {
                        checkAudioPermissionAndStart()
                        checkOverlayPermission()
                    } else {
                        wakeWordManager.stopContinuousListening()
                        AurioAssistantService.stop(context)
                    }
                }

                // Singleton lifecycle: Don't destroy the shared WakeWordManager on recomposition.
                // It persists across the app lifecycle and is managed by AurioAssistantService.

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppleBackground
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val isOfflineAllowed = currentScreen == AppNavScreen.SPLASH ||
                                currentScreen == AppNavScreen.LIBRARY ||
                                currentScreen == AppNavScreen.DOWNLOADS ||
                                currentScreen == AppNavScreen.LIKED_SONGS ||
                                currentScreen == AppNavScreen.PLAYLISTS_SEE_ALL ||
                                currentScreen == AppNavScreen.PROFILE

                        AnimatedContent(
                            targetState = if (!isOnline && !isOfflineAllowed) null else currentScreen,
                            transitionSpec = {
                                fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                            },
                            label = "screen_navigation"
                        ) { screen ->
                            if (screen == null) {
                                // No Network Offline Screen
                                NoNetworkScreen(
                                    logoBitmap = logoBitmap,
                                    onRetryClick = {
                                        val connected = networkMonitor.checkIsOnline()
                                        if (!connected) {
                                            showBanner("Still offline. Please check your connection ⚠️")
                                        } else {
                                            showBanner("Back online! 🎵")
                                        }
                                    },
                                    onGoToLibraryClick = {
                                        currentScreen = AppNavScreen.LIBRARY
                                    }
                                )
                            } else {
                                when (screen) {
                                    AppNavScreen.SPLASH -> {
                                        SplashScreen(
                                            logoBitmap = logoBitmap,
                                            onSplashFinished = {
                                                currentScreen = if (currentUser != null) {
                                                    AppNavScreen.HOME
                                                } else {
                                                    AppNavScreen.AUTH
                                                }
                                            }
                                        )
                                    }
                                    AppNavScreen.AUTH -> {
                                        AuthScreen(
                                            logoBitmap = logoBitmap,
                                            onLoginSuccess = { _ ->
                                                showBanner("Welcome to Aurio 🎵")
                                                currentScreen = AppNavScreen.HOME
                                            },
                                            onGoogleLoginSuccess = { _ ->
                                                showBanner("Welcome to Aurio 🎵")
                                                currentScreen = AppNavScreen.HOME
                                            },
                                            onRegistrationSuccess = {
                                                showBanner("Registration successful! Welcome to Aurio 🎵")
                                            }
                                        )
                                    }
                                    AppNavScreen.HOME -> {
                                        HomeScreen(
                                            user = currentUser,
                                            logoBitmap = logoBitmap,
                                            onSignOutClick = {
                                                RoomSessionManager.getInstance().leaveRoom()
                                                activeRoom = null
                                                authRepository.logout()
                                                currentScreen = AppNavScreen.AUTH
                                            },
                                            onSeeAllClick = { section ->
                                                when {
                                                    section.startsWith("search") -> {
                                                        val payload = section.removePrefix("search:").trim()
                                                        if (payload.contains(":")) {
                                                            val parts = payload.split(":", limit = 2)
                                                            activeSearchCategory = try { SearchCategory.valueOf(parts[0]) } catch (_: Exception) { SearchCategory.ALL }
                                                            activeSearchQuery = parts[1]
                                                        } else {
                                                            activeSearchCategory = SearchCategory.ALL
                                                            activeSearchQuery = payload
                                                        }
                                                        currentScreen = AppNavScreen.SEARCH_SEE_ALL
                                                    }
                                                    section == "for_you" -> currentScreen = AppNavScreen.FOR_YOU_SEE_ALL
                                                    section == "recently_played" -> currentScreen = AppNavScreen.RECENTLY_PLAYED_SEE_ALL
                                                    section == "popular" -> currentScreen = AppNavScreen.POPULAR_SEE_ALL
                                                    section == "artists" -> currentScreen = AppNavScreen.POPULAR_ARTISTS_SEE_ALL
                                                    section == "liked_songs" -> currentScreen = AppNavScreen.LIKED_SONGS
                                                }
                                            },
                                            onArtistClick = { artistProfile ->
                                                selectedArtist = ArtistDetailItem(
                                                    name = artistProfile.name,
                                                    imageUrl = artistProfile.imageUrl,
                                                    genre = "Popular Artist",
                                                    monthlyListeners = "60M+"
                                                )
                                                previousArtistScreen = AppNavScreen.HOME
                                                currentScreen = AppNavScreen.ARTIST_DETAIL
                                            },
                                            onOpenFullPlayer = {
                                                isFullPlayerVisible = true
                                            },
                                            onListenTogetherClick = {
                                                val liveRoom = RoomSessionManager.getInstance().activeRoom.value
                                                if (liveRoom != null) {
                                                    activeRoom = liveRoom
                                                    currentScreen = AppNavScreen.ROOM
                                                } else {
                                                    currentScreen = AppNavScreen.LISTEN_TOGETHER
                                                }
                                            },
                                            onLibraryClick = {
                                                currentScreen = AppNavScreen.LIBRARY
                                            },
                                            onProfileClick = {
                                                currentScreen = AppNavScreen.PROFILE
                                            }
                                        )
                                    }
                                    AppNavScreen.FOR_YOU_SEE_ALL -> {
                                        ForYouSeeAllScreen(
                                            onBackClick = {
                                                currentScreen = AppNavScreen.HOME
                                            },
                                            onLikedSongsClick = {
                                                currentScreen = AppNavScreen.LIKED_SONGS
                                            },
                                            onOpenFullPlayer = {
                                                isFullPlayerVisible = true
                                            }
                                        )
                                    }
                                    AppNavScreen.RECENTLY_PLAYED_SEE_ALL -> {
                                        RecentlyPlayedSeeAllScreen(
                                            onBackClick = {
                                                currentScreen = AppNavScreen.HOME
                                            },
                                            onOpenFullPlayer = {
                                                isFullPlayerVisible = true
                                            }
                                        )
                                    }
                                    AppNavScreen.POPULAR_SEE_ALL -> {
                                        PopularSeeAllScreen(
                                            onBackClick = {
                                                currentScreen = AppNavScreen.HOME
                                            },
                                            onOpenFullPlayer = {
                                                isFullPlayerVisible = true
                                            }
                                        )
                                    }
                                    AppNavScreen.POPULAR_ARTISTS_SEE_ALL -> {
                                        PopularArtistsSeeAllScreen(
                                            onBackClick = {
                                                currentScreen = AppNavScreen.HOME
                                            },
                                            onArtistClick = { artist ->
                                                selectedArtist = artist
                                                previousArtistScreen = AppNavScreen.POPULAR_ARTISTS_SEE_ALL
                                                currentScreen = AppNavScreen.ARTIST_DETAIL
                                            },
                                            onOpenFullPlayer = {
                                                isFullPlayerVisible = true
                                            }
                                        )
                                    }
                                    AppNavScreen.ARTIST_DETAIL -> {
                                        selectedArtist?.let { artist ->
                                            ArtistDetailScreen(
                                                artist = artist,
                                                onBackClick = {
                                                    currentScreen = previousArtistScreen
                                                },
                                                onOpenFullPlayer = {
                                                    isFullPlayerVisible = true
                                                }
                                            )
                                        } ?: run {
                                            currentScreen = AppNavScreen.HOME
                                        }
                                    }
                                    AppNavScreen.SEARCH_SEE_ALL -> {
                                        SearchSeeAllScreen(
                                            initialQuery = activeSearchQuery,
                                            initialCategory = activeSearchCategory,
                                            onBackClick = {
                                                currentScreen = AppNavScreen.HOME
                                            },
                                            onArtistClick = { artist ->
                                                selectedArtist = artist
                                                previousArtistScreen = AppNavScreen.SEARCH_SEE_ALL
                                                currentScreen = AppNavScreen.ARTIST_DETAIL
                                            },
                                            onOpenFullPlayer = {
                                                isFullPlayerVisible = true
                                            }
                                        )
                                    }
                                    AppNavScreen.LISTEN_TOGETHER -> {
                                        ListenTogetherScreen(
                                            onBackClick = { currentScreen = AppNavScreen.HOME },
                                            onRoomClick = { room ->
                                                activeRoom = room
                                                currentScreen = AppNavScreen.ROOM
                                            },
                                            onCreateRoom = { room ->
                                                activeRoom = room
                                                currentScreen = AppNavScreen.ROOM
                                            },
                                            onSeeAllPopularRooms = {
                                                currentScreen = AppNavScreen.POPULAR_ROOMS_SEE_ALL
                                            },
                                            onHomeClick = { currentScreen = AppNavScreen.HOME },
                                            onLibraryClick = { currentScreen = AppNavScreen.LIBRARY },
                                            onProfileClick = { currentScreen = AppNavScreen.PROFILE },
                                            onOpenFullPlayer = { isFullPlayerVisible = true }
                                        )
                                    }
                                    AppNavScreen.ROOM -> {
                                        val liveSessionRoom by RoomSessionManager.getInstance().activeRoom.collectAsState()
                                        val displayRoom = liveSessionRoom ?: activeRoom
                                        if (displayRoom != null) {
                                            RoomScreen(
                                                room = displayRoom,
                                                onBackClick = { currentScreen = AppNavScreen.LISTEN_TOGETHER },
                                                onHomeClick = { currentScreen = AppNavScreen.HOME },
                                                onLibraryClick = { currentScreen = AppNavScreen.LIBRARY },
                                                onProfileClick = { currentScreen = AppNavScreen.PROFILE },
                                                onOpenFullPlayer = { isFullPlayerVisible = true }
                                            )
                                        } else {
                                            currentScreen = AppNavScreen.LISTEN_TOGETHER
                                        }
                                    }
                                    AppNavScreen.POPULAR_ROOMS_SEE_ALL -> {
                                        PopularRoomsSeeAllScreen(
                                            onBackClick = { currentScreen = AppNavScreen.LISTEN_TOGETHER },
                                            onRoomClick = { room ->
                                                activeRoom = room
                                                currentScreen = AppNavScreen.ROOM
                                            },
                                            onHomeClick = { currentScreen = AppNavScreen.HOME },
                                            onLibraryClick = { currentScreen = AppNavScreen.LIBRARY },
                                            onOpenFullPlayer = { isFullPlayerVisible = true }
                                        )
                                    }
                                    AppNavScreen.LIBRARY -> {
                                        LibraryScreen(
                                            onLikedSongsClick = { currentScreen = AppNavScreen.LIKED_SONGS },
                                            onDownloadsClick = { currentScreen = AppNavScreen.DOWNLOADS },
                                            onPlaylistsSeeAllClick = { currentScreen = AppNavScreen.PLAYLISTS_SEE_ALL },
                                            onHomeClick = { currentScreen = AppNavScreen.HOME },
                                            onListenTogetherClick = {
                                                val liveRoom = RoomSessionManager.getInstance().activeRoom.value
                                                if (liveRoom != null) {
                                                    activeRoom = liveRoom
                                                    currentScreen = AppNavScreen.ROOM
                                                } else {
                                                    currentScreen = AppNavScreen.LISTEN_TOGETHER
                                                }
                                            },
                                            onProfileClick = { currentScreen = AppNavScreen.PROFILE },
                                            onOpenFullPlayer = { isFullPlayerVisible = true },
                                            onArtistClick = { artistProfile ->
                                                selectedArtist = ArtistDetailItem(
                                                    name = artistProfile.name,
                                                    imageUrl = artistProfile.imageUrl,
                                                    genre = "Followed Artist",
                                                    monthlyListeners = "Popular Artist"
                                                )
                                                previousArtistScreen = AppNavScreen.LIBRARY
                                                currentScreen = AppNavScreen.ARTIST_DETAIL
                                            }
                                        )
                                    }
                                    AppNavScreen.LIKED_SONGS -> {
                                        LikedSongsScreen(
                                            onBackClick = { currentScreen = AppNavScreen.LIBRARY },
                                            onHomeClick = { currentScreen = AppNavScreen.HOME },
                                            onListenTogetherClick = {
                                                val liveRoom = RoomSessionManager.getInstance().activeRoom.value
                                                if (liveRoom != null) {
                                                    activeRoom = liveRoom
                                                    currentScreen = AppNavScreen.ROOM
                                                } else {
                                                    currentScreen = AppNavScreen.LISTEN_TOGETHER
                                                }
                                            },
                                            onLibraryClick = { currentScreen = AppNavScreen.LIBRARY },
                                            onProfileClick = { currentScreen = AppNavScreen.PROFILE },
                                            onOpenFullPlayer = { isFullPlayerVisible = true }
                                        )
                                    }
                                    AppNavScreen.DOWNLOADS -> {
                                        DownloadsScreen(
                                            onBackClick = { currentScreen = AppNavScreen.LIBRARY },
                                            onHomeClick = { currentScreen = AppNavScreen.HOME },
                                            onListenTogetherClick = {
                                                val liveRoom = RoomSessionManager.getInstance().activeRoom.value
                                                if (liveRoom != null) {
                                                    activeRoom = liveRoom
                                                    currentScreen = AppNavScreen.ROOM
                                                } else {
                                                    currentScreen = AppNavScreen.LISTEN_TOGETHER
                                                }
                                            },
                                            onLibraryClick = { currentScreen = AppNavScreen.LIBRARY },
                                            onProfileClick = { currentScreen = AppNavScreen.PROFILE },
                                            onOpenFullPlayer = { isFullPlayerVisible = true }
                                        )
                                    }
                                    AppNavScreen.PLAYLISTS_SEE_ALL -> {
                                        PlaylistsSeeAllScreen(
                                            libraryRepository = libraryRepository,
                                            playerManager = playerManager,
                                            onBack = { currentScreen = AppNavScreen.LIBRARY },
                                            onNavigateTab = { tab ->
                                                when (tab) {
                                                    HomeTab.HOME -> currentScreen = AppNavScreen.HOME
                                                    HomeTab.LIBRARY -> currentScreen = AppNavScreen.LIBRARY
                                                    HomeTab.PROFILE -> currentScreen = AppNavScreen.PROFILE
                                                    HomeTab.LISTEN_TOGETHER -> {
                                                        val liveRoom = RoomSessionManager.getInstance().activeRoom.value
                                                        if (liveRoom != null) {
                                                            activeRoom = liveRoom
                                                            currentScreen = AppNavScreen.ROOM
                                                        } else {
                                                            currentScreen = AppNavScreen.LISTEN_TOGETHER
                                                        }
                                                    }
                                                }
                                            },
                                            onOpenFullPlayer = { isFullPlayerVisible = true },
                                            onOpenSpotifyImport = { showSpotifyImportDialog = true }
                                        )
                                    }
                                    AppNavScreen.PROFILE -> {
                                        ProfileScreen(
                                            user = currentUser,
                                            onUserUpdated = { /* Handled reactively via currentUserFlow */ },
                                            onLikedSongsClick = { currentScreen = AppNavScreen.LIKED_SONGS },
                                            onPlaylistsClick = { currentScreen = AppNavScreen.PLAYLISTS_SEE_ALL },
                                            onFollowingArtistsClick = { currentScreen = AppNavScreen.POPULAR_ARTISTS_SEE_ALL },
                                            onDownloadsClick = { currentScreen = AppNavScreen.DOWNLOADS },
                                            onHomeClick = { currentScreen = AppNavScreen.HOME },
                                            onListenTogetherClick = {
                                                val liveRoom = RoomSessionManager.getInstance().activeRoom.value
                                                if (liveRoom != null) {
                                                    activeRoom = liveRoom
                                                    currentScreen = AppNavScreen.ROOM
                                                } else {
                                                    currentScreen = AppNavScreen.LISTEN_TOGETHER
                                                }
                                            },
                                            onLibraryClick = { currentScreen = AppNavScreen.LIBRARY },
                                            onOpenFullPlayer = { isFullPlayerVisible = true },
                                            onSignOutClick = {
                                                RoomSessionManager.getInstance().leaveRoom()
                                                activeRoom = null
                                                authRepository.logout()
                                                currentScreen = AppNavScreen.AUTH
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Spotify Import Global Dialog
                        if (showSpotifyImportDialog) {
                            SpotifyImportDialog(
                                onDismiss = { showSpotifyImportDialog = false },
                                onImport = { name, url ->
                                    showSpotifyImportDialog = false
                                    libraryRepository.importSpotifyPlaylist(name, url) { success ->
                                        if (success) {
                                            showBanner("Spotify playlist imported to Library! 🎵")
                                        } else {
                                            showBanner("Could not import playlist. Please try again.")
                                        }
                                    }
                                }
                            )
                        }

                        // Full Screen Music Player Overlay (Accessible from ANY screen on MiniPlayer click)
                        AnimatedVisibility(
                            visible = isFullPlayerVisible,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(350)
                            ) + fadeIn(tween(250)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(tween(200))
                        ) {
                            FullPlayerScreen(
                                playerManager = playerManager,
                                onCollapse = { isFullPlayerVisible = false },
                                assistantPanel = assistantPanelRequest,
                                onAssistantPanelHandled = { assistantPanelRequest = null }
                            )
                        }

                        // Floating Draggable AI Assistant Bubble (Visible across every screen when logged in)
                        val isUserLoggedIn = currentUser != null && currentScreen != AppNavScreen.SPLASH && currentScreen != AppNavScreen.AUTH
                        if (isUserLoggedIn) {
                            FloatingAiBubble(
                                wakeState = wakeState,
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        wakeWordManager.triggerManualAwake(AssistantMode.IN_APP)
                                    } else {
                                        assistantPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    }
                                }
                            )
                        }

                        // Apple Siri-Style Bottom Floating Sound Wave Overlay
                        SiriWaveAssistantOverlay(
                            isVisible = isSiriOverlayVisible,
                            state = wakeState,
                            assistantName = customAssistantName,
                            trainingState = trainingState,
                            liveTranscript = liveTranscript,
                            responseMessage = responseMessage,
                            rmsVolume = rmsVolume,
                            onDismiss = { wakeWordManager.dismissOverlay() },
                            onChipClick = { query -> wakeWordManager.submitTextCommand(query) },
                            onConfirmCustomName = { name -> wakeWordManager.submitCustomNameFromUi(name) },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        // Floating Bottom Banner with Aurio Logo and confirmation text
                        AurioBottomBanner(
                            message = bannerMessage,
                            isVisible = isBannerVisible,
                            logoBitmap = logoBitmap,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}