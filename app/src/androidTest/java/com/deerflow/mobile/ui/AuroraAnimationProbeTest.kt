package com.deerflow.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Probe: an InfiniteTransition value read inside drawBehind must keep redrawing. */
class AuroraAnimationProbeTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun ProbeBox() {
        val transition = rememberInfiniteTransition(label = "probe")
        val pulse = transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(200, easing = LinearEasing), RepeatMode.Restart),
            "pulse",
        )
        Box(
            Modifier.size(64.dp).drawBehind {
                drawRect(Color(1f, 0f, 0f, alpha = 0.2f + 0.8f * pulse.value))
            },
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun infiniteTransitionDrivesContinuousRedraw() {
        // Infinite animations never go idle: drive the frame clock manually.
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { ProbeBox() } }
        compose.mainClock.advanceTimeByFrame()
        val first = compose.onRoot().captureToImage()
        compose.mainClock.advanceTimeBy(150)
        val second = compose.onRoot().captureToImage()
        assertFalse(
            "drawBehind must redraw as the transition advances",
            first.asAndroidBitmap().sameAs(second.asAndroidBitmap()),
        )
    }
}
