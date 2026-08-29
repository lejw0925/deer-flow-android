package com.deerflow.mobile.ui.glass

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.deerflow.mobile.ui.theme.GeminiColors
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid Glass foundation built on Kyant0's Backdrop library.
 *
 * Architecture rules imposed by the library:
 * - Glass elements must NOT be inside the [Modifier.layerBackdrop] content they sample
 *   (self-sampling smears). Screens are structured as a recorded content layer plus
 *   sibling glass overlays.
 * - Never place [Modifier.layerBackdrop] after [Modifier.drawBackdrop] for the same
 *   backdrop chain; nested glass samples the root/screen backdrop instead of re-recording.
 *
 * Graceful degradation: backdrop effects need API 31+ (RenderEffect) and the lens needs
 * API 33+ (RuntimeShader); below that, or when no backdrop is provided, [glass] falls
 * back to a translucent frosted fill so the UI keeps working on minSdk 26.
 */

/** Backdrop available to glass elements in this subtree. Null disables sampling. */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/** Translucent tints used by glass and frosted surfaces; provided by the app theme. */
@Immutable
data class GlassTints(
    /** Surface tint drawn over sampled backdrop content. */
    val surface: Color,
    /** Slightly stronger tint for bars and sheets where readability matters. */
    val veil: Color,
    /** Fill for frosted surfaces that cannot sample a backdrop (list rows, menus). */
    val frosted: Color,
    /** Highlight stroke for frosted surfaces. */
    val frostedBorder: Color,
    /** Fallback fill used by [glass] when sampling is unavailable. */
    val fallback: Color,
)

val LocalGlassTints = compositionLocalOf<GlassTints?> { null }

private fun defaultGlassTints(dark: Boolean, surfaceContainerHigh: Color): GlassTints =
    if (dark) {
        GlassTints(
            surface = Color.Black.copy(alpha = 0.32f),
            veil = Color.Black.copy(alpha = 0.52f),
            frosted = surfaceContainerHigh.copy(alpha = 0.80f),
            frostedBorder = Color.White.copy(alpha = 0.16f),
            fallback = surfaceContainerHigh.copy(alpha = 0.96f),
        )
    } else {
        GlassTints(
            surface = Color.White.copy(alpha = 0.55f),
            veil = Color.White.copy(alpha = 0.74f),
            frosted = surfaceContainerHigh.copy(alpha = 0.88f),
            frostedBorder = Color.White.copy(alpha = 0.40f),
            fallback = surfaceContainerHigh.copy(alpha = 0.94f),
        )
    }

/** Resolves [LocalGlassTints], filling in theme-aware defaults when the theme did not provide any. */
@Composable
fun rememberGlassTints(): GlassTints {
    val dark = isSystemInDarkTheme()
    val sch = MaterialTheme.colorScheme
    val provided = LocalGlassTints.current
    return remember(dark, sch, provided) { provided ?: defaultGlassTints(dark, sch.surfaceContainerHigh) }
}

/** Effect tuning for [glass]. Centralized so the whole UI can be dialed in one place. */
object GlassTunables {
    val BlurRadius: Dp = 3.dp
    val LensHeight: Dp = 12.dp
    val LensAmount: Dp = 24.dp
    val PressExpand: Dp = 14.dp
    val PressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
}

/**
 * Records this subtree into a backdrop that glass elements can sample.
 * Place [GeminiAuroraBackground] inside the recorded layer so the aurora is
 * both visible on screen and picked up by sampling glass.
 */
@Composable
fun rememberGlassBackdrop(): LayerBackdrop {
    val background = MaterialTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

/** Gemini gradient: blue -> violet -> pink, for accent elements (sparkle icons). */
val GeminiBrush: Brush
    get() = Brush.linearGradient(
        listOf(GeminiColors.Blue, GeminiColors.Violet, GeminiColors.Pink),
    )

/** Tints the composable's drawn content with [brush] (for vector icons). */
fun Modifier.brushTint(brush: Brush): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }

/** Sci-fi accent for the aurora's roaming core glow (complements Gemini blue). */
private val AuroraCyan = Color(0xFF4EC8D8)

private const val AURORA_TWO_PI = 6.2831855f

/**
 * Master loop of the whole aurora. Every motion below completes an INTEGER
 * number of cycles inside this window, so the composition is exactly periodic:
 * the loop point is mathematically invisible (the earlier independent
 * 28/37/46s loops produced slow near-beat "color pumping" that read as jumps).
 */
private const val AURORA_LOOP_MILLIS = 144_000

/** Aurora redraw cadence: ~30fps writes (the motion is glacial; per-vsync
 *  writes re-rendered every glass surface at the panel's full refresh rate). */
private const val AURORA_FRAME_MILLIS = 33L

/**
 * The visible Gemini-style aurora: a neutral background wash with soft
 * blue/violet/pink radial glows that slowly drift and pulse ("breathing"),
 * plus a faint cyan roaming core for a sci-fi depth cue. Drawn as real
 * content (first child of the recorded layer) so both the screen and the
 * glass sampling see it. One master sawtooth drives everything (5/4/3 drift
 * cycles and 20/15/12 breath cycles per loop), read inside the draw block so
 * the animation invalidates drawing only, never composition.
 *
 * Cost gating: the phase advances only while the host lifecycle is RESUMED
 * (backgrounded/locked the loop cancels — zero draw invalidations), and only
 * every [AURORA_FRAME_MILLIS] (the motion is glacial; per-vsync writes kept
 * the whole glass pipeline re-rendering at 120fps while idle).
 */
@Composable
fun GeminiAuroraBackground(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.5f
    val lifecycle = LocalLifecycleOwner.current
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val loopNanos = AURORA_LOOP_MILLIS * 1_000_000L
            // Anchor so a background freeze resumes from the same loop position.
            val origin = withFrameNanos { it } - (phase.floatValue * loopNanos).toLong()
            while (true) {
                withFrameNanos { now ->
                    val elapsed = Math.floorMod(now - origin, loopNanos)
                    phase.floatValue = elapsed / loopNanos.toFloat()
                }
                delay(AURORA_FRAME_MILLIS)
            }
        }
    }
    Box(
        modifier.drawBehind {
            drawRect(background)
            val w = size.width
            val h = size.height
            val maxDim = size.maxDimension
            val t = phase.floatValue * AURORA_TWO_PI
            val alphaScale = if (dark) 1f else 0.72f

            // Drift angles: integer cycles per master loop (cos/sin paths).
            val aA = t * 5f
            val aB = t * 4f
            val aC = t * 3f
            // Breathing: pure sinusoids in [0, 1], no easing kinks.
            val pA = 0.5f - 0.5f * cos(t * 20f)
            val pB = 0.5f - 0.5f * cos(t * 15f)
            val pC = 0.5f - 0.5f * cos(t * 12f)

            // Blue: roams a wide arc across the upper half.
            drawRect(
                Brush.radialGradient(
                    0f to GeminiColors.Blue.copy(alpha = (0.34f + 0.18f * pA) * alphaScale),
                    1f to Color.Transparent,
                    center = Offset(w * (0.5f + 0.44f * cos(aA)), h * (0.14f + 0.12f * sin(aA))),
                    radius = maxDim * (0.72f + 0.10f * pB),
                ),
            )
            // Violet: right side, slow vertical sweep.
            drawRect(
                Brush.radialGradient(
                    0f to GeminiColors.Violet.copy(alpha = (0.26f + 0.14f * pB) * alphaScale),
                    1f to Color.Transparent,
                    center = Offset(w * (0.86f + 0.12f * cos(aB)), h * (0.42f + 0.24f * sin(aB))),
                    radius = maxDim * (0.66f + 0.10f * pC),
                ),
            )
            // Pink: bottom-left, breathing near the composer.
            drawRect(
                Brush.radialGradient(
                    0f to GeminiColors.Pink.copy(alpha = (0.20f + 0.10f * pC) * alphaScale),
                    1f to Color.Transparent,
                    center = Offset(w * (0.16f + 0.16f * cos(aC)), h * (0.92f + 0.08f * sin(aC))),
                    radius = maxDim * (0.62f + 0.08f * pA),
                ),
            )
            // Cyan: faint roaming core, the sci-fi depth cue.
            drawRect(
                Brush.radialGradient(
                    0f to AuroraCyan.copy(alpha = (0.10f + 0.06f * pB) * alphaScale),
                    1f to Color.Transparent,
                    center = Offset(
                        w * (0.5f + 0.30f * cos(aB * 0.5f + aC)),
                        h * (0.55f + 0.20f * sin(aC * 0.7f)),
                    ),
                    radius = maxDim * (0.50f + 0.08f * pA),
                ),
            )
        },
    )
}

/**
 * Draws this element as liquid glass over [backdrop]: vibrancy + blur (+ lens on API 33+
 * for corner-based shapes, with chromatic aberration), with a translucent [tint] for
 * readability. [highlight], [shadow] and [innerShadow] map to the Backdrop library's
 * light-model layers; pass them for the full liquid treatment (they are re-evaluated
 * every frame, so press/drag progress can modulate them).
 *
 * Falls back to a frosted fill when [backdrop] is null or the device is below API 31.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    tint: Color? = null,
    useLens: Boolean = false,
    chromaticAberration: Boolean = useLens,
    blurRadius: Dp = GlassTunables.BlurRadius,
    highlight: (() -> Highlight)? = null,
    shadow: (() -> Shadow)? = null,
    innerShadow: (() -> InnerShadow)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val tints = rememberGlassTints()
    val surfaceTint = tint ?: tints.surface
    return if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(blurRadius.toPx())
                // Color-mixing with the sampled content: pushes the backdrop's
                // hues into the glass so panels pick up what's behind them.
                // (No strength parameter in the library — on/off only.)
                vibrancy()
                if (useLens &&
                    shape is CornerBasedShape &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    lens(
                        GlassTunables.LensHeight.toPx(),
                        GlassTunables.LensAmount.toPx(),
                        depthEffect = true,
                        chromaticAberration = chromaticAberration,
                    )
                }
            },
            highlight = highlight,
            shadow = shadow,
            innerShadow = innerShadow,
            layerBlock = layerBlock,
            // A transparent tint means "pure glass": no fill, only refraction.
            onDrawSurface = { if (surfaceTint.alpha > 0f) drawRect(surfaceTint) },
        )
    } else {
        this.background(tints.fallback, shape)
    }
}

/**
 * Frosted look without backdrop sampling, for surfaces that cannot sample:
 * rows inside the recorded scrollable layer, popup-window menus/tooltips.
 *
 * Renders a layered fill — base tint plus a top-lit vertical sheen — and a
 * gradient highlight border (bright along the top edge, fading toward the
 * bottom) so popup surfaces read as glass rather than a flat translucent card.
 */
@Composable
fun Modifier.glassFrosted(
    shape: Shape,
    tint: Color? = null,
    borderColor: Color? = null,
): Modifier {
    val tints = rememberGlassTints()
    val fill = tint ?: tints.frosted
    val borderBase = borderColor ?: tints.frostedBorder
    val dark = fill.luminance() < 0.5f
    val sheenTop = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.30f)
    val sheenBottom = Color.White.copy(alpha = 0.02f)
    return this
        .clip(shape)
        .drawBehind {
            drawRect(fill)
            drawRect(Brush.verticalGradient(0f to sheenTop, 1f to sheenBottom))
        }
        .border(
            1.dp,
            Brush.verticalGradient(
                0f to borderBase.copy(alpha = (borderBase.alpha * 0.95f).coerceAtMost(1f)),
                1f to borderBase.copy(alpha = borderBase.alpha * 0.35f),
            ),
            shape,
        )
}

/**
 * Directional light edge for glass surfaces, echoing the Backdrop catalog's
 * 45° highlight: a hairline stroke that is brightest along the top-left corner
 * and fades toward the bottom-right, as if lit from the top-left.
 */
@Composable
fun Modifier.glassEdge(
    shape: Shape,
    width: Dp = 1.dp,
    light: Color = Color.White,
    peakAlpha: Float = 0.28f,
    tailAlpha: Float = 0.03f,
): Modifier {
    val dark = isSystemInDarkTheme()
    val peak = light.copy(alpha = if (dark) peakAlpha * 0.6f else peakAlpha)
    val tail = light.copy(alpha = if (dark) tailAlpha * 0.6f else tailAlpha)
    // linearGradient's default axis is top-left -> bottom-right.
    return this.border(width, Brush.linearGradient(listOf(peak, tail)), shape)
}

/**
 * Mostly-clear glass veil shared by the floating input bars (chat composer,
 * drawer search bar) so both mix sampled color identically: a light veil just
 * enough for text readability, with blur + vibrancy supplying the frost.
 */
@Composable
fun rememberFloatingBarTint(): Color =
    if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.40f)

/** Progress (0f..1f) of an ongoing press, for glass press-to-scale feedback. */
@Composable
fun rememberGlassPressProgress(): Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }

/** Drives [progress] from press gestures; pair with [glassPressLayerBlock]. */
fun Modifier.glassPressGestures(progress: Animatable<Float, AnimationVector1D>): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        this.pointerInput(progress) {
            awaitEachGesture {
                awaitFirstDown()
                scope.launch { progress.animateTo(1f, GlassTunables.PressSpec) }
                waitForUpOrCancellation()
                scope.launch { progress.animateTo(0f, GlassTunables.PressSpec) }
            }
        }
    }

/** Layer block that scales the glass element with press progress without scaling the backdrop. */
fun glassPressLayerBlock(progress: () -> Float): GraphicsLayerScope.() -> Unit = {
    val p = progress()
    if (p != 0f) {
        val maxScale = (size.width + GlassTunables.PressExpand.toPx()) / size.width
        val scale = lerp(1f, maxScale, p)
        scaleX = scale
        scaleY = scale
    }
}
