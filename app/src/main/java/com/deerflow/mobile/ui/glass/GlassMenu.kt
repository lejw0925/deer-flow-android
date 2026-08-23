package com.deerflow.mobile.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.deerflow.mobile.ui.theme.GeminiColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Liquid-glass popup menus.
 *
 * Popup windows cannot sample the activity backdrop (they live in their own
 * window), so these surfaces use the frosted treatment: a strong veil fill plus
 * a hairline highlight — no gray elevation shadow ring.
 *
 * Selection feedback is a glass pill that springs to the pressed item and
 * follows the finger with a damped, squashy animation while dragging across
 * items; releasing after a drag selects the item under the finger.
 */

@Stable
class GlassMenuScope internal constructor() {
    internal var nextItemIndex = 0
    internal var containerCoordinates: LayoutCoordinates? = null
    internal val itemBounds = mutableStateMapOf<Int, Rect>()
    internal val itemActions = mutableMapOf<Int, () -> Unit>()
    internal val itemEnabled = mutableMapOf<Int, Boolean>()
}

/** Non-selectable section label inside a glass menu. */
@Composable
fun GlassMenuScope.GlassMenuHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** A selectable row inside [GlassDropdownMenu] / [GlassMenuSurface]. */
@Composable
fun GlassMenuScope.GlassMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val index = nextItemIndex++
    itemActions[index] = onClick
    itemEnabled[index] = enabled
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                containerCoordinates?.let { container ->
                    val topLeft = container.localPositionOf(coordinates, Offset.Zero)
                    itemBounds[index] = Rect(topLeft, coordinates.size.toSize())
                }
            }
            .heightIn(min = 44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.padding(end = 12.dp)) { leadingIcon() }
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                text()
            }
        }
        if (trailingIcon != null) {
            Box(Modifier.padding(start = 12.dp)) { trailingIcon() }
        }
    }
}

/**
 * Frosted glass menu body with the elastic selection pill. Place inside any
 * popup window (DropdownMenu, Popup, dialog). Wrap [content] in your own Column
 * if the list needs scrolling; item bounds are tracked relative to this surface.
 */
@Composable
fun GlassMenuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable GlassMenuScope.() -> Unit,
) {
    val scope = remember { GlassMenuScope() }
    scope.nextItemIndex = 0
    val tints = rememberGlassTints()
    Box(
        modifier
            // Popup windows live in their own window and cannot sample the
            // activity's recorded backdrop across the window boundary (the
            // inherited LocalGlassBackdrop is non-null but RenderEffect can't
            // reach another window's pixels), so sampling silently yields an
            // empty/transparent surface. Use the frosted treatment instead: a
            // strong translucent fill + top sheen, plus a directional glassEdge
            // highlight for the refraction look. This matches AGENTS.md, which
            // prescribes glassFrosted for popup-window surfaces.
            .glassFrosted(shape, tint = tints.veil, borderColor = Color.Transparent)
            .glassEdge(shape)
            .drawBehind {
                // Faint Gemini color wash so popup menus carry the same gradient
                // language as the aurora background.
                drawRect(
                    Brush.linearGradient(
                        0f to GeminiColors.Blue.copy(alpha = 0.05f),
                        0.5f to GeminiColors.Violet.copy(alpha = 0.05f),
                        1f to GeminiColors.Pink.copy(alpha = 0.05f),
                    ),
                )
            }
            .padding(vertical = 6.dp)
            .onGloballyPositioned { scope.containerCoordinates = it },
    ) {
        GlassSelectionPill(scope)
        content(scope)
    }
}

@Composable
private fun GlassSelectionPill(scope: GlassMenuScope) {
    val itemCount = scope.itemBounds.size
    if (itemCount == 0) return
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val drag = remember(coroutineScope, itemCount) {
        DampedDragAnimation(
            animationScope = coroutineScope,
            initialValue = 0f,
            valueRange = 0f..(itemCount - 1).coerceAtLeast(1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.04f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }
    var dragged by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(scope, itemCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val downIndex = itemIndexAt(scope, down.position)
                    dragged = false
                    if (downIndex >= 0) {
                        drag.press()
                        drag.updateValue(downIndex.toFloat())
                    }
                    var pointer = down.id
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointer } ?: break
                        if (change.changedToUpIgnoreConsumed() || !change.pressed) break
                        if (abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
                            dragged = true
                        }
                        if (dragged) {
                            val index = itemIndexAt(scope, change.position)
                            if (index >= 0) drag.updateValue(index.toFloat())
                        }
                        pointer = change.id
                    }
                    drag.release()
                    if (dragged && downIndex >= 0) {
                        val releaseIndex =
                            drag.targetValue.roundToInt().fastCoerceIn(0, itemCount - 1)
                        if (releaseIndex != downIndex && scope.itemEnabled[releaseIndex] != false) {
                            scope.itemActions[releaseIndex]?.invoke()
                        }
                    }
                    dragged = false
                }
            },
    ) {
        val press = drag.pressProgress
        if (press <= 0f) return@Box
        val value = drag.value.fastCoerceIn(0f, (itemCount - 1).toFloat())
        val lower = floor(value).toInt().fastCoerceIn(0, itemCount - 1)
        val upper = (lower + 1).fastCoerceIn(0, itemCount - 1)
        val fraction = value - lower
        val lowerBounds = scope.itemBounds[lower] ?: return@Box
        val upperBounds = scope.itemBounds[upper] ?: lowerBounds
        val centerY = lerp(lowerBounds.center.y, upperBounds.center.y, fraction)
        val pillHeightPx = with(density) {
            lerp(lowerBounds.height, upperBounds.height, fraction).toDp()
        } - 6.dp
        // Capsule like the Backdrop catalog's LiquidSlider thumb.
        val pillShape = RoundedCornerShape(percent = 50)
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(pillHeightPx)
                .graphicsLayer {
                    translationY = centerY - size.height / 2f
                    scaleX = drag.scaleX
                    scaleY = drag.scaleY
                    alpha = press
                }
                .glassFrosted(
                    pillShape,
                    tint = if (dark) {
                        Color.White.copy(alpha = lerp(0.10f, 0.22f, press))
                    } else {
                        Color.White.copy(alpha = lerp(0.55f, 0.88f, press))
                    },
                    borderColor = Color.Transparent,
                )
                .glassEdge(
                    pillShape,
                    peakAlpha = lerp(0.35f, 0.90f, press),
                    tailAlpha = 0.05f,
                ),
        )
    }
}

private fun itemIndexAt(scope: GlassMenuScope, position: Offset): Int {
    var best = -1
    var bestDistance = Float.MAX_VALUE
    for ((index, bounds) in scope.itemBounds) {
        if (position.y in bounds.top..bounds.bottom) return index
        val distance = minOf(abs(position.y - bounds.top), abs(position.y - bounds.bottom))
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

/**
 * Drop-in replacement for [DropdownMenu] with the liquid-glass treatment:
 * frosted panel without the gray elevation ring, plus the elastic selection pill.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    width: Dp? = null,
    content: @Composable GlassMenuScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier),
    ) {
        GlassMenuSurface(shape = shape, content = content)
    }
}
