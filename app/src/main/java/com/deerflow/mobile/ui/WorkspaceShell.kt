@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.ui.glass.GeminiAuroraBackground
import com.deerflow.mobile.ui.glass.GlassSnackbarHost
import com.deerflow.mobile.ui.glass.LocalGlassBackdrop
import com.deerflow.mobile.ui.glass.rememberGlassBackdrop
import com.deerflow.mobile.ui.glass.rememberGlassTints
import com.deerflow.mobile.ui.glass.glass
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DRAWER_NAVIGATION_LEAD_MILLIS = 72L

@Composable
fun WorkspaceShell(state: AppUiState, viewModel: AppViewModel, snackbar: SnackbarHostState) {
    val backdrop = rememberGlassBackdrop()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val expanded = maxWidth >= 840.dp
            if (expanded) {
                ExpandedWorkspace(state, viewModel, snackbar, backdrop)
            } else {
                CompactWorkspace(state, viewModel, snackbar, drawerWidth = maxWidth * 0.8f, backdrop = backdrop)
            }
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
            ModalDrawerSheet(
                modifier = Modifier
                    .width(drawerWidth)
                    .glass(
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        tint = rememberGlassTints().veil,
                    ),
                drawerContainerColor = Color.Transparent,
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
                )
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            snackbarHost = { GlassSnackbarHost(snackbar) },
        ) { padding ->
            // Record ONLY the aurora into the backdrop. Glass elements must be
            // siblings of this box, never children: self-sampling smears.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                GeminiAuroraBackground(Modifier.fillMaxSize())
            }
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
    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The side panel sits beside the recorded layer, so there is nothing to
        // refract behind it; a frosted fill keeps the glass language without
        // self-sampling artifacts.
        Box(Modifier.width(320.dp).background(rememberGlassTints().frosted)) {
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
            )
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            snackbarHost = { GlassSnackbarHost(snackbar) },
        ) { padding ->
            // Aurora-only recording; the page (with glass chrome) is a sibling.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                GeminiAuroraBackground(Modifier.fillMaxSize())
            }
            WorkspacePage(state, viewModel, onOpenDrawer = {}, contentPadding = padding)
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
