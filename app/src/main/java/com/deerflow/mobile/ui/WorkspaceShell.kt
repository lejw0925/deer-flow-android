@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.ui.glass.GeminiAuroraBackground
import com.deerflow.mobile.ui.glass.GlassMenuOverlayHost
import com.deerflow.mobile.ui.glass.GlassSnackbarHost
import com.deerflow.mobile.ui.glass.LocalGlassBackdrop
import com.deerflow.mobile.ui.glass.LocalGlassMenuHost
import com.deerflow.mobile.ui.glass.rememberGlassBackdrop
import com.deerflow.mobile.ui.glass.rememberGlassMenuHostState
import com.deerflow.mobile.ui.glass.rememberGlassTints
import com.deerflow.mobile.ui.glass.glass
import com.deerflow.mobile.ui.glass.glassEdge
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DRAWER_NAVIGATION_LEAD_MILLIS = 72L

/**
 * Blur + edge tuning shared by the drawer panel and its bottom bar. Kept local to the
 * drawer so the global [com.deerflow.mobile.ui.glass.GlassTunables] (2.dp) keeps tuning the
 * small interactive glass elements; the drawer is a large see-through surface that wants a
 * heavier frost + a stronger directional edge for the "glass edge refraction" gleam.
 */
internal val DrawerGlassBlurRadius: Dp = 6.dp
internal val DrawerGlassEdgePeakAlpha: Float = 0.40f

/**
 * See-through tint for the drawer panel: a low-alpha frosted veil resolved per theme from
 * the glass tint tokens, so the live content recorded behind the drawer reads through —
 * clearly but frosted. Well below [com.deerflow.mobile.ui.glass.GlassTints.veil] (0.68/0.44),
 * which is too opaque to see content through.
 */
@Composable
private fun rememberDrawerGlassTint(): Color {
    val veil = rememberGlassTints().veil
    return if (veil.luminance() < 0.5f) veil.copy(alpha = 0.28f) else veil.copy(alpha = 0.20f)
}

@Composable
fun WorkspaceShell(state: AppUiState, viewModel: AppViewModel, snackbar: SnackbarHostState) {
    val backdrop = rememberGlassBackdrop()
    val menuHost = rememberGlassMenuHostState()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassMenuHost provides menuHost) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val expanded = maxWidth >= 840.dp
            if (expanded) {
                ExpandedWorkspace(state, viewModel, snackbar, backdrop)
            } else {
                CompactWorkspace(state, viewModel, snackbar, drawerWidth = maxWidth * 0.8f, backdrop = backdrop)
            }
            // Drawer context menus (and any shell-level popup) render here with
            // real backdrop glass; screens with their own backdrop override the host.
            GlassMenuOverlayHost(menuHost, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CompactWorkspace(
    state: AppUiState,
    viewModel: AppViewModel,
    snackbar: SnackbarHostState,
    drawerWidth: Dp,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showSkills by remember { mutableStateOf(false) }
    var drawerNavigationInProgress by remember { mutableStateOf(false) }

    fun navigateWithDrawerExit(overlapDrawerExit: Boolean = true, destination: () -> Unit) {
        if (drawerNavigationInProgress) return

        drawerNavigationInProgress = true
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        scope.launch {
            try {
                val drawerCloseJob = launch { drawerState.close() }
                if (overlapDrawerExit) {
                    delay(DRAWER_NAVIGATION_LEAD_MILLIS)
                } else {
                    drawerCloseJob.join()
                }
                destination()
                drawerCloseJob.join()
            } finally {
                drawerNavigationInProgress = false
            }
        }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ModalDrawerSheet(
                modifier = Modifier
                    .width(drawerWidth)
                    .glass(
                        shape = drawerShape,
                        backdrop = backdrop,
                        tint = rememberDrawerGlassTint(),
                        useLens = true,
                        blurRadius = DrawerGlassBlurRadius,
                        highlight = { Highlight.Ambient },
                    )
                    .glassEdge(drawerShape, peakAlpha = DrawerGlassEdgePeakAlpha),
                drawerContainerColor = Color.Transparent,
                drawerTonalElevation = 0.dp,
            ) {
                WorkspaceDrawer(
                    state = state,
                    onNewChat = {
                        navigateWithDrawerExit(destination = viewModel::createThread)
                    },
                    onOpenThread = {
                        navigateWithDrawerExit { viewModel.openThread(it) }
                    },
                    onRenameThread = viewModel::renameThread,
                    onDeleteThread = viewModel::deleteThread,
                    onPinThread = viewModel::toggleThreadPinned,
                    onRefreshThreads = viewModel::refreshThreads,
                    onOpenProfile = {
                        navigateWithDrawerExit {
                            viewModel.openWorkspaceChild(AppRoute.Profile)
                        }
                    },
                    drawerOpen = drawerState.currentValue == DrawerValue.Open,
                    onDestination = { destination ->
                        when (destination) {
                            DrawerDestination.Agents -> navigateWithDrawerExit {
                                viewModel.openWorkspaceChild(AppRoute.Agents)
                            }
                            DrawerDestination.Tasks -> navigateWithDrawerExit {
                                viewModel.openWorkspaceChild(AppRoute.Tasks)
                            }
                            DrawerDestination.Skills -> navigateWithDrawerExit(overlapDrawerExit = false) {
                                showSkills = true
                                viewModel.refreshMcpConfig()
                                viewModel.refreshMcpTools()
                            }
                            DrawerDestination.Memory -> navigateWithDrawerExit {
                                viewModel.openWorkspaceChild(AppRoute.Memory)
                            }
                            DrawerDestination.NewConversation -> navigateWithDrawerExit {
                                viewModel.closeConversation()
                            }
                            else -> Unit
                        }
                    },
                    backdrop = backdrop,
                )
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            snackbarHost = { GlassSnackbarHost(snackbar) },
        ) { padding ->
            // Record aurora AND the live page into the backdrop. The drawer is a sibling
            // OUTSIDE this box (ModalDrawerSheet overlays the content), so it samples the
            // real content behind it — not just the static gradient. Aurora stays the first
            // child (placement invariant) so glass overlays sample color, not the dead
            // background. Per-screen backdrops (ChatScreen etc.) remain nested inside; they
            // are different LayerBackdrop instances, so this is not a same-chain re-record.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                GeminiAuroraBackground(Modifier.fillMaxSize())
                WorkspacePage(
                    state,
                    viewModel,
                    onOpenDrawer = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        scope.launch { drawerState.open() }
                    },
                    contentPadding = padding,
                )
            }
        }
    }
    if (showSkills) SkillsSheet(state, viewModel, onDismiss = { showSkills = false })
}

@Composable
private fun ExpandedWorkspace(
    state: AppUiState,
    viewModel: AppViewModel,
    snackbar: SnackbarHostState,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
) {
    var showSkills by remember { mutableStateOf(false) }
    val drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    Box(Modifier.fillMaxSize()) {
        // The side panel sits at the screen's left edge with the chat to its RIGHT (not
        // behind it), so recording the page would not reveal it through the panel — it would
        // only add per-frame cost during chat scroll. Record aurora-only; the side panel's
        // see-through comes from the glass treatment below over the aurora.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            GeminiAuroraBackground(Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(320.dp)
                    .glass(
                        shape = drawerShape,
                        backdrop = backdrop,
                        tint = rememberDrawerGlassTint(),
                        useLens = true,
                        blurRadius = DrawerGlassBlurRadius,
                        highlight = { Highlight.Ambient },
                    )
                    .glassEdge(drawerShape, peakAlpha = DrawerGlassEdgePeakAlpha),
            ) {
                WorkspaceDrawer(
                    state = state,
                    onNewChat = viewModel::createThread,
                    onOpenThread = viewModel::openThread,
                    onRenameThread = viewModel::renameThread,
                    onDeleteThread = viewModel::deleteThread,
                    onPinThread = viewModel::toggleThreadPinned,
                    onRefreshThreads = viewModel::refreshThreads,
                    onOpenProfile = { viewModel.openWorkspaceChild(AppRoute.Profile) },
                    onDestination = { destination ->
                        when (destination) {
                            DrawerDestination.Agents -> viewModel.openWorkspaceChild(AppRoute.Agents)
                            DrawerDestination.Tasks -> viewModel.openWorkspaceChild(AppRoute.Tasks)
                            DrawerDestination.Skills -> {
                                showSkills = true
                                viewModel.refreshMcpConfig()
                                viewModel.refreshMcpTools()
                            }
                            DrawerDestination.Memory -> viewModel.openWorkspaceChild(AppRoute.Memory)
                            else -> Unit
                        }
                    },
                    backdrop = backdrop,
                )
            }
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = Color.Transparent,
                snackbarHost = { GlassSnackbarHost(snackbar) },
            ) { padding ->
                WorkspacePage(state, viewModel, onOpenDrawer = {}, contentPadding = padding)
            }
        }
    }
    if (showSkills) SkillsSheet(state, viewModel, onDismiss = { showSkills = false })
}

@Composable
private fun WorkspacePage(
    state: AppUiState,
    viewModel: AppViewModel,
    onOpenDrawer: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val routeSnapshots = remember { mutableStateMapOf<AppRoute, AppUiState>() }
    val targetRoute = state.workspacePageRoute()
    SideEffect {
        if (routeSnapshots[targetRoute] != state) routeSnapshots[targetRoute] = state
    }
    AnimatedContent(
        targetState = targetRoute,
        transitionSpec = {
            when {
                initialState == AppRoute.Workspace && targetState == AppRoute.Conversation -> {
                    (
                        fadeIn(initialAlpha = 0.4f, animationSpec = tween(durationMillis = 210)) +
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 240),
                                initialOffsetX = { width -> width / 6 },
                            )
                    ).togetherWith(
                        fadeOut(animationSpec = tween(durationMillis = 120)) +
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 180),
                                targetOffsetX = { width -> -width / 12 },
                            ),
                    )
                }
                initialState == AppRoute.Conversation && targetState == AppRoute.Workspace -> {
                    (
                        fadeIn(initialAlpha = 0.4f, animationSpec = tween(durationMillis = 210)) +
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 240),
                                initialOffsetX = { width -> -width / 12 },
                            )
                    ).togetherWith(
                        fadeOut(animationSpec = tween(durationMillis = 120)) +
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 180),
                                targetOffsetX = { width -> width / 6 },
                            ),
                    )
                }
                else -> {
                    val enteringDrawerDestination = targetState != AppRoute.Workspace
                    (
                        fadeIn(initialAlpha = 0.4f, animationSpec = tween(durationMillis = 210)) +
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 240),
                                initialOffsetX = { width -> if (enteringDrawerDestination) width / 6 else -width / 12 },
                            )
                    ).togetherWith(
                        fadeOut(animationSpec = tween(durationMillis = 120)) +
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 180),
                                targetOffsetX = { width -> if (enteringDrawerDestination) -width / 12 else width / 12 },
                            ),
                    )
                }
            }
        },
        contentKey = { it },
        label = "workspace-child-hero",
    ) { route ->
        Box(Modifier.fillMaxSize()) {
            when (route) {
                AppRoute.Workspace, AppRoute.Conversation -> {
                    val routeState = if (route == targetRoute) state else routeSnapshots[route] ?: state
                    ChatScreen(routeState.copy(route = state.route), viewModel, onOpenDrawer, contentPadding)
                }
                AppRoute.Agents -> AgentsScreen(state, viewModel, viewModel::closeWorkspaceChild, contentPadding)
                AppRoute.Tasks -> TasksScreen(state, viewModel, viewModel::closeWorkspaceChild, contentPadding)
                AppRoute.Memory -> MemoryScreen(
                    state = state,
                    onBack = viewModel::closeWorkspaceChild,
                    contentPadding = contentPadding,
                    onRefresh = viewModel::refreshMemory,
                    onSaveFact = viewModel::saveMemoryFact,
                    onDeleteFact = viewModel::deleteMemoryFact,
                    onClearMemory = viewModel::clearMemory,
                )
                AppRoute.Profile -> ProfileScreen(state, viewModel, viewModel::closeWorkspaceChild, contentPadding)
            }
        }
    }
}
