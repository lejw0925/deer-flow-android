package com.deerflow.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserViewportGeometryTest {
    @Test
    fun mapsTheLetterboxed1280By720FrameInsteadOfTheWholeContainer() {
        val bounds = requireNotNull(
            browserFrameBounds(
                containerWidth = 1_104f,
                containerHeight = 1_616f,
            ),
        )

        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(497.5f, bounds.top, 0.001f)
        assertEquals(1_104f, bounds.width, 0.001f)
        assertEquals(621f, bounds.height, 0.001f)
        assertNull(bounds.normalizedPointAt(552f, 200f))

        val center = requireNotNull(bounds.normalizedPointAt(552f, 808f))
        assertEquals(0.5f, center.nx, 0.001f)
        assertEquals(0.5f, center.ny, 0.001f)
    }

    @Test
    fun fillsTheDedicated16By9MobileViewport() {
        val bounds = requireNotNull(browserFrameBounds(containerWidth = 1_104f, containerHeight = 621f))

        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(1_104f, bounds.width, 0.001f)
        assertEquals(621f, bounds.height, 0.001f)
    }
}
