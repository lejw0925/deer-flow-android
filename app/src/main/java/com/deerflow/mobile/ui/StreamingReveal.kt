package com.deerflow.mobile.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Gemini-style progressive reveal for assistant output that appears while a run streams.
 * Content starts blurred, translucent and slightly shifted down, then sharpens to full
 * clarity over a short tween. The animation runs exactly once per composition: the decision
 * to animate is captured on first composition, so a message that finishes streaming keeps
 * any in-flight entrance until it completes, and once the entrance is done the composable
 * renders statically with no graphics layer and no blur. Blur needs API 31+; below that the
 * reveal degrades to a fade + shift.
 */
@Composable
internal fun StreamingReveal(
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val entrance = remember { animate }
    if (!entrance) {
        Box(modifier) { content() }
        return
    }
    val progress = remember { Animatable(0f) }
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        running = false
    }
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { 12.dp.toPx() }
    val shiftPx = with(density) { 12.dp.toPx() }
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier.then(
            if (running) {
                Modifier.graphicsLayer {
                    val p = progress.value
                    alpha = p
                    translationY = (1f - p) * shiftPx
                    renderEffect = if (blurSupported && p < 0.999f) {
                        val radius = ((1f - p) * blurRadiusPx).coerceAtLeast(0.1f)
                        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    } else {
                        null
                    }
                }
            } else {
                Modifier
            },
        ),
    ) { content() }
}
