package com.deerflow.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownImageLabelTest {
    @Test
    fun prefersNonBlankTitle() {
        assertEquals("cover", markdownImageLabel(title = "cover", destination = "/mnt/user-data/outputs/a.png"))
    }

    @Test
    fun fallsBackToDestinationWhenTitleIsNull() {
        assertEquals(
            "/mnt/user-data/outputs/badge-bajie.png",
            markdownImageLabel(title = null, destination = "/mnt/user-data/outputs/badge-bajie.png"),
        )
    }

    @Test
    fun fallsBackToDestinationWhenTitleIsBlank() {
        assertEquals(
            "/mnt/user-data/outputs/badge-bajie.png",
            markdownImageLabel(title = "  ", destination = "/mnt/user-data/outputs/badge-bajie.png"),
        )
    }

    @Test
    fun returnsEmptyWhenTitleAndDestinationAreMissing() {
        assertEquals("", markdownImageLabel(title = null, destination = null))
    }
}
