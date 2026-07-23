package com.deerflow.mobile.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deerflow.mobile.MainActivity
import com.deerflow.mobile.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLocaleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun restoreSystemLocale() {
        setApplicationLocales("")
    }

    @Test
    fun selectedLocaleUpdatesResourcesAndSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            setApplicationLocales("zh-CN")
            scenario.onActivity { activity ->
                assertEquals("我的", activity.getString(R.string.profile_title))
                assertEquals("在网络上搜索 “DeerFlow”", activity.getString(R.string.tool_search_on_web_for, "DeerFlow"))
                assertEquals("使用 “bash” 工具", activity.getString(R.string.tool_use, "bash"))
                assertEquals("读取技能：deep-research", activity.getString(R.string.tool_read_named_skill, "deep-research"))
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("我的", activity.getString(R.string.profile_title))
                assertTrue(AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("zh"))
            }

            setApplicationLocales("en")
            scenario.onActivity { activity ->
                assertEquals("Me", activity.getString(R.string.profile_title))
                assertEquals("Search on the web for “DeerFlow”", activity.getString(R.string.tool_search_on_web_for, "DeerFlow"))
                assertEquals("Use “bash” tool", activity.getString(R.string.tool_use, "bash"))
                assertEquals("Read skill: deep-research", activity.getString(R.string.tool_read_named_skill, "deep-research"))
            }
            setApplicationLocales("")
        }
    }

    private fun setApplicationLocales(languageTags: String) {
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
        }
        instrumentation.waitForIdleSync()
    }
}
