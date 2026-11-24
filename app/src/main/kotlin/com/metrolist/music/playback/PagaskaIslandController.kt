/*
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 *
 * Pagaska Island is an independent Android overlay implementation.
 * Its interaction model is inspired by common third-party music islands;
 * it does not embed Auralys/DAPZ decompiled source.
 */
package com.metrolist.music.playback

import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.target
import com.metrolist.music.MainActivity
import com.metrolist.music.constants.PagaskaIslandEnabledKey
import com.metrolist.music.constants.PagaskaIslandPositionKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Owns Pagaska Island's system-window lifecycle and binds it to the existing
 * MusicService ExoPlayer. The island never creates a second playback engine.
 */
internal class PagaskaIslandController(
    private val context: MusicService,
    private val playerFlow: kotlinx.coroutines.flow.StateFlow<ExoPlayer?>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var player: ExoPlayer? = null
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var enabled = false
    private var position = "TOP_CENTER"
    private var expanded = false
    private var minimized = false
    private var islandCard: View? = null
    private var bubbleView: View? = null
    private var bubbleCoverView: ImageView? = null
    private var minimizeButton: ImageButton? = null
    private var bubbleStartX = 0f
    private var bubbleStartY = 0f
    private var bubbleStartLeft = 0
    private var bubbleStartTop = 0
    private var bubbleDragging = false
    private val islandPrefs by lazy { context.getSharedPreferences("pagaska_island_overlay", Context.MODE_PRIVATE) }
    private var userSeeking = false
    private var coverUrl: String? = null
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartWindowY = 0
    private var dragging = false
    private var hasUserPosition = false
    private var dragStartLeft = 0

    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var playButton: ImageButton? = null
    private var coverView: ImageView? = null
    private var progressView: SeekBar? = null
    private var elapsedView: TextView? = null
    private var durationView: TextView? = null
    private var expandedPanel: View? = null
    private var previousButton: ImageButton? = null
    private var nextButton: ImageButton? = null


    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            mainHandler.post {
                val view = root ?: return@post
                view.post {
                    if (minimized) showBubble() else updateWindowPosition()
                }
            }
        }

        override fun onLowMemory() = Unit
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (root == null) return
            updateProgress()
            mainHandler.postDelayed(this, 500L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            render()
        }
    }

    init {
        context.registerComponentCallbacks(configurationCallbacks)
        scope.launch {
            val preferences = context.dataStore.data.map { prefs ->
                (prefs[PagaskaIslandEnabledKey] ?: false) to
                    (prefs[PagaskaIslandPositionKey] ?: "TOP_CENTER")
            }.distinctUntilChanged()
            combine(preferences, playerFlow) { pref, activePlayer ->
                Triple(pref.first, pref.second, activePlayer)
            }.collect { (isEnabled, islandPosition, activePlayer) ->
                enabled = isEnabled
                if (position != islandPosition) {
                    hasUserPosition = false
                    islandPrefs.edit().remove("island_x").remove("island_y").apply()
                }
                position = islandPosition
                setPlayer(activePlayer)
                if (!enabled || !Settings.canDrawOverlays(context)) {
                    removeOverlay()
                } else {
                    render()
                }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun setPlayer(next: ExoPlayer?) {
        if (player === next) return
        player?.removeListener(playerListener)
        player = next
        player?.addListener(playerListener)
        render()
    }

    private fun render() {
        if (!enabled || !Settings.canDrawOverlays(context)) {
            removeOverlay()
            return
        }
        val activePlayer = player ?: run {
            removeOverlay()
            return
        }
        val item = activePlayer.currentMediaItem ?: run {
            removeOverlay()
            return
        }
        if (activePlayer.playbackState == Player.STATE_IDLE ||
            activePlayer.playbackState == Player.STATE_ENDED
        ) {
            removeOverlay()
            return
        }

        val metadata = item.mediaMetadata
        minimized = islandPrefs.getBoolean("minimized", false)
        titleView?.text = metadata.title?.toString()?.takeIf(String::isNotBlank) ?: "Memutar musik"
        artistView?.text = metadata.artist?.toString()?.takeIf(String::isNotBlank) ?: "Pagaska Music"
        playButton?.apply {
            contentDescription = if (activePlayer.isPlaying) "Jeda" else "Putar"
            setImageResource(
                if (activePlayer.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
        }

        val artwork = metadata.artworkUri?.toString()
        val view = root ?: createOverlay()?.also { root = it } ?: return
        if (artwork != coverUrl) {
            coverUrl = artwork
            listOfNotNull(coverView, bubbleCoverView).forEach { image ->
                if (artwork.isNullOrBlank()) {
                    image.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    val request = ImageRequest.Builder(context)
                        .data(artwork)
                        .target(image)
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }
        }
        updateExpandedState()
        if (minimized) showBubble() else showIslandCard()
        updateWindowPosition()
        updateProgress()
        if (view.parent == null) return
        mainHandler.removeCallbacks(progressUpdater)
        mainHandler.post(progressUpdater)
    }

    private fun createOverlay(): FrameLayout? {
        // The shell itself is the offset shadow plate. The card sits 4dp
        // above and left, keeping the shadow crisp with no blur or elevation.
        val shell = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            background = roundedRect(Color.BLACK, 8, Color.BLACK, 0)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = roundedRect(Color.rgb(245, 232, 75), 8, Color.BLACK, 3)
        }
        islandCard = card
        shell.addView(
            card,
            FrameLayout.LayoutParams(-2, -2).apply {
                rightMargin = dp(4)
                bottomMargin = dp(4)
            },
        )

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val cover = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_media_play)
            background = roundedRect(Color.WHITE, 5, Color.BLACK, 2)
            clipToOutline = true
            contentDescription = "Sampul album"
        }
        coverView = cover
        topRow.addView(cover, LinearLayout.LayoutParams(dp(34), dp(34)))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(7), 0)
        }
        titleView = TextView(context).apply {
            text = "Pagaska Music"
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
        }
        artistView = TextView(context).apply {
            text = "Sedang diputar"
            textSize = 9f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.rgb(45, 45, 45))
        }
        labels.addView(titleView)
        labels.addView(artistView)
        topRow.addView(labels, LinearLayout.LayoutParams(dp(118), dp(36), 1f))

        previousButton = islandButton(
            android.R.drawable.ic_media_previous,
            "Sebelumnya",
            dp(30),
        ) { player?.seekToPreviousMediaItem() }
        playButton = islandButton(
            android.R.drawable.ic_media_pause,
            "Putar atau jeda",
            dp(32),
        ) {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            render()
        }
        minimizeButton = islandButton(
            android.R.drawable.arrow_down_float,
            "Minimalkan Pagaska Island",
            dp(28),
        ) { minimizeIsland() }
        nextButton = islandButton(
            android.R.drawable.ic_media_next,
            "Berikutnya",
            dp(30),
        ) { player?.seekToNextMediaItem() }
        topRow.addView(previousButton)
        topRow.addView(playButton)
        topRow.addView(nextButton)
        topRow.addView(minimizeButton)
        card.addView(topRow)

        val details = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), 0)
        }
        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        elapsedView = timeLabel("0:00")
        durationView = timeLabel("0:00")
        timeRow.addView(elapsedView, LinearLayout.LayoutParams(0, -2, 1f))
        timeRow.addView(durationView)
        details.addView(timeRow)

        progressView = SeekBar(context).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Color.BLACK)
            progressBackgroundTintList = ColorStateList.valueOf(Color.argb(90, 0, 0, 0))
            thumbTintList = ColorStateList.valueOf(Color.BLACK)
            setPadding(0, 0, 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) elapsedView?.text = formatTime(
                        ((player?.duration?.coerceAtLeast(0L) ?: 0L) * progress / 1000L),
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val duration = player?.duration ?: 0L
                    if (duration > 0L) player?.seekTo(duration * seekBar.progress / 1000L)
                    userSeeking = false
                    updateProgress()
                }
            })
        }
        details.addView(progressView, LinearLayout.LayoutParams(-1, dp(28)))
        expandedPanel = details
        card.addView(details)

        // The card handles gestures on its empty surface; child controls retain touch ownership.
        card.setOnTouchListener { _, event -> handleDrag(event) }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(6)
            setTitle("PagaskaIsland")
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        params = layoutParams
        return runCatching {
            windowManager.addView(shell, layoutParams)
            shell
        }.getOrElse {
            params = null
            null
        }
    }

    private fun minimizeIsland() {
        minimized = true
        islandPrefs.edit().putBoolean("minimized", true).apply()
        expanded = false
        showBubble()
    }

    private fun restoreIsland() {
        minimized = false
        islandPrefs.edit().putBoolean("minimized", false).apply()
        bubbleView?.let { root?.removeView(it) }
        bubbleView = null
        showIslandCard()
        updateWindowPosition()
    }

    private fun animateOverlayChange() {
        val container = root ?: return
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            TransitionManager.beginDelayedTransition(container, AutoTransition().apply { duration = 210L })
        }
    }

    private fun showIslandCard() {
        animateOverlayChange()
        root?.background = roundedRect(Color.BLACK, 8, Color.BLACK, 0)
        islandCard?.visibility = View.VISIBLE
        bubbleView?.visibility = View.GONE
        root?.post { root?.let { view -> params?.let { runCatching { windowManager.updateViewLayout(view, it) } } } }
    }

    private fun showBubble() {
        val shell = root ?: return
        animateOverlayChange()
        shell.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        islandCard?.visibility = View.GONE
        bubbleView?.let { existing ->
            existing.visibility = View.VISIBLE
            params?.let { window ->
                window.gravity = Gravity.TOP or Gravity.START
                val savedX = islandPrefs.getInt("bubble_x", -1)
                val savedY = islandPrefs.getInt("bubble_y", -1)
                val area = PagaskaIslandGeometry.read(context, windowManager, root)
                window.x = (if (savedX >= 0) savedX else (area.widthPx - dp(70)).coerceAtLeast(0))
                    .coerceIn(0, (area.widthPx - dp(56)).coerceAtLeast(0))
                window.y = (if (savedY >= 0) savedY else dp(140))
                    .coerceIn(dp(24), (area.heightPx - dp(56)).coerceAtLeast(dp(24)))
            }
            shell.post { params?.let { runCatching { windowManager.updateViewLayout(shell, it) } } }
            return
        }
        val bubble = FrameLayout(context).apply {
            background = roundedRect(Color.rgb(245, 232, 75), 50, Color.BLACK, 3)
            elevation = 0f
            val art = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_media_play)
                contentDescription = "Pagaska Island, ketuk untuk membuka kontrol musik"
                clipToOutline = true
                background = roundedRect(Color.WHITE, 50, Color.BLACK, 1)
            }
            bubbleCoverView = art
            coverUrl?.takeIf(String::isNotBlank)?.let { url ->
                context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).target(art).build())
            }
            addView(art, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            setOnClickListener { restoreIsland() }
            setOnTouchListener { _, event -> handleBubbleDrag(event) }
        }
        bubbleView = bubble
        shell.addView(bubble, FrameLayout.LayoutParams(dp(56), dp(56)))
        val savedX = islandPrefs.getInt("bubble_x", -1)
        val savedY = islandPrefs.getInt("bubble_y", -1)
        params?.let { window ->
            window.gravity = Gravity.TOP or Gravity.START
            val area = PagaskaIslandGeometry.read(context, windowManager, root)
            window.x = (if (savedX >= 0) savedX else (area.widthPx - dp(70)).coerceAtLeast(0))
                .coerceIn(0, (area.widthPx - dp(56)).coerceAtLeast(0))
            window.y = (if (savedY >= 0) savedY else dp(140))
                .coerceIn(dp(24), (area.heightPx - dp(56)).coerceAtLeast(dp(24)))
        }
        bubble.translationX = 0f
        bubble.translationY = 0f
        shell.post { params?.let { runCatching { windowManager.updateViewLayout(shell, it) } } }
    }

    private fun handleBubbleDrag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bubbleStartX = event.rawX
                bubbleStartY = event.rawY
                bubbleStartLeft = params?.x ?: 0
                bubbleStartTop = params?.y ?: 0
                bubbleDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - bubbleStartX).roundToInt()
                val dy = (event.rawY - bubbleStartY).roundToInt()
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (!bubbleDragging && (abs(dx) > slop || abs(dy) > slop)) bubbleDragging = true
                if (bubbleDragging) {
                    val window = params ?: return true
                    val area = PagaskaIslandGeometry.read(context, windowManager, root)
                    window.x = (bubbleStartLeft + dx).coerceIn(0, (area.widthPx - dp(56)).coerceAtLeast(0))
                    window.y = (bubbleStartTop + dy).coerceIn(dp(24), (area.heightPx - dp(100)).coerceAtLeast(dp(24)))
                    root?.let { shell -> runCatching { windowManager.updateViewLayout(shell, window) } }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (bubbleDragging) {
                    val window = params
                    if (window != null) {
                        val area = PagaskaIslandGeometry.read(context, windowManager, root)
                        val maxX = (area.widthPx - dp(56)).coerceAtLeast(0)
                        val maxY = (area.heightPx - dp(56)).coerceAtLeast(0)
                        val targetX = if (window.x + dp(28) < area.widthPx / 2) dp(6) else maxX - dp(6)
                        window.x = targetX.coerceIn(0, maxX)
                        window.y = window.y.coerceIn(0, maxY)
                        root?.let { shell -> runCatching { windowManager.updateViewLayout(shell, window) } }
                        islandPrefs.edit()
                            .putInt("bubble_x", window.x)
                            .putInt("bubble_y", window.y)
                            .apply()
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    restoreIsland()
                }
                bubbleDragging = false
                return true
            }
        }
        return true
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        updateExpandedState()
    }

    private fun updateExpandedState() {
        val panel = expandedPanel ?: return
        root?.let { container ->
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                TransitionManager.beginDelayedTransition(
                    container,
                    AutoTransition().apply { duration = 190L },
                )
            }
        }
        panel.visibility = if (expanded) View.VISIBLE else View.GONE
        previousButton?.visibility = if (expanded) View.VISIBLE else View.GONE
        nextButton?.visibility = if (expanded) View.VISIBLE else View.GONE
        coverView?.let {
            val size = if (expanded) dp(42) else dp(34)
            it.layoutParams = it.layoutParams.apply {
                width = size
                height = size
            }
        }
        root?.let { view ->
            view.post {
                params?.let { runCatching { windowManager.updateViewLayout(view, it) } }
            }
        }
    }

    private fun updateProgress() {
        val activePlayer = player ?: return
        val duration = activePlayer.duration.takeIf { it > 0L } ?: 0L
        val current = activePlayer.currentPosition.coerceAtLeast(0L)
        if (!userSeeking) {
            progressView?.progress = if (duration > 0L) {
                ((current * 1000L) / duration).toInt().coerceIn(0, 1000)
            } else {
                0
            }
            elapsedView?.text = formatTime(current)
        }
        durationView?.text = formatTime(duration)
        progressView?.isEnabled = duration > 0L
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = max(0L, milliseconds / 1000L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private fun timeLabel(value: String) = TextView(context).apply {
        text = value
        textSize = 9f
        setTextColor(Color.BLACK)
        typeface = Typeface.MONOSPACE
    }

    private fun roundedRect(
        fill: Int,
        radiusDp: Int,
        stroke: Int,
        strokeDp: Int,
    ) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun islandButton(
        icon: Int,
        description: String,
        size: Int,
        action: () -> Unit,
    ): ImageButton = ImageButton(context).apply {
        contentDescription = description
        setImageResource(icon)
        setColorFilter(Color.BLACK)
        background = roundedRect(Color.WHITE, 5, Color.BLACK, 2)
        setPadding(dp(5), dp(5), dp(5), dp(5))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginStart = dp(3)
        }
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        val window = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartWindowY = window.y
                val screenWidth = context.resources.displayMetrics.widthPixels
                val viewWidth = root?.width ?: 0
                dragStartLeft = when (window.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                    Gravity.CENTER_HORIZONTAL -> (screenWidth - viewWidth) / 2 + window.x
                    Gravity.END -> screenWidth - viewWidth - window.x
                    else -> window.x
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartRawX).roundToInt()
                val dy = (event.rawY - dragStartRawY).roundToInt()
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                if (dragging) {
                    window.gravity = Gravity.TOP or Gravity.START
                    window.x = (dragStartLeft + dx).coerceAtLeast(0)
                    window.y = (dragStartWindowY + dy).coerceAtLeast(0)
                    root?.let { runCatching { windowManager.updateViewLayout(it, window) } }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val view = root
                    val area = PagaskaIslandGeometry.read(context, windowManager, view)
                    val viewWidth = view?.width?.takeIf { it > 0 } ?: dp(220)
                    val viewHeight = view?.height?.takeIf { it > 0 } ?: dp(72)
                    window.gravity = Gravity.TOP or Gravity.START
                    window.x = window.x.coerceIn(0, (area.widthPx - viewWidth).coerceAtLeast(0))
                    window.y = window.y.coerceIn(0, (area.heightPx - viewHeight).coerceAtLeast(0))
                    view?.let { runCatching { windowManager.updateViewLayout(it, window) } }
                    islandPrefs.edit().putInt("island_x", window.x).putInt("island_y", window.y).apply()
                    hasUserPosition = true
                    dragging = false
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()) {
                        openMainActivity()
                    } else {
                        setExpanded(!expanded)
                    }
                }
                return true
            }
        }
        return true
    }

    private fun openMainActivity() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun updateWindowPosition() {
        val view = root ?: return
        val window = params ?: return
        if (dragging || minimized) return
        if (islandPrefs.contains("island_x") && islandPrefs.contains("island_y")) {
            val area = PagaskaIslandGeometry.read(context, windowManager, view)
            val viewWidth = view.width.takeIf { it > 0 } ?: dp(220)
            val viewHeight = view.height.takeIf { it > 0 } ?: dp(72)
            window.gravity = Gravity.TOP or Gravity.START
            window.x = islandPrefs.getInt("island_x", 0)
                .coerceIn(0, (area.widthPx - viewWidth).coerceAtLeast(0))
            window.y = islandPrefs.getInt("island_y", dp(6))
                .coerceIn(0, (area.heightPx - viewHeight).coerceAtLeast(0))
            runCatching { windowManager.updateViewLayout(view, window) }
            return
        }
        if (hasUserPosition) return
        val area = PagaskaIslandGeometry.read(context, windowManager, view)
        val viewWidth = view.width.takeIf { it > 0 } ?: dp(220)
        window.gravity = Gravity.TOP or Gravity.START
        window.x = when (position) {
            "TOP_LEFT" -> dp(12)
            "TOP_RIGHT" -> (area.widthPx - viewWidth - dp(12)).coerceAtLeast(0)
            else -> ((area.topCutout?.centerX() ?: (area.widthPx / 2)) - viewWidth / 2)
                .coerceIn(0, (area.widthPx - viewWidth).coerceAtLeast(0))
        }
        window.y = area.topCutout?.top?.coerceAtLeast(0) ?: dp(6)
        runCatching { windowManager.updateViewLayout(view, window) }
    }

    private fun removeOverlay() {
        val view = root ?: return
        root = null
        params = null
        mainHandler.removeCallbacks(progressUpdater)
        expanded = false
        userSeeking = false
        dragging = false
        hasUserPosition = false
        islandCard = null
        bubbleView = null
        bubbleCoverView = null
        minimizeButton = null
        coverUrl = null
        titleView = null
        artistView = null
        playButton = null
        coverView = null
        progressView = null
        elapsedView = null
        durationView = null
        expandedPanel = null
        previousButton = null
        nextButton = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    fun release() {
        context.unregisterComponentCallbacks(configurationCallbacks)
        player?.removeListener(playerListener)
        player = null
        removeOverlay()
        scope.cancel()
    }
}
