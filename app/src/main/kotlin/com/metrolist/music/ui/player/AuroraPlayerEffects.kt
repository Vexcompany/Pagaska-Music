/*
 * Pagaska Music — Aurora Player ambience
 * Lightweight decorative effects for the expanded player.
 */

package com.metrolist.music.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Subtle artwork-inspired ambience for the immersive player.
 *
 * The effect is intentionally limited to the expanded player: it does not
 * create a foreground service, wake the display, or draw over Android's
 * actual lock screen. Animation is inexpensive and tap bursts are transient.
 */
@Composable
fun AuroraPlayerEffects(
    modifier: Modifier = Modifier,
    accent: Color,
    enabled: Boolean,
    isPlaying: Boolean,
) {
    if (!enabled) return

    val transition = rememberInfiniteTransition(label = "auroraAmbient")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "auroraPhase",
    )
    val phase = if (isPlaying) animatedPhase else 0f

    var tapOrigin by remember { mutableStateOf<Offset?>(null) }
    val rippleProgress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                detectTapGestures { position ->
                    tapOrigin = position
                    scope.launch {
                        rippleProgress.snapTo(0f)
                        rippleProgress.animateTo(1f, animationSpec = tween(720, easing = LinearEasing))
                        tapOrigin = null
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val maxRadius = maxOf(w, h) * 0.78f

        // Two slow, very soft color fields keep the artwork atmosphere alive.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.17f), accent.copy(alpha = 0f)),
                center = Offset(w * (0.24f + 0.035f * sin(phase)), h * 0.28f),
                radius = maxRadius,
            ),
            radius = maxRadius,
            center = Offset(w * 0.24f, h * 0.28f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.11f), Color.Transparent),
                center = Offset(w * 0.78f, h * (0.72f + 0.04f * cos(phase))),
                radius = maxRadius * 0.82f,
            ),
            radius = maxRadius * 0.82f,
            center = Offset(w * 0.78f, h * 0.72f),
        )

        // A small set of slow-moving dust particles; no audio polling required.
        repeat(26) { index ->
            val angle = index * (2.0 * PI / 26.0) + phase * (if (index % 2 == 0) 0.16f else -0.12f)
            val orbit = 0.18f + (index % 5) * 0.105f
            val x = w * (0.5f + cos(angle).toFloat() * orbit)
            val y = h * (0.5f + sin(angle).toFloat() * orbit * 0.82f)
            val pulse = 0.45f + 0.35f * sin(phase + index).toFloat().let { (it + 1f) / 2f }
            drawCircle(
                color = accent.copy(alpha = 0.10f * pulse),
                radius = 1.1f + (index % 3) * 0.45f,
                center = Offset(x, y),
            )
        }

        tapOrigin?.let { origin ->
            val progress = rippleProgress.value.coerceIn(0f, 1f)
            val radius = 22f + progress * maxOf(w, h) * 0.22f
            val alpha = (1f - progress) * 0.48f
            drawCircle(
                color = accent.copy(alpha = alpha),
                radius = radius,
                center = origin,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f),
            )
            repeat(12) { index ->
                val angle = index * (2.0 * PI / 12.0)
                val distance = radius * (0.58f + progress * 0.34f)
                drawCircle(
                    color = accent.copy(alpha = alpha * 0.75f),
                    radius = (3.2f * (1f - progress)).coerceAtLeast(0.35f),
                    center = Offset(
                        origin.x + cos(angle).toFloat() * distance,
                        origin.y + sin(angle).toFloat() * distance,
                    ),
                )
            }
        }
    }
}
