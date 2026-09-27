package com.samhith.aurio.data.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.samhith.aurio.MainActivity
import com.samhith.aurio.data.music.SongItem
import com.samhith.aurio.ui.components.highResArtworkUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * System-Wide Dynamic Island Overlay Manager.
 *
 * Rules:
 * 1. MUST NOT appear when the Aurio app is in the foreground (tracked via ActivityLifecycleCallbacks).
 * 2. MUST ONLY appear when the Aurio app is in the background AND a song is active/playing.
 * 3. Collapsed View: Stadium black pill centered around the front camera punch hole cutout,
 *    featuring the song's album art thumbnail, elapsed time, and an animated audio waveform.
 * 4. Tap: Opens Aurio app directly into FullPlayerScreen.
 * 5. Long-Press: Expands into the full Dynamic Island Mini Player card matching the reference design.
 * 6. Swipe-Up / Close: Collapses back to the compact camera pill.
 */
class AurioDynamicIslandOverlayManager private constructor(private val context: Context) {

    private val TAG = "AurioDynamicIsland"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Defaults to true so overlay NEVER shows on initial app launch inside the app
    private var isAppInForeground = true
    private var isOverlayAttached = false
    private var isExpanded = false
    private var startedActivityCount = 0

    private var rootView: FrameLayout? = null
    private var collapsedLayout: LinearLayout? = null
    private var expandedLayout: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    // Collapsed View References
    private var collapsedArtView: ImageView? = null
    private var collapsedElapsedView: TextView? = null
    private var collapsedWaveform: WaveformVisualizerView? = null

    // Expanded View References
    private var expandedArtView: ImageView? = null
    private var expandedTitleView: TextView? = null
    private var expandedArtistView: TextView? = null
    private var expandedWaveform: WaveformVisualizerView? = null
    private var expandedElapsedTimeView: TextView? = null
    private var expandedRemainingTimeView: TextView? = null
    private var expandedSeekBar: SeekBar? = null
    private var expandedLikeView: ImageView? = null
    private var expandedPrevView: ImageView? = null
    private var expandedPlayPauseView: ImageView? = null
    private var expandedNextView: ImageView? = null
    private var expandedAirplayView: ImageView? = null

    private var currentLoadedThumbnailUrl: String = ""
    private var cachedArtworkBitmap: Bitmap? = null
    private var isUserSeeking = false

    private val density = context.resources.displayMetrics.density

    // Design Tokens (Solid pitch black Apple dynamic island)
    private val BG_COLOR = Color.BLACK
    private val BORDER_COLOR = Color.parseColor("#1F1F24")
    private val TEXT_PRIMARY = Color.WHITE
    private val TEXT_SECONDARY = Color.parseColor("#8E8E93")
    private val SILVER_ACCENT = Color.parseColor("#E0E0E0")

    companion object {
        @Volatile
        private var instance: AurioDynamicIslandOverlayManager? = null

        fun getInstance(context: Context): AurioDynamicIslandOverlayManager {
            return instance ?: synchronized(this) {
                instance ?: AurioDynamicIslandOverlayManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        registerLifecycleTracker()
        observePlayerState()
    }

    private fun registerLifecycleTracker() {
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                setAppInForeground(true)
            }

            override fun onActivityResumed(activity: Activity) {
                setAppInForeground(true)
            }

            override fun onActivityPaused(activity: Activity) {
                // Do NOT mark app as background on temporary pause (e.g. permission popup, pull-down shade)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount <= 0) {
                    startedActivityCount = 0
                    setAppInForeground(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                setAppInForeground(true)
            }

            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    private fun observePlayerState() {
        val playerManager = AudioPlayerManager.getInstance(context)

        scope.launch {
            playerManager.currentSong.collectLatest { song ->
                mainHandler.post {
                    if (song == null) {
                        hideOverlay()
                    } else {
                        updateSongInfo(song)
                        if (!isAppInForeground && hasOverlayPermission()) {
                            showOverlay()
                        }
                    }
                }
            }
        }

        scope.launch {
            playerManager.isPlaying.collectLatest { isPlaying ->
                mainHandler.post {
                    updatePlaybackState(isPlaying)
                    if (isPlaying && !isAppInForeground && hasOverlayPermission()) {
                        showOverlay()
                    }
                }
            }
        }

        scope.launch {
            playerManager.playbackPositionMs.collectLatest { posMs ->
                mainHandler.post {
                    val durMs = playerManager.durationMs.value
                    updateProgress(posMs, durMs)
                }
            }
        }

        scope.launch {
            playerManager.likedSongIds.collectLatest { likedIds ->
                mainHandler.post {
                    val currentId = playerManager.currentSong.value?.id
                    val isLiked = currentId != null && likedIds.contains(currentId)
                    updateLikeState(isLiked)
                }
            }
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        Log.d(TAG, "setAppInForeground: $inForeground (isOverlayAttached=$isOverlayAttached)")
        if (inForeground) {
            hideOverlay()
        } else {
            val playerManager = AudioPlayerManager.getInstance(context)
            if (playerManager.currentSong.value != null && hasOverlayPermission()) {
                showOverlay()
            }
        }
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOverlayInternal()
        } else {
            mainHandler.post { showOverlayInternal() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlayInternal() {
        if (isAppInForeground || !hasOverlayPermission() || windowManager == null || isOverlayAttached) return

        val playerManager = AudioPlayerManager.getInstance(context)
        val currentSong = playerManager.currentSong.value ?: return

        try {
            if (isAppInForeground || isOverlayAttached) return

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val statusBarHeight = getStatusBarHeight(context)
            val collapsedWidth = (205 * density).toInt()
            val collapsedHeight = (36 * density).toInt()
            val topY = ((statusBarHeight - collapsedHeight) / 2).coerceAtLeast((4 * density).toInt())

            val params = WindowManager.LayoutParams(
                if (isExpanded) getExpandedWidth() else collapsedWidth,
                if (isExpanded) WindowManager.LayoutParams.WRAP_CONTENT else collapsedHeight,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = topY
            }

            windowParams = params

            val root = FrameLayout(context).apply {
                clipToPadding = false
                clipChildren = false
            }
            rootView = root

            // Build Collapsed and Expanded Views
            val collapsed = buildCollapsedView()
            val expanded = buildExpandedView()

            collapsedLayout = collapsed
            expandedLayout = expanded

            root.addView(collapsed)
            root.addView(expanded)

            expanded.visibility = if (isExpanded) View.VISIBLE else View.GONE
            collapsed.visibility = if (isExpanded) View.GONE else View.VISIBLE

            setupTouchGestures(root)

            windowManager?.addView(root, params)
            isOverlayAttached = true
            Log.d(TAG, "Dynamic Island overlay attached around camera cutout")

            // Update contents
            cachedArtworkBitmap?.let { bmp ->
                collapsedArtView?.setImageBitmap(getCircularBitmap(bmp))
                expandedArtView?.setImageBitmap(getRoundedBitmap(bmp, (12 * density).toInt()))
            }
            updateSongInfo(currentSong)
            updatePlaybackState(playerManager.isPlaying.value)
            updateProgress(playerManager.playbackPositionMs.value, playerManager.durationMs.value)
            updateLikeState(playerManager.likedSongIds.value.contains(currentSong.id))

        } catch (e: Exception) {
            Log.e(TAG, "Error showing dynamic island: ${e.message}")
        }
    }

    fun hideOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideOverlayInternal()
        } else {
            mainHandler.post { hideOverlayInternal() }
        }
    }

    private fun hideOverlayInternal() {
        try {
            if (isOverlayAttached && rootView != null) {
                collapsedWaveform?.stop()
                expandedWaveform?.stop()
                windowManager?.removeView(rootView)
                isOverlayAttached = false
                rootView = null
                collapsedLayout = null
                expandedLayout = null
                collapsedWaveform = null
                expandedWaveform = null
                isExpanded = false
                Log.d(TAG, "Dynamic Island removed from WindowManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding dynamic island: ${e.message}")
        }
    }

    private fun getExpandedWidth(): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val marginHorizontalPx = (12 * density).toInt()
        return screenWidth - (2 * marginHorizontalPx)
    }

    private fun setExpandedState(expanded: Boolean) {
        if (isExpanded == expanded) return
        isExpanded = expanded

        mainHandler.post {
            if (!isOverlayAttached) return@post
            collapsedLayout?.visibility = if (expanded) View.GONE else View.VISIBLE
            expandedLayout?.visibility = if (expanded) View.VISIBLE else View.GONE

            val statusBarHeight = getStatusBarHeight(context)
            val collapsedHeight = (36 * density).toInt()
            val topY = ((statusBarHeight - collapsedHeight) / 2).coerceAtLeast((4 * density).toInt())

            windowParams?.let { params ->
                params.width = if (expanded) getExpandedWidth() else (205 * density).toInt()
                params.height = if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else collapsedHeight
                params.y = topY
                try {
                    windowManager?.updateViewLayout(rootView, params)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating dynamic island layout size: ${e.message}")
                }
            }

            val player = AudioPlayerManager.getInstance(context)
            if (player.isPlaying.value) {
                if (expanded) expandedWaveform?.start() else collapsedWaveform?.start()
            }
        }
    }

    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35)
                }
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures(view: View) {
        var downX = 0f
        var downY = 0f
        var isLongPressTriggered = false
        val longPressRunnable = Runnable {
            isLongPressTriggered = true
            performHapticFeedback()
            setExpandedState(true)
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    isLongPressTriggered = false
                    if (!isExpanded) {
                        mainHandler.postDelayed(longPressRunnable, 380)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    if (abs(dx) > 15 || abs(dy) > 15) {
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isExpanded && dy < -35) {
                        // Swiped up on expanded view -> collapse back to pill
                        setExpandedState(false)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    if (!isLongPressTriggered && abs(dx) < 20 && abs(dy) < 20) {
                        if (!isExpanded) {
                            openApp()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }

                else -> false
            }
        }
    }

    private fun openApp() {
        try {
            setAppInForeground(true)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_FULL_PLAYER", true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app from dynamic island: ${e.message}")
        }
    }

    // =========================================================================================
    // COLLAPSED COMPACT PILL VIEW (Around Camera Cutout)
    // =========================================================================================
    private fun buildCollapsedView(): LinearLayout {
        val padH = (6 * density).toInt()
        val cornerRadius = 18 * density

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, 0, padH, 0)
            layoutParams = FrameLayout.LayoutParams(
                (205 * density).toInt(),
                (36 * density).toInt()
            )

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setColor(BG_COLOR)
                setStroke((1 * density).toInt(), BORDER_COLOR)
            }

            // 1. Mini Circular Thumbnail on Left
            val art = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (24 * density).toInt(),
                    (24 * density).toInt()
                ).apply {
                    marginStart = (2 * density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(
                    cachedArtworkBitmap?.let { getCircularBitmap(it) }
                        ?: drawCircularFallbackBitmap((24 * density).toInt())
                )
            }
            collapsedArtView = art
            addView(art)

            // 2. Elapsed Timer (e.g. 0:08)
            val elapsed = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (5 * density).toInt()
                }
                setTextColor(Color.parseColor("#4CAF50"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                text = "0:00"
            }
            collapsedElapsedView = elapsed
            addView(elapsed)

            // 3. Center Camera Notch Spacer
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, (1 * density).toInt(), 1f)
            }
            addView(spacer)

            // 4. Waveform Audio Equalizer on Right
            val waveform = WaveformVisualizerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (22 * density).toInt(),
                    (16 * density).toInt()
                ).apply {
                    marginEnd = (4 * density).toInt()
                }
            }
            collapsedWaveform = waveform
            addView(waveform)
        }
    }

    // =========================================================================================
    // EXPANDED FULL MINI PLAYER CARD (Matching Reference Image)
    // =========================================================================================
    private fun buildExpandedView(): LinearLayout {
        val padH = (14 * density).toInt()
        val padV = (14 * density).toInt()
        val cornerRadius = 32 * density

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setColor(BG_COLOR)
                setStroke((1 * density).toInt(), BORDER_COLOR)
            }

            // 1. TOP ROW: Artwork + [Title + Artist] + Waveform + Collapse
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val art = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (50 * density).toInt(),
                    (50 * density).toInt()
                ).apply {
                    marginEnd = (12 * density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(
                    cachedArtworkBitmap?.let { getRoundedBitmap(it, (12 * density).toInt()) }
                        ?: drawFallbackArtworkBitmap((50 * density).toInt(), (12 * density).toInt())
                )
                setOnClickListener { openApp() }
            }
            expandedArtView = art
            topRow.addView(art)

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { openApp() }
            }

            val titleView = TextView(context).apply {
                setTextColor(TEXT_PRIMARY)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                text = "Aurio Music"
            }
            expandedTitleView = titleView

            val artistView = TextView(context).apply {
                setTextColor(TEXT_SECONDARY)
                textSize = 11.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                text = "Feel Every Beat"
            }
            expandedArtistView = artistView

            textCol.addView(titleView)
            textCol.addView(artistView)
            topRow.addView(textCol)

            // Right: Animated Waveform
            val waveform = WaveformVisualizerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (28 * density).toInt(),
                    (18 * density).toInt()
                ).apply {
                    marginEnd = (6 * density).toInt()
                }
            }
            expandedWaveform = waveform
            topRow.addView(waveform)

            // Collapse Button
            val collapseBtn = TextView(context).apply {
                text = "✕"
                setTextColor(TEXT_SECONDARY)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding((4 * density).toInt(), (2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt())
                setOnClickListener { setExpandedState(false) }
            }
            topRow.addView(collapseBtn)
            addView(topRow)

            // Spacer
            val space1 = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (10 * density).toInt()
                )
            }
            addView(space1)

            // 2. MIDDLE ROW: Elapsed + Seekbar + Remaining
            val scrubberRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val elapsed = TextView(context).apply {
                text = "0:00"
                setTextColor(TEXT_SECONDARY)
                textSize = 10.5f
            }
            expandedElapsedTimeView = elapsed
            scrubberRow.addView(elapsed)

            val seekBar = SeekBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (6 * density).toInt()
                    marginEnd = (6 * density).toInt()
                }
                max = 1000
                progress = 0
                progressDrawable.colorFilter = android.graphics.PorterDuffColorFilter(SILVER_ACCENT, PorterDuff.Mode.SRC_IN)
                thumb.colorFilter = android.graphics.PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {}

                    override fun onStartTrackingTouch(sb: SeekBar?) {
                        isUserSeeking = true
                    }

                    override fun onStopTrackingTouch(sb: SeekBar?) {
                        isUserSeeking = false
                        val prog = sb?.progress ?: 0
                        val dur = AudioPlayerManager.getInstance(context).durationMs.value
                        if (dur > 0) {
                            val targetMs = (dur * (prog / 1000f)).toLong()
                            AudioPlayerManager.getInstance(context).seekTo(targetMs)
                        }
                    }
                })
            }
            expandedSeekBar = seekBar
            scrubberRow.addView(seekBar)

            val remaining = TextView(context).apply {
                text = "-0:00"
                setTextColor(TEXT_SECONDARY)
                textSize = 10.5f
            }
            expandedRemainingTimeView = remaining
            scrubberRow.addView(remaining)

            addView(scrubberRow)

            // Spacer
            val space2 = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (8 * density).toInt()
                )
            }
            addView(space2)

            // 3. BOTTOM ROW: Like + [Prev, Play/Pause, Next] + Airplay/Output
            val controlsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (44 * density).toInt()
                )
            }

            // Favorite/Like Star
            val likeBtn = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (34 * density).toInt(),
                    (34 * density).toInt()
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageBitmap(drawStarBitmap(false))
                setOnClickListener {
                    val player = AudioPlayerManager.getInstance(context)
                    player.currentSong.value?.let { s -> player.toggleLikeSong(s) }
                }
            }
            expandedLikeView = likeBtn
            controlsRow.addView(likeBtn)

            // Center Group: Prev, Play/Pause, Next
            val centerGroup = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }

            val prevBtn = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                    marginEnd = (14 * density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageBitmap(drawPrevBitmap())
                setOnClickListener { AudioPlayerManager.getInstance(context).playPrevious() }
            }
            expandedPrevView = prevBtn
            centerGroup.addView(prevBtn)

            val playPauseBtn = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((46 * density).toInt(), (46 * density).toInt())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageBitmap(drawPlayPauseBitmap(true))
                setOnClickListener { AudioPlayerManager.getInstance(context).togglePlayPause() }
            }
            expandedPlayPauseView = playPauseBtn
            centerGroup.addView(playPauseBtn)

            val nextBtn = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                    marginStart = (14 * density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageBitmap(drawNextBitmap())
                setOnClickListener { AudioPlayerManager.getInstance(context).playNext() }
            }
            expandedNextView = nextBtn
            centerGroup.addView(nextBtn)

            controlsRow.addView(centerGroup)

            // Right: AirPlay / Audio Output Icon
            val airplayBtn = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageBitmap(drawAirplayBitmap())
                setOnClickListener { openApp() }
            }
            expandedAirplayView = airplayBtn
            controlsRow.addView(airplayBtn)

            addView(controlsRow)
        }
    }

    private fun updateSongInfo(song: SongItem) {
        expandedTitleView?.text = song.title
        expandedArtistView?.text = song.artist

        if (song.thumbnailUrl.isNotBlank()) {
            if (song.thumbnailUrl != currentLoadedThumbnailUrl || cachedArtworkBitmap == null) {
                currentLoadedThumbnailUrl = song.thumbnailUrl
                cachedArtworkBitmap = null
                collapsedArtView?.setImageBitmap(drawCircularFallbackBitmap((24 * density).toInt()))
                expandedArtView?.setImageBitmap(drawFallbackArtworkBitmap((50 * density).toInt(), (12 * density).toInt()))
                loadArtworkBitmap(song.thumbnailUrl)
            } else {
                cachedArtworkBitmap?.let { bmp ->
                    collapsedArtView?.setImageBitmap(getCircularBitmap(bmp))
                    expandedArtView?.setImageBitmap(getRoundedBitmap(bmp, (12 * density).toInt()))
                }
            }
        } else {
            currentLoadedThumbnailUrl = ""
            cachedArtworkBitmap = null
            collapsedArtView?.setImageBitmap(drawCircularFallbackBitmap((24 * density).toInt()))
            expandedArtView?.setImageBitmap(drawFallbackArtworkBitmap((50 * density).toInt(), (12 * density).toInt()))
        }
    }

    private fun loadArtworkBitmap(url: String) {
        if (url.isBlank()) {
            cachedArtworkBitmap = null
            mainHandler.post {
                val fallbackColl = drawCircularFallbackBitmap((24 * density).toInt())
                val fallbackExp = drawFallbackArtworkBitmap((50 * density).toInt(), (12 * density).toInt())
                collapsedArtView?.setImageBitmap(fallbackColl)
                expandedArtView?.setImageBitmap(fallbackExp)
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            val imageLoader: ImageLoader = SingletonImageLoader.get(context)
            var bitmap: Bitmap? = null

            // 1. Try Coil with high-res URL first
            try {
                val req1 = ImageRequest.Builder(context).data(highResArtworkUrl(url)).build()
                val res1 = imageLoader.execute(req1)
                if (res1 is SuccessResult) {
                    val drawable = res1.image.asDrawable(context.resources)
                    bitmap = drawableToBitmap(drawable)
                }
            } catch (_: Exception) {}

            // 2. If high-res failed (e.g. 404), fallback to the original URL via Coil
            if (bitmap == null) {
                try {
                    val req2 = ImageRequest.Builder(context).data(url).build()
                    val res2 = imageLoader.execute(req2)
                    if (res2 is SuccessResult) {
                        val drawable = res2.image.asDrawable(context.resources)
                        bitmap = drawableToBitmap(drawable)
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback to HttpURLConnection + BitmapFactory
            if (bitmap == null) {
                try {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connectTimeout = 6000
                    connection.readTimeout = 6000
                    connection.connect()
                    val stream = connection.inputStream
                    val decoded = BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (decoded != null) {
                        bitmap = decoded
                    }
                } catch (_: Exception) {}
            }

            // 4. Set bitmaps into both collapsed (circular) and expanded (rounded-square) views
            if (bitmap != null) {
                cachedArtworkBitmap = bitmap
                try {
                    val circColl = getCircularBitmap(bitmap)
                    val roundedExp = getRoundedBitmap(bitmap, (12 * density).toInt())
                    mainHandler.post {
                        collapsedArtView?.setImageBitmap(circColl)
                        expandedArtView?.setImageBitmap(roundedExp)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error cropping circular/rounded bitmap: ${e.message}")
                    val fallbackColl = drawCircularFallbackBitmap((24 * density).toInt())
                    val fallbackExp = drawFallbackArtworkBitmap((50 * density).toInt(), (12 * density).toInt())
                    mainHandler.post {
                        collapsedArtView?.setImageBitmap(fallbackColl)
                        expandedArtView?.setImageBitmap(fallbackExp)
                    }
                }
            } else {
                val fallbackColl = drawCircularFallbackBitmap((24 * density).toInt())
                val fallbackExp = drawFallbackArtworkBitmap((50 * density).toInt(), (12 * density).toInt())
                mainHandler.post {
                    collapsedArtView?.setImageBitmap(fallbackColl)
                    expandedArtView?.setImageBitmap(fallbackExp)
                }
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE) {
                return bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
            }
            return bmp
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else (100 * density).toInt()
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else (100 * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        expandedPlayPauseView?.setImageBitmap(drawPlayPauseBitmap(isPlaying))
        if (isPlaying) {
            collapsedWaveform?.start()
            expandedWaveform?.start()
        } else {
            collapsedWaveform?.stop()
            expandedWaveform?.stop()
        }
    }

    private fun updateProgress(positionMs: Long, durationMs: Long) {
        val formattedElapsed = formatTime(positionMs)
        collapsedElapsedView?.text = formattedElapsed
        if (isUserSeeking) return

        expandedElapsedTimeView?.text = formattedElapsed
        val remainingMs = if (durationMs > positionMs) durationMs - positionMs else 0L
        expandedRemainingTimeView?.text = "-${formatTime(remainingMs)}"

        if (durationMs > 0) {
            val prog = ((positionMs.toFloat() / durationMs.toFloat()) * 1000).toInt().coerceIn(0, 1000)
            expandedSeekBar?.progress = prog
        } else {
            expandedSeekBar?.progress = 0
        }
    }

    private fun updateLikeState(isLiked: Boolean) {
        expandedLikeView?.setImageBitmap(drawStarBitmap(isLiked))
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%d:%02d", m, s)
    }

    private fun getStatusBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        if (result <= 0) {
            result = (28 * context.resources.displayMetrics.density).toInt()
        }
        return result
    }

    private fun getCircularBitmap(src: Bitmap): Bitmap {
        val safeSrc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
        val minDim = minOf(safeSrc.width, safeSrc.height)
        val output = Bitmap.createBitmap(minDim, minDim, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = minDim / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val srcRect = Rect(
            (safeSrc.width - minDim) / 2,
            (safeSrc.height - minDim) / 2,
            (safeSrc.width + minDim) / 2,
            (safeSrc.height + minDim) / 2
        )
        val dstRect = Rect(0, 0, minDim, minDim)
        canvas.drawBitmap(safeSrc, srcRect, dstRect, paint)
        return output
    }

    private fun getRoundedBitmap(src: Bitmap, cornerRadiusPx: Int): Bitmap {
        val safeSrc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
        val output = Bitmap.createBitmap(safeSrc.width, safeSrc.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, safeSrc.width, safeSrc.height)
        val rectF = RectF(rect)
        canvas.drawRoundRect(rectF, cornerRadiusPx.toFloat(), cornerRadiusPx.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(safeSrc, rect, rect, paint)
        return output
    }

    private fun drawCircularFallbackBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2C2C2E")
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val r = size * 0.16f
        canvas.drawCircle(radius - r * 0.6f, radius + r * 0.4f, r, notePaint)
        canvas.drawRect(radius - r * 0.1f, radius - r * 1.1f, radius + r * 0.3f, radius + r * 0.4f, notePaint)
        return bitmap
    }

    private fun drawFallbackArtworkBitmap(size: Int, cornerRadius: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2C2C2E")
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), cornerRadius.toFloat(), cornerRadius.toFloat(), bgPaint)

        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.16f
        canvas.drawCircle(cx - r * 0.6f, cy + r * 0.4f, r, notePaint)
        canvas.drawRect(cx - r * 0.1f, cy - r * 1.1f, cx + r * 0.3f, cy + r * 0.4f, notePaint)
        return bitmap
    }

    // =========================================================================================
    // PROGRAMMATIC VECTOR BITMAP GENERATORS
    // =========================================================================================
    private fun drawPlayPauseBitmap(isPlaying: Boolean): Bitmap {
        val size = (46 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        if (isPlaying) {
            val barW = size * 0.12f
            val barH = size * 0.44f
            val top = (size - barH) / 2f
            canvas.drawRoundRect(RectF(size * 0.32f, top, size * 0.32f + barW, top + barH), 4f, 4f, paint)
            canvas.drawRoundRect(RectF(size * 0.56f, top, size * 0.56f + barW, top + barH), 4f, 4f, paint)
        } else {
            val path = android.graphics.Path().apply {
                moveTo(size * 0.38f, size * 0.28f)
                lineTo(size * 0.72f, size * 0.50f)
                lineTo(size * 0.38f, size * 0.72f)
                close()
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    private fun drawPrevBitmap(): Bitmap {
        val size = (36 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(size * 0.22f, size * 0.28f, size * 0.28f, size * 0.72f), 3f, 3f, paint)
        val path = android.graphics.Path().apply {
            moveTo(size * 0.68f, size * 0.28f)
            lineTo(size * 0.34f, size * 0.50f)
            lineTo(size * 0.68f, size * 0.72f)
            close()
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    private fun drawNextBitmap(): Bitmap {
        val size = (36 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(size * 0.72f, size * 0.28f, size * 0.78f, size * 0.72f), 3f, 3f, paint)
        val path = android.graphics.Path().apply {
            moveTo(size * 0.32f, size * 0.28f)
            lineTo(size * 0.66f, size * 0.50f)
            lineTo(size * 0.32f, size * 0.72f)
            close()
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    private fun drawStarBitmap(isLiked: Boolean): Bitmap {
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLiked) SILVER_ACCENT else TEXT_SECONDARY
            style = if (isLiked) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 2.2f * density
        }

        val cx = size / 2f
        val cy = size / 2f
        val rOuter = size * 0.38f
        val rInner = size * 0.18f
        val path = android.graphics.Path()

        for (i in 0 until 10) {
            val r = if (i % 2 == 0) rOuter else rInner
            val angle = (i * 36 - 90) * Math.PI / 180.0
            val x = (cx + r * Math.cos(angle)).toFloat()
            val y = (cy + r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
        return bitmap
    }

    private fun drawAirplayBitmap(): Bitmap {
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SILVER_ACCENT
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        val cx = size / 2f
        val cy = size / 2f
        canvas.drawArc(RectF(cx - size * 0.34f, cy - size * 0.34f, cx + size * 0.34f, cy + size * 0.34f), 200f, 140f, false, paint)
        canvas.drawArc(RectF(cx - size * 0.22f, cy - size * 0.22f, cx + size * 0.22f, cy + size * 0.22f), 200f, 140f, false, paint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SILVER_ACCENT
            style = Paint.Style.FILL
        }
        val path = android.graphics.Path().apply {
            moveTo(cx, cy - size * 0.08f)
            lineTo(cx + size * 0.14f, cy + size * 0.18f)
            lineTo(cx - size * 0.14f, cy + size * 0.18f)
            close()
        }
        canvas.drawPath(path, fillPaint)
        return bitmap
    }

    // =========================================================================================
    // ANIMATED WAVEFORM EQUALIZER VIEW (Amber / Gold / Orange Gradient)
    // =========================================================================================
    class WaveformVisualizerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : View(context, attrs) {

        private val barPaints = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private var isRunning = false
        private var animStep = 0f
        private val barCount = 4

        private val gradientColors = intArrayOf(
            Color.parseColor("#FFA000"), // Amber
            Color.parseColor("#FF5722")  // Deep Orange
        )

        fun start() {
            if (!isRunning) {
                isRunning = true
                postInvalidateOnAnimation()
            }
        }

        fun stop() {
            isRunning = false
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            barPaints.shader = LinearGradient(0f, 0f, 0f, h, gradientColors, null, Shader.TileMode.CLAMP)

            val totalGaps = (barCount - 1) * (2f * resources.displayMetrics.density)
            val barWidth = (w - totalGaps) / barCount
            val gap = 2f * resources.displayMetrics.density
            val cornerRadius = barWidth / 2f

            for (i in 0 until barCount) {
                val left = i * (barWidth + gap)
                val right = left + barWidth

                val barFraction = if (isRunning) {
                    val phase = i * 0.9f
                    (0.35f + 0.65f * abs(sin(animStep + phase))).coerceIn(0.2f, 1f)
                } else {
                    0.25f
                }

                val barHeight = h * barFraction
                val top = (h - barHeight) / 2f
                val bottom = top + barHeight

                canvas.drawRoundRect(RectF(left, top, right, bottom), cornerRadius, cornerRadius, barPaints)
            }

            if (isRunning) {
                animStep += 0.16f
                postInvalidateOnAnimation()
            }
        }
    }
}
