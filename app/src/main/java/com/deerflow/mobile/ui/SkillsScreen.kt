@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.R
import com.deerflow.mobile.data.SkillInfo
import com.deerflow.mobile.ui.glass.GeminiAuroraBackground
import com.deerflow.mobile.ui.glass.glass
import com.deerflow.mobile.ui.glass.glassEdge
import com.deerflow.mobile.ui.glass.glassShadow
import com.deerflow.mobile.ui.glass.GlassFloatingTabBar
import com.deerflow.mobile.ui.glass.GlassIconButton
import com.deerflow.mobile.ui.glass.LocalGlassBackdrop
import com.deerflow.mobile.ui.glass.glassFrosted
import com.deerflow.mobile.ui.glass.rememberGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.highlight.Highlight

private enum class SkillCatalogTab(val labelRes: Int) {
    Public(R.string.public_skills),
    Custom(R.string.custom_skills),
    Tools(R.string.tools),
}

private val SkillInfo.isCustom: Boolean
    get() = category.contains("custom", ignoreCase = true) || category.contains("user", ignoreCase = true)

/**
 * Skills workspace page (migrated out of the old skills bottom sheet): same
 * glass skeleton as the memory page — aurora recorded into a backdrop, a
 * floating glass header, and the catalog as an overlay. Detail and the MCP
 * JSON editor are in-page sub-views that pop before the screen itself.
 */
@Composable
fun SkillsScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    var detail by remember { mutableStateOf<SkillInfo?>(null) }
    var tab by remember { mutableStateOf(SkillCatalogTab.Public) }
    var editingConfiguration by remember { mutableStateOf(false) }
    val canManageSkillStates = state.user?.role == "admin"

    BackHandler(enabled = detail != null || editingConfiguration) {
        if (editingConfiguration) editingConfiguration = false else detail = null
    }
    LaunchedEffect(Unit) {
        viewModel.refreshMcpConfig()
        viewModel.refreshMcpTools()
    }

    Box(Modifier.fillMaxSize().padding(contentPadding).testTag(UiTags.SkillsSheet)) {
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            var topBarHeightPx by remember { mutableIntStateOf(0) }
            val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                GeminiAuroraBackground(Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().padding(top = topBarHeight)) {
                val selectedDetail = detail?.let { wanted ->
                    state.capabilities.skills.firstOrNull { it.name == wanted.name } ?: wanted
                }
                if (editingConfiguration) {
                    McpConfigEditorContent(
                        config = state.mcpConfig,
                        mutationBusy = state.workspaceMutationBusy,
                        onBack = { editingConfiguration = false },
                        onSave = { rawJson ->
                            viewModel.updateMcpConfiguration(rawJson) { editingConfiguration = false }
                        },
                    )
                } else if (selectedDetail != null) {
                    SkillDetailContent(
                        skill = selectedDetail,
                        onBack = { detail = null },
                        canManageSkillStates = canManageSkillStates,
                        mutationBusy = state.workspaceMutationBusy,
                        onSkillEnabledChanged = viewModel::setSkillEnabled,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        when (tab) {
                            SkillCatalogTab.Public, SkillCatalogTab.Custom -> {
                                val custom = tab == SkillCatalogTab.Custom
                                SkillsPageContent(
                                    skills = state.capabilities.skills.filter { skill -> skill.isCustom == custom },
                                    onSkillDetail = { detail = it },
                                    showDisabledSkills = canManageSkillStates,
                                    canManageSkillStates = canManageSkillStates,
                                    mutationBusy = state.workspaceMutationBusy,
                                    onSkillEnabledChanged = viewModel::setSkillEnabled,
                                    gridHeight = null,
                                )
                            }
                            SkillCatalogTab.Tools -> McpSheetContent(
                                config = state.mcpConfig,
                                loading = state.loadingMcpConfig,
                                mutationBusy = state.workspaceMutationBusy,
                                canManageServers = canManageSkillStates,
                                onServerEnabledChanged = viewModel::setMcpServerEnabled,
                                onEditConfiguration = { editingConfiguration = true },
                                showTitle = false,
                                listHeight = null,
                            )
                        }
                    }
                }
            }
            FloatingScreenTopBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { topBarHeightPx = it.size.height },
                title = stringResource(R.string.skills),
                onBack = onBack,
            )
            if (!editingConfiguration && detail == null) {
                GlassFloatingTabBar(
                    icons = listOf(
                        Icons.Outlined.Public,
                        Icons.Outlined.Tune,
                        Icons.Outlined.Handyman,
                    ),
                    tabs = SkillCatalogTab.entries.map { stringResource(it.labelRes) },
                    selected = tab.ordinal,
                    onSelect = { index -> tab = SkillCatalogTab.entries[index] },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                )
            }
        }
    }
}

/**
 * Search field plus the skills staggered grid. [gridHeight] = null lets the
 * grid fill the remaining page height; a fixed height keeps the old
 * sheet-sized layout (used by tests).
 */
@Composable
internal fun SkillsPageContent(
    skills: List<SkillInfo>,
    onSkillDetail: (SkillInfo) -> Unit = {},
    showDisabledSkills: Boolean = false,
    canManageSkillStates: Boolean = false,
    mutationBusy: Boolean = false,
    onSkillEnabledChanged: (String, Boolean) -> Unit = { _, _ -> },
    gridHeight: androidx.compose.ui.unit.Dp? = 440.dp,
) {
    var query by remember { mutableStateOf("") }
    val visibleSkills = skills.filter { skill ->
        (showDisabledSkills || skill.enabled) && (
            query.isBlank() || listOf(skill.name, skill.description, skill.category)
                .any { value -> value.contains(query, ignoreCase = true) }
        )
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.search_skills)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(UiTags.SkillsSearch),
    )
    if (visibleSkills.isEmpty()) {
        Text(
            stringResource(if (skills.any { it.enabled }) R.string.no_matching_skills else R.string.no_skills_available),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        )
    } else {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(168.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (gridHeight != null) Modifier.height(gridHeight) else Modifier)
                .testTag(UiTags.SkillsGrid),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            staggeredItems(visibleSkills, key = { it.name }) { skill ->
                // Real-glass-capable card: enabled skills get a soft primary veil,
                // disabled ones the neutral frosted tint.
                val cardTint = if (skill.enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    null
                }
                androidx.compose.material3.Surface(
                    onClick = { onSkillDetail(skill) },
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(
                            MaterialTheme.shapes.large,
                            tint = cardTint,
                            useLens = true,
                            shadow = { glassShadow() },
                            highlight = { Highlight.Ambient },
                        )
                        .glassEdge(MaterialTheme.shapes.large)
                        .testTag(UiTags.SkillCardPrefix + skill.name),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(skill.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            skill.description.ifBlank { stringResource(R.string.skill_no_description) },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (canManageSkillStates) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.enable_skill_for_workspace),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = skill.enabled,
                                    onCheckedChange = { onSkillEnabledChanged(skill.name, it) },
                                    enabled = !mutationBusy,
                                    modifier = Modifier.testTag(UiTags.SkillGlobalEnablePrefix + skill.name),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.navigationBarsPadding().height(20.dp))
}

@Composable
internal fun SkillDetailContent(
    skill: SkillInfo,
    onBack: () -> Unit,
    canManageSkillStates: Boolean = false,
    mutationBusy: Boolean = false,
    onSkillEnabledChanged: (String, Boolean) -> Unit = { _, _ -> },
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(UiTags.SkillDetailScreen)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(UiTags.SkillDetailBack)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(skill.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.skill_category), style = MaterialTheme.typography.labelLarge)
            Text(
                skill.category.ifBlank { stringResource(R.string.skill_uncategorized) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.description), style = MaterialTheme.typography.labelLarge)
            Text(
                skill.description.ifBlank { stringResource(R.string.skill_no_description) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        if (canManageSkillStates) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.enable_skill_for_workspace),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = skill.enabled,
                    onCheckedChange = { onSkillEnabledChanged(skill.name, it) },
                    enabled = !mutationBusy,
                    modifier = Modifier.testTag(UiTags.SkillDetailGlobalEnable),
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}
