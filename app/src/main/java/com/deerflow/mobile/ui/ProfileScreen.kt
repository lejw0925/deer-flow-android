@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.BuildConfig
import com.deerflow.mobile.R
import com.deerflow.mobile.data.ArtifactDownloadLimits
import com.deerflow.mobile.data.CacheRetentionPolicy
import com.deerflow.mobile.data.LanguagePreference
import com.deerflow.mobile.data.MAX_ARTIFACT_AUTO_DOWNLOAD_BYTES
import com.deerflow.mobile.data.MAX_ARTIFACT_DOWNLOAD_BYTES
import com.deerflow.mobile.data.MIN_ARTIFACT_AUTO_DOWNLOAD_BYTES
import com.deerflow.mobile.data.MIN_ARTIFACT_MANUAL_DOWNLOAD_BYTES
import com.deerflow.mobile.data.ThemePreference
import com.deerflow.mobile.data.parseThirdPartyLicenseNotices
import com.deerflow.mobile.ui.glass.GeminiAuroraBackground
import com.deerflow.mobile.ui.glass.GlassAlertDialog
import com.deerflow.mobile.ui.glass.LocalGlassBackdrop
import com.deerflow.mobile.ui.glass.rememberGlassBackdrop
import com.deerflow.mobile.ui.glass.glassFrosted
import com.kyant.backdrop.backdrops.layerBackdrop

@Composable
fun ProfileScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var showChannels by remember { mutableStateOf(false) }
    var showLarkIntegration by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshCacheStats() }
    ProfileContent(
        state = state,
        onBack = onBack,
        onSaveServerUrl = viewModel::saveServerUrl,
        onThemeSelected = viewModel::setTheme,
        onLanguageSelected = viewModel::setLanguage,
        onNotifyOnRunCompletionChanged = viewModel::setNotifyOnRunCompletion,
        onCacheRetentionPolicySelected = viewModel::setCacheRetentionPolicy,
        onArtifactDownloadLimitsSelected = viewModel::setArtifactDownloadLimits,
        onRefreshCacheStats = viewModel::refreshCacheStats,
        onClearCache = viewModel::clearCache,
        onSignOut = viewModel::logout,
        onOpenChannels = {
            viewModel.refreshChannels()
            showChannels = true
        },
        onOpenLarkIntegration = {
            viewModel.refreshLarkIntegration()
            showLarkIntegration = true
        },
        onOpenSourceLicenses = {},
        onOpenSourceCode = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_CODE_URL)))
        },
        contentPadding = contentPadding,
    )
    if (showChannels) {
        ChannelsSheet(state, viewModel, onDismiss = { showChannels = false })
    }
    if (showLarkIntegration) {
        LarkIntegrationSheet(state, viewModel, onDismiss = { showLarkIntegration = false })
    }
}

@Composable
internal fun ProfileContent(
    state: AppUiState,
    onBack: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onThemeSelected: (ThemePreference) -> Unit,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onNotifyOnRunCompletionChanged: (Boolean) -> Unit,
    onCacheRetentionPolicySelected: (CacheRetentionPolicy) -> Unit,
    onArtifactDownloadLimitsSelected: (ArtifactDownloadLimits) -> Unit = {},
    onRefreshCacheStats: () -> Unit,
    onClearCache: () -> Unit,
    onSignOut: () -> Unit,
    onOpenChannels: () -> Unit = {},
    onOpenLarkIntegration: () -> Unit = {},
    onOpenSourceLicenses: () -> Unit,
    onOpenSourceCode: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var serverUrl by rememberSaveable(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showCachePolicyDialog by rememberSaveable { mutableStateOf(false) }
    var showClearCacheDialog by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showDeerFlowLicense by rememberSaveable { mutableStateOf(false) }
    var showThirdPartyLicenses by rememberSaveable { mutableStateOf(false) }
    var showServerDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showAbout || showThirdPartyLicenses) {
        if (showThirdPartyLicenses) showThirdPartyLicenses = false else showAbout = false
    }
    if (showThirdPartyLicenses) {
        ThirdPartyLicensesScreen(onBack = { showThirdPartyLicenses = false }, contentPadding = contentPadding)
    } else if (showAbout) {
        AboutScreen(
            onBack = { showAbout = false },
            onOpenDeerFlowLicense = { showDeerFlowLicense = true },
            onOpenSourceLicenses = {
                showThirdPartyLicenses = true
                onOpenSourceLicenses()
            },
            onOpenSourceCode = onOpenSourceCode,
            contentPadding = contentPadding,
        )
    } else {
        // Liquid glass layout: the settings list is recorded into a backdrop and the
        // glass top bar floats above it as a sibling overlay sampling that recording.
        Box(Modifier.fillMaxSize().padding(contentPadding).testTag(UiTags.ProfileScreen)) {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                var topBarHeightPx by remember { mutableIntStateOf(0) }
                val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    // Aurora inside the recorded layer so the floating glass top bar
                    // samples colorful refraction, not the dead solid background.
                    GeminiAuroraBackground(Modifier.fillMaxSize())
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding().testTag(UiTags.ProfileList),
                        contentPadding = PaddingValues(start = 20.dp, top = topBarHeight + 16.dp, end = 20.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            ProfileSection {
                                SettingsSectionTitle(R.string.account)
                                SettingsCard {
                                    ListItem(
                                        headlineContent = { Text(state.user?.email.orEmpty()) },
                                        supportingContent = { Text(state.user?.role.orEmpty()) },
                                        leadingContent = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }
                        item {
                            ProfileSection {
                                SettingsSectionTitle(R.string.connection)
                                SettingsCard {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.server_address)) },
                                        supportingContent = { Text(serverUrl) },
                                        leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth().clickable { showServerDialog = true }.testTag(UiTags.ProfileServer),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                    SettingsRowDivider()
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.channels)) },
                                        leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onOpenChannels)
                                            .testTag(UiTags.ProfileChannels),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                    SettingsRowDivider()
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.lark_integration)) },
                                        supportingContent = { Text(stringResource(R.string.lark_integration_subtitle)) },
                                        leadingContent = { Icon(Icons.Outlined.Extension, contentDescription = null) },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onOpenLarkIntegration)
                                            .testTag(UiTags.ProfileLarkIntegration),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }
                        item {
                            ProfileSection {
                                SettingsSectionTitle(R.string.preferences)
                                SettingsCard {
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                            ThemePreference.entries.forEachIndexed { index, preference ->
                                                SegmentedButton(
                                                    selected = state.theme == preference,
                                                    onClick = { onThemeSelected(preference) },
                                                    shape = SegmentedButtonDefaults.itemShape(index, ThemePreference.entries.size),
                                                    label = { Text(preference.label()) },
                                                    modifier = Modifier.testTag(UiTags.ProfileThemePrefix + preference.name),
                                                )
                                            }
                                        }
                                    }
                                    SettingsRowDivider()
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.language)) },
                                        supportingContent = { Text(state.language.label()) },
                                        leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showLanguageDialog = true }
                                            .testTag(UiTags.ProfileLanguage),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }
                        item {
                            ProfileSection {
                                SettingsSectionTitle(R.string.notifications)
                                SettingsCard {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.run_completion_notifications)) },
                                        supportingContent = { Text(stringResource(R.string.run_completion_notifications_subtitle)) },
                                        leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                                        trailingContent = {
                                            Switch(
                                                checked = state.notifyOnRunCompletion,
                                                onCheckedChange = onNotifyOnRunCompletionChanged,
                                                modifier = Modifier.testTag(UiTags.ProfileNotifications),
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                    SettingsRowDivider()
                                    LiveUpdateSettingsItem()
                                }
                            }
                        }
                        item {
                            ProfileSection {
                                SettingsSectionTitle(R.string.storage)
                                SettingsCard {
                                    ArtifactDownloadLimitSlider(
                                        title = stringResource(R.string.artifact_auto_download_limit),
                                        summary = stringResource(
                                            R.string.artifact_auto_download_limit_summary,
                                            Formatter.formatFileSize(
                                                LocalContext.current,
                                                state.artifactDownloadLimits.autoDownloadBytes,
                                            ),
                                        ),
                                        valueBytes = state.artifactDownloadLimits.autoDownloadBytes,
                                        valueRange = MIN_ARTIFACT_AUTO_DOWNLOAD_BYTES.toFloat()..MAX_ARTIFACT_AUTO_DOWNLOAD_BYTES.toFloat(),
                                        steps = 0,
                                        tag = UiTags.ProfileArtifactAutoDownloadLimit,
                                        onValueChangeFinished = { value ->
                                            onArtifactDownloadLimitsSelected(
                                                ArtifactDownloadLimits(
                                                    autoDownloadBytes = value,
                                                    manualDownloadBytes = state.artifactDownloadLimits.manualDownloadBytes,
                                                ),
                                            )
                                        },
                                    )
                                    SettingsRowDivider()
                                    ArtifactDownloadLimitSlider(
                                        title = stringResource(R.string.artifact_manual_download_limit),
                                        summary = stringResource(
                                            R.string.artifact_manual_download_limit_summary,
                                            Formatter.formatFileSize(
                                                LocalContext.current,
                                                state.artifactDownloadLimits.manualDownloadBytes,
                                            ),
                                        ),
                                        valueBytes = state.artifactDownloadLimits.manualDownloadBytes,
                                        valueRange = MIN_ARTIFACT_MANUAL_DOWNLOAD_BYTES.toFloat()..MAX_ARTIFACT_DOWNLOAD_BYTES.toFloat(),
                                        steps = 0,
                                        tag = UiTags.ProfileArtifactManualDownloadLimit,
                                        onValueChangeFinished = { value ->
                                            onArtifactDownloadLimitsSelected(
                                                ArtifactDownloadLimits(
                                                    autoDownloadBytes = state.artifactDownloadLimits.autoDownloadBytes,
                                                    manualDownloadBytes = value,
                                                ),
                                            )
                                        },
                                    )
                                    SettingsRowDivider()
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.cached_data)) },
                                        supportingContent = {
                                            val size = Formatter.formatShortFileSize(LocalContext.current, state.cacheStats.bytesOnDisk)
                                            Text(
                                                if (state.loadingCacheStats) {
                                                    stringResource(R.string.loading)
                                                } else {
                                                    stringResource(R.string.cache_summary, state.cacheStats.itemCount, size)
                                                },
                                            )
                                        },
                                        leadingContent = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                                        trailingContent = {
                                            IconButton(
                                                onClick = onRefreshCacheStats,
                                                enabled = !state.loadingCacheStats && !state.clearingCache,
                                                modifier = Modifier.size(48.dp).testTag(UiTags.ProfileCacheRefresh),
                                            ) {
                                                if (state.loadingCacheStats) {
                                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                    SettingsRowDivider()
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.cache_retention)) },
                                        supportingContent = { Text(state.cacheRetentionPolicy.label()) },
                                        leadingContent = { Icon(Icons.Outlined.Policy, contentDescription = null) },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showCachePolicyDialog = true }
                                            .testTag(UiTags.ProfileCachePolicy),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                    SettingsRowDivider()
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.clear_cached_data)) },
                                        supportingContent = {
                                            if (state.run.active) Text(stringResource(R.string.clear_cache_run_active))
                                        },
                                        leadingContent = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !state.run.active && !state.clearingCache) {
                                                showClearCacheDialog = true
                                            }
                                            .testTag(UiTags.ProfileCacheClear),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }
                        item {
                            ProfileSection {
                                SettingsSectionTitle(R.string.about_title)
                                SettingsCard {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.about_deerflow)) },
                                        supportingContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) },
                                        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showAbout = true }
                                            .testTag(UiTags.ProfileAbout),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                                OutlinedButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                ) {
                                    Text(stringResource(R.string.sign_out))
                                }
                            }
                        }
                    }
                }
                FloatingScreenTopBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onGloballyPositioned { topBarHeightPx = it.size.height },
                    title = stringResource(R.string.profile_title),
                    onBack = onBack,
                )
            }
        }
    }

    if (showLanguageDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.language),
            values = LanguagePreference.entries,
            selected = state.language,
            label = { it.label() },
            tag = { UiTags.ProfileLanguageOptionPrefix + it.name },
            onSelect = {
                showLanguageDialog = false
                onLanguageSelected(it)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showServerDialog) {
        GlassAlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text(stringResource(R.string.server_address)) },
            text = {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.server_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showServerDialog = false
                    onSaveServerUrl(serverUrl)
                }) { Text(stringResource(R.string.save_reconnect)) }
            },
            dismissButton = { TextButton(onClick = { showServerDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showCachePolicyDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.cache_retention),
            values = CacheRetentionPolicy.entries,
            selected = state.cacheRetentionPolicy,
            label = { it.label() },
            tag = { UiTags.ProfileCachePolicyOptionPrefix + it.name },
            onSelect = {
                showCachePolicyDialog = false
                onCacheRetentionPolicySelected(it)
            },
            onDismiss = { showCachePolicyDialog = false },
        )
    }
    if (showClearCacheDialog) {
        GlassAlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.clear_cache_title)) },
            text = { Text(stringResource(R.string.clear_cache_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        onClearCache()
                    },
                    modifier = Modifier.testTag(UiTags.ProfileCacheClearConfirm),
                ) { Text(stringResource(R.string.clear_cached_data), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showDeerFlowLicense) {
        DeerFlowLicenseDialog(onDismiss = { showDeerFlowLicense = false })
    }
}

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    onOpenDeerFlowLicense: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
    onOpenSourceCode: () -> Unit,
    contentPadding: PaddingValues,
) {
    // Liquid glass layout: the about content is recorded into a backdrop and the
    // glass top bar floats above it as a sibling overlay sampling that recording.
    Box(Modifier.fillMaxSize().padding(contentPadding).testTag(UiTags.AboutScreen)) {
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            var topBarHeightPx by remember { mutableIntStateOf(0) }
            val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // Aurora inside the recorded layer so the floating glass top bar
                // samples colorful refraction, not the dead solid background.
                GeminiAuroraBackground(Modifier.fillMaxSize())
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 20.dp, top = topBarHeight + 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BrandMark()
                                Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.build_number, BuildConfig.VERSION_CODE),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.package_name, BuildConfig.APPLICATION_ID),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            SettingsCard {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.deerflow_license)) },
                                    supportingContent = { Text(stringResource(R.string.mit_license)) },
                                    leadingContent = { Icon(Icons.Outlined.Policy, contentDescription = null) },
                                    trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onOpenDeerFlowLicense)
                                        .testTag(UiTags.AboutDeerFlowLicense),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                                SettingsRowDivider()
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.open_source_licenses)) },
                                    supportingContent = { Text(stringResource(R.string.open_source_licenses_subtitle)) },
                                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                    trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onOpenSourceLicenses)
                                        .testTag(UiTags.AboutOpenSourceLicenses),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                                SettingsRowDivider()
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.source_code)) },
                                    supportingContent = { Text(SOURCE_CODE_URL) },
                                    leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                                    trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onOpenSourceCode)
                                        .testTag(UiTags.AboutSourceCode),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
            FloatingScreenTopBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { topBarHeightPx = it.size.height },
                title = stringResource(R.string.about_title),
                onBack = onBack,
                backModifier = Modifier.testTag(UiTags.AboutBack),
            )
        }
    }
}

@Composable
private fun DeerFlowLicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val license = remember {
        context.resources.openRawResource(R.raw.deerflow_license).bufferedReader().use { it.readText() }
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deerflow_license)) },
        text = {
            Text(
                license,
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        modifier = Modifier.testTag(UiTags.AboutLicenseDialog),
    )
}

@Composable
private fun ThirdPartyLicensesScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val notices = remember {
        runCatching {
            val metadata = context.resources.openRawResource(R.raw.third_party_license_metadata)
                .bufferedReader().use { it.readText() }
            val text = context.resources.openRawResource(R.raw.third_party_licenses)
                .bufferedReader().use { it.readText() }
            parseThirdPartyLicenseNotices(metadata, text)
        }.getOrDefault(emptyList())
    }
    // Liquid glass layout: the license list is recorded into a backdrop and the
    // glass top bar floats above it as a sibling overlay sampling that recording.
    Box(Modifier.fillMaxSize().padding(contentPadding).testTag(UiTags.ThirdPartyLicensesScreen)) {
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            var topBarHeightPx by remember { mutableIntStateOf(0) }
            val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // Aurora inside the recorded layer so the floating glass top bar
                // samples colorful refraction, not the dead solid background.
                GeminiAuroraBackground(Modifier.fillMaxSize())
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 20.dp, top = topBarHeight + 12.dp, end = 20.dp, bottom = 12.dp),
                ) {
                    notices.forEach { notice ->
                        item(key = notice.name) {
                            Text(notice.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                            Text(notice.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            HorizontalDivider(Modifier.padding(top = 12.dp))
                        }
                    }
                    if (notices.isEmpty()) {
                        item { Text(stringResource(R.string.licenses_unavailable)) }
                    }
                }
            }
            FloatingScreenTopBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { topBarHeightPx = it.size.height },
                title = stringResource(R.string.open_source_licenses),
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LiveUpdateSettingsItem() {
    val context = LocalContext.current
    val notifications = context.getSystemService(android.app.NotificationManager::class.java)
    val promotedAvailable = Build.VERSION.SDK_INT >= 36 && notifications.canPostPromotedNotifications()
    val status = if (promotedAvailable) {
        stringResource(R.string.live_updates_available)
    } else {
        stringResource(R.string.live_updates_unavailable)
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.live_updates)) },
        supportingContent = { Text(status) },
        leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                values.forEach { value ->
                    ListItem(
                        headlineContent = { Text(label(value)) },
                        leadingContent = { RadioButton(selected = selected == value, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == value, onClick = { onSelect(value) })
                            .testTag(tag(value)),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ArtifactDownloadLimitSlider(
    title: String,
    summary: String,
    valueBytes: Long,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    tag: String,
    onValueChangeFinished: (Long) -> Unit,
) {
    var sliderValue by remember(valueBytes) { mutableStateOf(valueBytes.toFloat()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        androidx.compose.material3.Slider(
            value = sliderValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { sliderValue = it },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = { onValueChangeFinished(sliderValue.toLong()) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag + "-slider"),
        )
    }
}

@Composable
private fun ProfileSection(content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
        content()
    }
}

@Composable
private fun SettingsSectionTitle(resourceId: Int) {
    Text(
        stringResource(resourceId),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
    )
}

/** Grouped glass settings card: one frosted rounded panel per settings section. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassFrosted(MaterialTheme.shapes.medium),
        content = content,
    )
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ThemePreference.label(): String = when (this) {
    ThemePreference.System -> stringResource(R.string.theme_system)
    ThemePreference.Light -> stringResource(R.string.theme_light)
    ThemePreference.Dark -> stringResource(R.string.theme_dark)
}

@Composable
private fun LanguagePreference.label(): String = when (this) {
    LanguagePreference.System -> stringResource(R.string.language_system)
    LanguagePreference.English -> stringResource(R.string.language_english)
    LanguagePreference.SimplifiedChinese -> stringResource(R.string.language_simplified_chinese)
}

@Composable
private fun CacheRetentionPolicy.label(): String = when (this) {
    CacheRetentionPolicy.KeepUntilCleared -> stringResource(R.string.cache_keep_until_cleared)
    CacheRetentionPolicy.ClearOnSignOut -> stringResource(R.string.cache_clear_on_sign_out)
}

private const val SOURCE_CODE_URL = "https://github.com/lejw0925/deer-flow-android"
