/*
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 *
 * Pagaska Island is an independent implementation informed by observable
 * Android overlay behavior. It does not embed Auralys/DAPZ decompiled code.
 */
package com.metrolist.music.playback

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import kotlin.math.roundToInt

/**
 * Owns the system-window lifecycle for the music island.
 *
 * The overlay is deliberately driven by MusicService's existing ExoPlayer state:
 * no notification-listener permission or second playback controller is needed
 * for Pagaska Music's own playback.
 */
internal class PagaskaIslandController(
    private val context: MusicService,
    private val playerFlow: kotlinx.coroutines.flow.StateFlow<ExoPlayer?>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var player: ExoPlayer? = null
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var enabled = false
    private var position = "TOP_CENTER"

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            render()
        }
    }

    init {
        scope.launch {
            val preferences = context.dataStore.data.map { prefs ->
                (prefs[PagaskaIslandEnabledKey] ?: false) to
                    (prefs[PagaskaIslandPositionKey] ?: "TOP_CENTER")
            }.distinctUntilChanged()
            combine(preferences, playerFlow) { pref, activePlayer ->
                Triple(pref.first, pref.second, activePlayer)
            }.collect { (isEnabled, islandPosition, activePlayer) ->
                enabled = isEnabled
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
        if (!activePlayer.isPlaying && activePlayer.playbackState != Player.STATE_BUFFERING) {
            removeOverlay()
            return
        }

        val metadata = item.mediaMetadata
        val title = metadata.title?.toString()?.takeIf(String::isNotBlank) ?: "Memutar musik"
        val artist = metadata.artist?.toString()?.takeIf(String::isNotBlank) ?: "Pagaska Music"
        val view = root ?: createOverlay()?.also { root = it } ?: return
        val content = view.getChildAt(0) as? LinearLayout ?: return
        val titleView = content.findViewWithTag<TextView>("island_title")
        val artistView = content.findViewWithTag<TextView>("island_artist")
        val playButton = content.findViewWithTag<ImageButton>("island_play")
        titleView?.text = title
        artistView?.text = artist
        playButton?.contentDescription = if (activePlayer.isPlaying) "Jeda" else "Putar"
        playButton?.setImageResource(
            if (activePlayer.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play,
        )
        updateWindowPosition()
    }

    private fun createOverlay(): FrameLayout? {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        val shell = FrameLayout(context)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 12, 12))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.rgb(74, 74, 74))
            }
            elevation = dp(8).toFloat()
        }

        val mark = TextView(context).apply {
            text = "P"
            textSize = 15f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(245, 232, 75))
                cornerRadius = dp(14).toFloat()
            }
        }
        card.addView(mark, LinearLayout.LayoutParams(dp(30), dp(30)))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, dp(6), 0)
        }
        labels.addView(TextView(context).apply {
            tag = "island_title"
            text = "Pagaska Music"
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        labels.addView(TextView(context).apply {
            tag = "island_artist"
            text = "Sedang diputar"
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.LTGRAY)
        })
        card.addView(labels, LinearLayout.LayoutParams(dp(142), dp(38), 1f))

        val previous = islandButton(android.R.drawable.ic_media_previous, "Sebelumnya", dp(34)) {
            player?.seekToPreviousMediaItem()
        }
        val play = islandButton(android.R.drawable.ic_media_pause, "Putar atau jeda", dp(34)) {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            render()
        }.apply { tag = "island_play" }
        val next = islandButton(android.R.drawable.ic_media_next, "Berikutnya", dp(34)) {
            player?.seekToNextMediaItem()
        }
        card.addView(previous)
        card.addView(play)
        card.addView(next)
        shell.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        shell.setOnClickListener {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
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
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
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

    private fun islandButton(
        icon: Int,
        description: String,
        size: Int,
        action: () -> Unit,
    ): ImageButton = ImageButton(context).apply {
        contentDescription = description
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        background = null
        setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(size, size)
    }

    private fun updateWindowPosition() {
        val view = root ?: return
        val window = params ?: return
        val density = context.resources.displayMetrics.density
        window.gravity = Gravity.TOP or (when (position) {
            "TOP_LEFT" -> Gravity.START
            "TOP_RIGHT" -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        })
        window.x = if (position == "TOP_CENTER") 0 else (12 * density).roundToInt()
        window.y = (6 * density).roundToInt()
        runCatching { windowManager.updateViewLayout(view, window) }
    }

    private fun removeOverlay() {
        val view = root ?: return
        root = null
        params = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    fun release() {
        player?.removeListener(playerListener)
        player = null
        removeOverlay()
        scope.cancel()
    }
}
