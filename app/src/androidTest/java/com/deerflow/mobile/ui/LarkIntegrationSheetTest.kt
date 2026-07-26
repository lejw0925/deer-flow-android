package com.deerflow.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.deerflow.mobile.data.LarkAuthProbe
import com.deerflow.mobile.data.LarkCliProbe
import com.deerflow.mobile.data.LarkIntegrationStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LarkIntegrationSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun adminCanStartASeparateSkillPackInstallation() {
        var installs = 0
        compose.setContent {
            MaterialTheme {
                LarkIntegrationContent(
                    status = LarkIntegrationStatus(
                        installed = false,
                        version = "",
                        latestAvailableVersion = null,
                        runtimeVersionMismatch = false,
                        appConfigured = false,
                        appId = null,
                        appBrand = null,
                        skillsExpected = 0,
                        skillsInstalled = 0,
                        enabledSkills = emptyList(),
                        cli = LarkCliProbe(available = true, version = "1.2.3", error = null),
                        auth = LarkAuthProbe("not_configured", null, null, false),
                        sandboxRuntimeReady = true,
                        sandboxRuntimeDetail = null,
                    ),
                    loading = false,
                    busy = false,
                    isAdmin = true,
                    verification = null,
                    error = null,
                    onRefresh = {},
                    onInstall = { installs += 1 },
                    onStartConfiguration = {},
                    onCompleteConfiguration = {},
                    onStartAuthorization = {},
                    onCompleteAuthorization = {},
                    onOpenUrl = {},
                )
            }
        }

        compose.onNodeWithTag(UiTags.LarkInstall).assertExists().performClick()
        compose.runOnIdle { assertEquals(1, installs) }
    }
}
