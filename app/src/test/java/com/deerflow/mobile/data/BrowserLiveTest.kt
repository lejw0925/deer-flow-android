package com.deerflow.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLiveTest {
    @Test
    fun buildsWebSocketUrlWithEncodedThreadAndSeed() {
        assertEquals(
            "wss://gateway.example/api/threads/thread%20id/browser/stream?seed=https%3A%2F%2Fexample.com%2Fpath%3Fa%3D1%26b%3D2",
            browserStreamUrl("https://gateway.example", "thread id", "example.com/path?a=1&b=2"),
        )
        assertEquals(
            "ws://10.0.2.2:2027/api/threads/thread-1/browser/stream",
            browserStreamUrl("http://10.0.2.2:2027", "thread-1", "file:///not-supported"),
        )
        assertEquals(
            "https://gateway.example/api/threads/thread-1/browser/stream",
            browserHandshakeCookieUrl("wss://gateway.example/api/threads/thread-1/browser/stream"),
        )
        assertEquals(
            "http://10.0.2.2:2027/api/threads/thread-1/browser/stream",
            browserHandshakeCookieUrl("ws://10.0.2.2:2027/api/threads/thread-1/browser/stream"),
        )
    }

    @Test
    fun parsesValidatedFramesAndBrowserStateEvents() {
        assertEquals(
            BrowserLiveEvent.Frame("aGVsbG8="),
            parseBrowserLiveEvent("""{"type":"frame","data":"aGVsbG8="}"""),
        )
        assertEquals(
            BrowserLiveEvent.Url("https://example.com"),
            parseBrowserLiveEvent("""{"type":"url","url":"https://example.com"}"""),
        )
        assertEquals(
            BrowserLiveEvent.Tabs(
                listOf(
                    BrowserTab(0, "Example", "https://example.com", active = true),
                    BrowserTab(1, "", "https://docs.example.com", active = false),
                ),
            ),
            parseBrowserLiveEvent(
                """{"type":"tabs","tabs":[{"index":0,"title":"Example","url":"https://example.com","active":true},{"index":1,"url":"https://docs.example.com","active":false}]}""",
            ),
        )
        assertEquals(
            BrowserLiveEvent.NavigationRejected("http://127.0.0.1", "Blocked by policy"),
            parseBrowserLiveEvent("""{"type":"nav_rejected","url":"http://127.0.0.1","message":"Blocked by policy"}"""),
        )
        assertNull(parseBrowserLiveEvent("""{"type":"frame","data":"not base64!"}"""))
        assertNull(parseBrowserLiveEvent("not-json"))
    }

    @Test
    fun serializesOnlyBoundedValidBrowserInput() {
        val click = JSONObject(requireNotNull(BrowserInput.Click(-1f, 2f).toBrowserWirePayload()))
        assertEquals("click", click.getString("type"))
        assertEquals(0.0, click.getDouble("nx"), 0.0)
        assertEquals(1.0, click.getDouble("ny"), 0.0)

        val wheel = JSONObject(requireNotNull(BrowserInput.Wheel(0f, 9_000f, Float.NaN, 2f).toBrowserWirePayload()))
        assertEquals(2_000.0, wheel.getDouble("dy"), 0.0)
        assertEquals(0.5, wheel.getDouble("nx"), 0.0)
        assertEquals(1.0, wheel.getDouble("ny"), 0.0)

        val navigate = JSONObject(requireNotNull(BrowserInput.Navigate("docs.example.com").toBrowserWirePayload()))
        assertEquals("https://docs.example.com", navigate.getString("url"))
        assertNull(BrowserInput.Navigate("file:///tmp/page.html").toBrowserWirePayload())
        assertNull(BrowserInput.Wheel(0f, 0f).toBrowserWirePayload())
        assertTrue(BrowserInput.Text("page input").toBrowserWirePayload()?.contains("page input") == true)
    }
}
