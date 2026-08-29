package com.deerflow.mobile.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
