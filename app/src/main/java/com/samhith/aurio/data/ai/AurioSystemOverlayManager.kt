package com.samhith.aurio.data.ai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

/**
 * System-Wide Floating AI Assistant Bubble Manager.
 *
 * Uses Android WindowManager (TYPE_APPLICATION_OVERLAY) to display the draggable AI Assistant
 * bubble on top of other apps and the phone's home screen when the app is minimized or closed.
 *
 * This is mode 2's entire surface: a tap starts a listening session, and the bubble reacts on the
 * spot - a pulsing blue halo while it listens and a status pill carrying the live transcript
 * and the assistant's reply, so the user gets feedback without the app ever coming forward.
 */
class AurioSystemOverlayManager private constructor(private val context: Context) {

    private val TAG = "AurioSystemOverlay"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var floatingBubbleView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isBubbleAttached = false
    private var isAppInForeground = false

    // Reactive pieces of the bubble
    private var haloView: View? = null
    private var avatarView: ImageView? = null
    private var statusPill: TextView? = null
    private var pulseAnimator: AnimatorSet? = null
    private var currentState: AssistantWakeState = AssistantWakeState.IDLE

    private val density = context.resources.displayMetrics.density
    private val bubbleSizePx = (BUBBLE_SIZE_DP * density).toInt()
    private val haloSizePx = (HALO_SIZE_DP * density).toInt()
    private val edgeMarginPx = (16 * density).toInt()

    var onBubbleClick: (() -> Unit)? = null

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        Log.d(TAG, "setAppInForeground: $inForeground (isBubbleAttached=$isBubbleAttached)")
        if (inForeground) {
            hideFloatingBubble()
        } else {
            if (hasOverlayPermission()) {
                showFloatingBubble()
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

    /**
     * Mirrors the assistant's state onto the bubble: halo pulse while listening, a soft steady
     * glow while speaking, and nothing at all when idle.
     */
    fun updateAssistantState(state: AssistantWakeState) {
        mainHandler.post {
            currentState = state
            if (!isBubbleAttached) return@post

            when (state) {
                AssistantWakeState.AWAKE_LISTENING,
                AssistantWakeState.TRAINING_NAME -> {
                    haloView?.visibility = View.VISIBLE
                    startPulse()
                }

                AssistantWakeState.PROCESSING,
                AssistantWakeState.SPEAKING -> {
                    haloView?.visibility = View.VISIBLE
                    stopPulse()
                    haloView?.alpha = 0.55f
                    haloView?.scaleX = 1f
                    haloView?.scaleY = 1f
                }

                else -> {
                    stopPulse()
                    haloView?.visibility = View.GONE
                    setStatusText(null)
                }
            }
        }
    }

    /** Shows the live transcript / reply next to the bubble. Passing null hides the pill. */
    fun setStatusText(text: String?) {
        mainHandler.post {
            val pill = statusPill ?: return@post
            if (text.isNullOrBlank()) {
                pill.visibility = View.GONE
                pill.text = ""
            } else {
                pill.text = text
                pill.visibility = View.VISIBLE
            }
        }
    }

    /** Drives the halo's brightness from the microphone level while listening. */
    fun updateVoiceLevel(rms: Float) {
        val halo = haloView ?: return
        if (currentState != AssistantWakeState.AWAKE_LISTENING) return
        mainHandler.post {
            halo.alpha = (0.35f + rms * 0.55f).coerceIn(0.2f, 0.95f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingBubble() {
        if (isAppInForeground || !hasOverlayPermission() || windowManager == null || isBubbleAttached) return

        mainHandler.post {
            try {
                if (isAppInForeground || isBubbleAttached) return@post

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val screenWidth = context.resources.displayMetrics.widthPixels
                    val screenHeight = context.resources.displayMetrics.heightPixels
                    x = screenWidth - haloSizePx - edgeMarginPx
                    y = (screenHeight * 0.65f).toInt()
                }

                windowParams = params

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                // Status pill: live transcript / spoken reply, sitting to the left of the bubble
                val pill = TextView(context).apply {
                    visibility = View.GONE
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(LABEL)
                    textSize = 12.5f
                    val padH = (12 * density).toInt()
                    val padV = (8 * density).toInt()
                    setPadding(padH, padV, padH, padV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 18 * density
                        setColor(PILL_BACKGROUND)
                        setStroke((1 * density).toInt(), SEPARATOR)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = (6 * density).toInt()
                    }
                    maxWidth = (context.resources.displayMetrics.widthPixels * 0.5f).toInt()
                }
                statusPill = pill

                // Bubble stack: pulsing halo behind, circular avatar in front
                val bubbleStack = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(haloSizePx, haloSizePx)
                }

                val halo = View(context).apply {
                    visibility = View.GONE
                    layoutParams = FrameLayout.LayoutParams(haloSizePx, haloSizePx, Gravity.CENTER)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ACCENT_HALO)
                        setStroke((2 * density).toInt(), ACCENT)
                    }
                }
                haloView = halo

                val avatarImageView = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx, Gravity.CENTER)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val avatarBitmap = loadAssistantCircularBitmap()
                    if (avatarBitmap != null) {
                        setImageBitmap(avatarBitmap)
                    } else {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(ACCENT)
                        }
                    }
                }
                avatarView = avatarImageView

                bubbleStack.addView(halo)
                bubbleStack.addView(avatarImageView)

                row.addView(pill)
                row.addView(bubbleStack)

                // Draggable Touch Listener with Tap Detection
                var initialX = 0
                var initialY = 0
                var initialTouchX = 0f
                var initialTouchY = 0f
                var isDrag = false

                row.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDrag = false
                            animateAvatarScale(0.9f)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            if (abs(dx) > 10 || abs(dy) > 10) {
                                isDrag = true
                                params.x = initialX + dx.toInt()
                                params.y = initialY + dy.toInt()
                                try {
                                    windowManager.updateViewLayout(view, params)
                                } catch (_: Exception) {
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            animateAvatarScale(1f)
                            if (!isDrag && event.action == MotionEvent.ACTION_UP) {
                                // Tap detected -> wake Aurio right where the user is
                                onBubbleClick?.invoke()
                            } else {
                                snapToNearestEdge(view, params)
                            }
                            true
                        }

                        else -> false
                    }
                }

                floatingBubbleView = row
                windowManager.addView(row, params)
                isBubbleAttached = true
                updateAssistantState(currentState)
                Log.d(TAG, "System floating bubble added to window.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding floating bubble to window: ${e.message}")
            }
        }
    }

    private fun snapToNearestEdge(view: View, params: WindowManager.LayoutParams) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val viewWidth = if (view.width > 0) view.width else haloSizePx
        val mid = screenWidth / 2
        params.x = if (params.x + viewWidth / 2 < mid) {
            edgeMarginPx
        } else {
            screenWidth - viewWidth - edgeMarginPx
        }
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    private fun animateAvatarScale(scale: Float) {
        avatarView?.animate()?.scaleX(scale)?.scaleY(scale)?.setDuration(120)?.start()
    }

    private fun startPulse() {
        val halo = haloView ?: return
        if (pulseAnimator?.isRunning == true) return

        val scaleX = ObjectAnimator.ofFloat(halo, View.SCALE_X, 0.85f, 1.12f)
        val scaleY = ObjectAnimator.ofFloat(halo, View.SCALE_Y, 0.85f, 1.12f)
        val alpha = ObjectAnimator.ofFloat(halo, View.ALPHA, 0.35f, 0.9f)
        listOf(scaleX, scaleY, alpha).forEach {
            it.duration = 720
            it.repeatCount = ValueAnimator.INFINITE
            it.repeatMode = ValueAnimator.REVERSE
        }

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        haloView?.scaleX = 1f
        haloView?.scaleY = 1f
    }

    fun hideFloatingBubble() {
        mainHandler.post {
            try {
                stopPulse()
                if (isBubbleAttached && floatingBubbleView != null && windowManager != null) {
                    windowManager.removeView(floatingBubbleView)
                    floatingBubbleView = null
                    haloView = null
                    avatarView = null
                    statusPill = null
                    isBubbleAttached = false
                    Log.d(TAG, "System floating bubble removed from window.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating bubble: ${e.message}")
            }
        }
    }

    private fun loadAssistantCircularBitmap(): Bitmap? {
        return try {
            val stream = try {
                context.assets.open("Ai_Assistant.png")
            } catch (_: Exception) {
                context.assets.open("Ai_Assistannt.png")
            }
            val original = BitmapFactory.decodeStream(stream) ?: return null
            getCroppedCircularBitmapWithGlow(original)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading avatar bitmap: ${e.message}")
            null
        }
    }

    private fun getCroppedCircularBitmapWithGlow(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        // Draw circle
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawOval(rectF, paint)

        // Clip source bitmap
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        // Draw Crimson border
        paint.xfermode = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.04f
        paint.color = ACCENT
        canvas.drawOval(rectF, paint)

        return output
    }

    companion object {
        private const val BUBBLE_SIZE_DP = 62
        private const val HALO_SIZE_DP = 78
        // Apple Modern palette (mirrors ui/theme/Color.kt; this file draws with Android Views)
        private val ACCENT = 0xFF007AFF.toInt()
        private val ACCENT_HALO = 0x33007AFF
        private val LABEL = 0xFF1D1D1F.toInt()
        private val SEPARATOR = 0xFFD2D2D7.toInt()
        private val PILL_BACKGROUND = 0xF2FFFFFF.toInt()

        @Volatile
        private var instance: AurioSystemOverlayManager? = null

        fun getInstance(context: Context): AurioSystemOverlayManager {
            return instance ?: synchronized(this) {
                instance ?: AurioSystemOverlayManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
