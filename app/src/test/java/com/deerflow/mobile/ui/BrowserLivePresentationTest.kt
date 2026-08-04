package com.deerflow.mobile.ui

import com.deerflow.mobile.R
import com.deerflow.mobile.data.BrowserLiveEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserLivePresentationTest {
    @Test
    fun `protocol error details are mapped to localized UI resources`() {
        assertEquals(
            R.string.browser_live_navigation_rejected,
            browserLiveErrorMessageResource(
                BrowserLiveEvent.NavigationRejected("http://127.0.0.1", "Blocked by policy"),
            ),
        )
        assertEquals(
            R.string.browser_live_connection_failed,
            browserLiveErrorMessageResource(BrowserLiveEvent.Failure("Connection reset by peer")),
        )
        assertEquals(
            R.string.browser_live_disconnected,
            browserLiveErrorMessageResource(BrowserLiveEvent.Closed(1006, "Abnormal closure")),
        )
        assertEquals(
            R.string.browser_live_unauthenticated,
            browserLiveErrorMessageResource(BrowserLiveEvent.Closed(4401, "Unauthorized")),
        )
    }

    @Test
    fun `normal browser events do not produce error messages`() {
        assertNull(browserLiveErrorMessageResource(BrowserLiveEvent.Opened))
        assertNull(browserLiveErrorMessageResource(BrowserLiveEvent.Url("https://example.com")))
    }
}
