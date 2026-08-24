package com.deerflow.mobile.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * In-composition menu hosting for real liquid-glass popups.
 *
 * DropdownMenu/Popup content lives in a separate window, where the recorded
 * backdrop is unreachable ([LocalGlassBackdrop] propagates into the popup
 * composition but sampling another window's pixels silently renders nothing —
 * the menu never opens). Instead, screens with menus place ONE
 * [GlassMenuOverlayHost] as a sibling OUTSIDE their recorded layer (same rule
 * as every other glass element) and provide [LocalGlassMenuHost]. A
 * [GlassDropdownMenu] on such a screen then renders its panel in the activity
 * composition, where [Modifier.glass] can really sample the backdrop.
 *
 * [GlassMenuSurface] switches between real glass and the frosted fill via
 * [LocalGlassMenuInComposition], which is true ONLY around the overlay panel —
 * never inside a popup window, where the frosted fallback remains mandatory.
 */

/** Host for in-composition menus on this screen; null = menus use popup windows. */
val LocalGlassMenuHost = compositionLocalOf<GlassMenuHostState?> { null }

/** True only while composing the overlay panel; never true inside popup windows. */
internal val LocalGlassMenuInComposition = compositionLocalOf { false }

private val MenuAnchorGap = 4.dp
private val MenuScreenMargin = 8.dp

/** Tracks the single menu currently shown by a [GlassMenuOverlayHost]. */
@Stable
class GlassMenuHostState internal constructor() {
    internal var current by mutableStateOf<GlassMenuEntry?>(null)

    internal fun open(entry: GlassMenuEntry) {
        current = entry
    }

    internal fun close(entry: GlassMenuEntry) {
        if (current === entry) current = null
    }
}

@Stable
internal class GlassMenuEntry {
    /** Anchor bounds in root coordinates, kept live while the menu is open. */
    var anchorBounds by mutableStateOf<Rect?>(null)
    var onDismiss by mutableStateOf<() -> Unit>({})
    var panelModifier by mutableStateOf<Modifier>(Modifier)
    var panelShape by mutableStateOf<Shape>(RoundedCornerShape(24.dp))
    var content by mutableStateOf<(@Composable GlassMenuScope.() -> Unit)?>(null)
}

@Composable
fun rememberGlassMenuHostState(): GlassMenuHostState = remember { GlassMenuHostState() }

/**
 * Full-screen overlay rendering the open menu's panel with real backdrop glass.
 * Place as the LAST sibling inside the screen's `LocalGlassBackdrop` provider,
 * next to (never inside) the `Modifier.layerBackdrop` content.
 */
@Composable
fun GlassMenuOverlayHost(state: GlassMenuHostState, modifier: Modifier = Modifier) {
    val entry = state.current ?: return
    val content = entry.content ?: return
    var overlayBounds by remember { mutableStateOf<Rect?>(null) }
    var panelSize by remember(entry) { mutableStateOf(IntSize.Zero) }
    var panelPlacedAbove by remember(entry) { mutableStateOf(false) }
    val appear = remember(entry) { Animatable(0f) }
    LaunchedEffect(entry, state.current) {
        appear.snapTo(0f)
        appear.animateTo(1f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
    }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayBounds = it.boundsInRoot() },
    ) {
        // Modal scrim: consumes every outside press (blocking scroll-through)
        // and dismisses on tap.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { entry.onDismiss() },
                ),
        )
        val anchor = entry.anchorBounds
        val overlay = overlayBounds
        if (anchor != null && overlay != null) {
            Box(
                Modifier
                    .offset {
                        menuPanelOffset(
                            anchor = anchor,
                            overlay = overlay,
                            panel = panelSize,
                            gapPx = MenuAnchorGap.toPx(),
                            marginPx = MenuScreenMargin.toPx(),
                            onPlacedAbove = { placedAbove ->
                                if (panelPlacedAbove != placedAbove) panelPlacedAbove = placedAbove
                            },
                        )
                    }
                    .graphicsLayer {
                        val scale = lerp(0.94f, 1f, appear.value)
                        scaleX = scale
                        scaleY = scale
                        alpha = appear.value
                        transformOrigin =
                            if (panelPlacedAbove) TransformOrigin(0f, 1f) else TransformOrigin(0f, 0f)
                    }
                    .onGloballyPositioned { panelSize = it.size }
                    .then(entry.panelModifier)
                    // Keep stray taps on the panel background from reaching the scrim.
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
                    },
            ) {
                CompositionLocalProvider(LocalGlassMenuInComposition provides true) {
                    GlassMenuSurface(shape = entry.panelShape, content = content)
                }
            }
        }
    }
}

/** DropdownMenu-style placement: below the anchor, start-aligned, flipping/clamping on overflow. */
private fun menuPanelOffset(
    anchor: Rect,
    overlay: Rect,
    panel: IntSize,
    gapPx: Float,
    marginPx: Float,
    onPlacedAbove: (Boolean) -> Unit,
): IntOffset {
    val anchorLeft = anchor.left - overlay.left
    val anchorTop = anchor.top - overlay.top
    val panelWidth = panel.width.toFloat()
    val panelHeight = panel.height.toFloat()
    var x = anchorLeft
    if (x + panelWidth > overlay.width - marginPx) {
        x = anchorLeft + anchor.width - panelWidth // end-align like DropdownMenu
    }
    x = x.coerceIn(marginPx, (overlay.width - panelWidth - marginPx).coerceAtLeast(marginPx))
    var y = anchorTop + anchor.height + gapPx
    var placedAbove = false
    if (y + panelHeight > overlay.height - marginPx) {
        val above = anchorTop - gapPx - panelHeight
        if (above >= marginPx) {
            y = above
            placedAbove = true
        }
    }
    y = y.coerceAtLeast(marginPx)
    onPlacedAbove(placedAbove)
    return IntOffset(x.roundToInt(), y.roundToInt())
}

/**
 * Registers this menu with the nearest [LocalGlassMenuHost] while [expanded],
 * and leaves a zero-size node at the call site whose PARENT layout bounds serve
 * as the popup anchor (mirroring how DropdownMenu anchors to its parent).
 */
@Composable
internal fun HostedGlassDropdownMenu(
    host: GlassMenuHostState,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    content: @Composable GlassMenuScope.() -> Unit,
) {
    val entry = remember(host) { GlassMenuEntry() }
    entry.onDismiss = onDismissRequest
    entry.panelModifier = modifier
    entry.panelShape = shape
    entry.content = content
    BackHandler(enabled = expanded) { onDismissRequest() }
    DisposableEffect(host, expanded) {
        if (expanded) host.open(entry) else host.close(entry)
        onDispose { host.close(entry) }
    }
    Spacer(
        Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.parentLayoutCoordinates?.boundsInRoot()
            if (entry.anchorBounds != bounds) entry.anchorBounds = bounds
        },
    )
}
