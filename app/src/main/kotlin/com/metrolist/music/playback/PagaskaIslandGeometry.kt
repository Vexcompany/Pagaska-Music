/*
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0
 */
package com.metrolist.music.playback

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager

/**
 * Display geometry shared by the island and its floating bubble.
 *
 * WindowMetrics is used on Android 11+; older versions fall back to the
 * current resource metrics and attached-window cutout information where
 * Android exposes it.
 */
internal data class PagaskaIslandDisplayArea(
    val widthPx: Int,
    val heightPx: Int,
    val topCutout: Rect?,
)

internal object PagaskaIslandGeometry {
    fun read(
        context: Context,
        windowManager: WindowManager,
        attachedView: View? = null,
    ): PagaskaIslandDisplayArea {
        val width: Int
        val height: Int
        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            width = metrics.bounds.width()
            height = metrics.bounds.height()
            metrics.windowInsets.displayCutout
        } else {
            val metrics = context.resources.displayMetrics
            width = metrics.widthPixels
            height = metrics.heightPixels
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attachedView?.rootWindowInsets?.displayCutout
            } else {
                null
            }
        }

        val topRect = cutout?.boundingRects
            ?.asSequence()
            ?.filter { rect -> rect.top <= maxOf(cutout.safeInsetTop, 48) }
            ?.maxByOrNull { rect -> rect.width() }
            ?.let(::Rect)

        return PagaskaIslandDisplayArea(
            widthPx = width.coerceAtLeast(1),
            heightPx = height.coerceAtLeast(1),
            topCutout = topRect,
        )
    }
}
