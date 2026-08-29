package com.deerflow.mobile.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import androidx.compose.ui.util.lerp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import com.kyant.backdrop.drawBackdrop
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.ui.theme.ExpressiveMotion
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * Liquid-glass replacements for the Material 3 chrome used across the app.
 * All of them degrade to translucent frosted surfaces when backdrop sampling is
 * unavailable (see [glass]).
 *
 * Interactive elements (buttons, chips) carry the full liquid treatment from
 * the Backdrop catalog: ambient edge highlight, soft drop shadow, press-driven
 * inner shadow, and a radial glow that follows the finger.
 *
 * Screen headers use the floating style ([com.deerflow.mobile.ui.FloatingScreenTopBar]
 * + [GlassIconButton]) — no full-width glass bar.
 */

/** Soft shadow lifting floating glass off the recorded content. */
internal fun glassShadow(): Shadow = Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.08f))

/**
 * iOS-style floating liquid-glass tab bar, ported from Kyant0's catalog
 * `LiquidBottomTabs` (three-layer structure):
 *  1. base glass capsule (vibrancy + blur 8dp + lens 24dp, 40% container);
 *  2. an invisible row recorded into [tabsBackdrop] with the content tinted
 *     to the accent color;
 *  3. the selection bubble samples `rememberCombinedBackdrop(page, tabs)` so
 *     the selected tab's content is magnified/refracted inside it, tinted,
 *     with chromatic aberration — the iOS look.
 * Drag the bubble or tap an item. Screens anchor it to the bottom edge and
 * pad their scroll content clear of its 64dp height.
 */
@Composable
fun GlassFloatingTabBar(
    icons: List<ImageVector>,
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemTag: ((Int) -> String)? = null,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)
    // Sampling needs some backdrop even in test compositions that provide none;
    // an empty canvas simply renders no sampled content behind the glass.
    val backdrop = LocalGlassBackdrop.current ?: rememberCanvasBackdrop { }

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabs.size
        }
        val tabWidthDp = with(density) { tabWidth.toDp() }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * kotlin.math.sign(fraction) * EaseOut.transform(kotlin.math.abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selected) }
        LaunchedEffect(selected) {
            snapshotFlow { selected }.collectLatest { index -> currentIndex = index }
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selected.toFloat(),
                valueRange = 0f..(tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabs.size - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabs.size - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    // The bubble follows the damped animation; without this the
                    // tap only flips state and the lens capsule never moves.
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onSelect(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        @Composable
        fun TabItem(index: Int, accentTinted: Boolean) {
            val contentColor = if (accentTinted || index == selected) {
                accentColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val tintModifier = if (accentTinted) {
                Modifier.graphicsLayer(colorFilter = ColorFilter.tint(accentColor))
            } else {
                Modifier
            }
            // The accent layer is pure render (reference design): interactive
            // elements live only on the base bar, or the invisible row would
            // swallow every tap.
            Column(
                Modifier
                    .width(tabWidthDp)
                    .fillMaxHeight()
                    .then(tintModifier)
                    .then(
                        if (accentTinted) {
                            Modifier
                        } else {
                            Modifier
                                .then(itemTag?.let { Modifier.testTag(it(index)) } ?: Modifier)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { currentIndex = index },
                                    role = Role.Tab,
                                )
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icons[index],
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tabs[index],
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.indices.forEach { index -> TabItem(index, accentTinted = false) }
        }

        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                    },
                    highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(56f.dp)
                .fillMaxWidth()
                .padding(horizontal = 4f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.indices.forEach { index -> TabItem(index, accentTinted = true) }
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                    shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.1f)
                            else Color.White.copy(alpha = 0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabs.size),
        )
    }
}


/**
 * Modal bottom sheet whose body is a real glass panel sampling the screen's
 * recorded backdrop. Unlike M3's ModalBottomSheet this renders IN the activity
 * composition (a dialog window could never sample the backdrop), so screens
 * gate it with `if (show)`: composition removal is the dismissal — the entry
 * animates up, the exit is instant, and scrim taps / system back dismiss.
 */
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val sheetTint = tint ?: rememberGlassTints().veil
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    BackHandler { onDismissRequest() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = appear.value }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .graphicsLayer { translationY = (1f - appear.value) * size.height }
                .glass(shape = sheetShape, tint = sheetTint, useLens = true)
                .glassEdge(sheetShape),
            content = content,
        )
    }
}

/** Alert dialog with a glass panel instead of an opaque surface. */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        // Dialogs live in their own window; frosted + edge like popup menus.
        modifier = modifier
            .glassFrosted(shape = shape, borderColor = Color.Transparent)
            .glassEdge(shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    )
}

/** Primary action button rendered as an interactive glass capsule. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = Color.Transparent,
    content: @Composable RowScope.() -> Unit,
) {
    val progress = rememberGlassPressProgress()
    val scope = rememberCoroutineScope()
    val glow = remember(scope) { InteractiveHighlight(scope) }
    Row(
        modifier
            .glass(
                shape = CircleShape,
                tint = tint,
                useLens = true,
                shadow = { glassShadow() },
                innerShadow = { InnerShadow(radius = 4.dp * progress.value, alpha = progress.value) },
                highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * progress.value) },
                layerBlock = glassPressLayerBlock { progress.value },
            )
            .glassEdge(CircleShape)
            .then(glow.modifier)
            .then(glow.gestureModifier)
            .glassPressGestures(progress)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** Circular glass icon button with press-to-scale and finger-following glow. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val progress = rememberGlassPressProgress()
    val scope = rememberCoroutineScope()
    val glow = remember(scope) { InteractiveHighlight(scope) }
    Box(
        modifier
            .size(48.dp)
            .glass(
                shape = CircleShape,
                tint = tint,
                useLens = true,
                chromaticAberration = progress.value > 0.01f,
                shadow = { glassShadow() },
                innerShadow = { InnerShadow(radius = 4.dp * progress.value, alpha = progress.value) },
                highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * progress.value) },
                layerBlock = glassPressLayerBlock { progress.value },
            )
            .glassEdge(CircleShape)
            .then(glow.modifier)
            .then(glow.gestureModifier)
            .glassPressGestures(progress)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Small glass pill for quick actions, with the interactive liquid treatment. */
@Composable
fun GlassChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = Color.Transparent,
    leadingIcon: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    val progress = rememberGlassPressProgress()
    val scope = rememberCoroutineScope()
    val glow = remember(scope) { InteractiveHighlight(scope) }
    Row(
        modifier
            .glass(
                shape = CircleShape,
                tint = tint,
                useLens = true,
                shadow = { glassShadow() },
                innerShadow = { InnerShadow(radius = 3.dp * progress.value, alpha = progress.value) },
                highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * progress.value) },
                layerBlock = glassPressLayerBlock { progress.value },
            )
            .glassEdge(CircleShape)
            .then(glow.modifier)
            .then(glow.gestureModifier)
            .glassPressGestures(progress)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.invoke()
        label()
    }
}

/** Glass container for floating cards (welcome suggestions, summaries). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassModifier = modifier
        .glass(
            shape = shape,
            useLens = true,
            shadow = { glassShadow() },
            highlight = { Highlight.Ambient },
        )
        .glassEdge(shape)
    Column(
        if (onClick != null) {
            glassModifier.clickable(onClick = onClick, role = Role.Button)
        } else {
            glassModifier
        },
        content = content,
    )
}

/** Snackbar host that renders notifications on glass. */
@Composable
fun GlassSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data: SnackbarData ->
        val shape = RoundedCornerShape(16.dp)
        Snackbar(
            snackbarData = data,
            modifier = Modifier
                .glass(shape = shape, tint = rememberGlassTints().veil, useLens = true)
                .glassEdge(shape),
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}
