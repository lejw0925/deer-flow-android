@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deerflow.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.R
import com.deerflow.mobile.data.LarkIntegrationStatus
import com.deerflow.mobile.data.LarkVerification
import com.deerflow.mobile.data.LarkVerificationKind
import com.deerflow.mobile.ui.glass.GlassModalBottomSheet

@Composable
fun LarkIntegrationSheet(
    state: AppUiState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    GlassModalBottomSheet(
        onDismissRequest = {
            viewModel.clearLarkVerification()
            onDismiss()
        },
        modifier = Modifier.testTag(UiTags.LarkIntegrationSheet),
    ) {
        LarkIntegrationContent(
            status = state.larkIntegration,
            loading = state.loadingLarkIntegration,
            busy = state.larkIntegrationBusy,
            isAdmin = state.user?.role == "admin",
            verification = state.larkVerification,
            error = state.larkIntegrationError,
            onRefresh = viewModel::refreshLarkIntegration,
            onInstall = viewModel::installLarkIntegration,
            onStartConfiguration = viewModel::startLarkConfiguration,
            onCompleteConfiguration = viewModel::completeLarkConfiguration,
            onStartAuthorization = viewModel::startLarkAuthorization,
            onCompleteAuthorization = viewModel::completeLarkAuthorization,
            onOpenUrl = uriHandler::openUri,
        )
    }
}

@Composable
internal fun LarkIntegrationContent(
    status: LarkIntegrationStatus?,
    loading: Boolean,
    busy: Boolean,
    isAdmin: Boolean,
    verification: LarkVerification?,
    error: String?,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onStartConfiguration: (String) -> Unit,
    onCompleteConfiguration: () -> Unit,
    onStartAuthorization: () -> Unit,
    onCompleteAuthorization: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var brand by rememberSaveable { mutableStateOf("feishu") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 660.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.lark_integration),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onRefresh,
                enabled = !loading && !busy,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.run_details_reload))
            }
        }
        when {
            loading && status == null -> Text(
                stringResource(R.string.loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )

            status == null -> Text(
                error ?: stringResource(R.string.lark_cli_unavailable),
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            else -> {
                LarkStatusContent(status)
                error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                when {
                    !status.cli.available -> Text(
                        status.cli.error ?: stringResource(R.string.lark_cli_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    !status.installed -> {
                        if (isAdmin) {
                            Button(
                                onClick = onInstall,
                                enabled = !busy,
                                modifier = Modifier.testTag(UiTags.LarkInstall),
                            ) { Text(stringResource(R.string.lark_install)) }
                        } else {
                            Text(
                                stringResource(R.string.lark_admin_required),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }

                    !status.appConfigured && verification?.kind != LarkVerificationKind.Configuration -> {
                        Text(stringResource(R.string.lark_configuration), style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("feishu", "lark").forEachIndexed { index, value ->
                                SegmentedButton(
                                    selected = brand == value,
                                    onClick = { brand = value },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                                    label = {
                                        Text(
                                            stringResource(
                                                if (value == "feishu") R.string.lark_brand_feishu else R.string.lark_brand_lark,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = { onStartConfiguration(brand) },
                            enabled = !busy,
                            modifier = Modifier.testTag(UiTags.LarkStartConfiguration),
                        ) { Text(stringResource(R.string.lark_start_configuration)) }
                    }

                    status.appConfigured && !status.auth.authenticated && verification?.kind != LarkVerificationKind.Authorization -> {
                        Text(stringResource(R.string.lark_authorization), style = MaterialTheme.typography.titleSmall)
                        Button(
                            onClick = onStartAuthorization,
                            enabled = !busy,
                            modifier = Modifier.testTag(UiTags.LarkStartAuthorization),
                        ) { Text(stringResource(R.string.lark_start_authorization)) }
                    }
                }
                verification?.let { flow ->
                    VerificationActions(
                        flow = flow,
                        busy = busy,
                        onOpenUrl = onOpenUrl,
                        onComplete = if (flow.kind == LarkVerificationKind.Configuration) {
                            onCompleteConfiguration
                        } else {
                            onCompleteAuthorization
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LarkStatusContent(status: LarkIntegrationStatus) {
    ListItem(
        headlineContent = {
            Text(stringResource(if (status.installed) R.string.lark_installed else R.string.lark_not_installed))
        },
        supportingContent = {
            Column {
                status.version.takeIf(String::isNotBlank)?.let { Text(it) }
                Text(
                    stringResource(if (status.appConfigured) R.string.lark_app_configured else R.string.lark_app_not_configured),
                )
                Text(
                    stringResource(if (status.auth.authenticated) R.string.lark_authenticated else R.string.lark_not_authenticated),
                )
                status.auth.user?.takeIf(String::isNotBlank)?.let { Text(it) }
                if (!status.sandboxRuntimeReady) {
                    Text(
                        stringResource(R.string.lark_sandbox_not_ready, status.sandboxRuntimeDetail.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun VerificationActions(
    flow: LarkVerification,
    busy: Boolean,
    onOpenUrl: (String) -> Unit,
    onComplete: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.lark_device_code, flow.deviceCode), style = MaterialTheme.typography.bodySmall)
        flow.userCode?.let { code -> Text(stringResource(R.string.lark_user_code, code), style = MaterialTheme.typography.bodySmall) }
        flow.hint?.takeIf(String::isNotBlank)?.let { hint ->
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onOpenUrl(flow.verificationUrl) },
                enabled = !busy,
                modifier = Modifier.testTag(UiTags.LarkOpenVerification),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.lark_open_verification))
            }
            TextButton(
                onClick = onComplete,
                enabled = !busy,
                modifier = Modifier.testTag(UiTags.LarkCompleteVerification),
            ) {
                Text(
                    stringResource(
                        if (flow.kind == LarkVerificationKind.Configuration) {
                            R.string.lark_complete_configuration
                        } else {
                            R.string.lark_complete_authorization
                        },
                    ),
                )
            }
        }
    }
}
