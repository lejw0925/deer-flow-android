package com.deerflow.mobile.ui

import androidx.compose.ui.text.style.TextAlign
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTableAlignmentTest {
    private fun tableAlignments(markdown: String, columns: Int): List<TextAlign> {
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
        val table = tree.children.first { it.type == GFMElementTypes.TABLE }
        return tableColumnAlignments(markdown, table, columns)
    }

    @Test
    fun readsPerColumnAlignmentFromTheSeparatorRow() {
        val markdown = """
            | Name | Value | Note |
            | :--- | :---: | ---: |
            | a | b | c |
        """.trimIndent()

        assertEquals(
            listOf(TextAlign.Start, TextAlign.Center, TextAlign.End),
            tableAlignments(markdown, 3),
        )
    }

    @Test
    fun defaultsToStartAlignmentWithoutColons() {
        val markdown = """
            | Name | Value |
            | --- | --- |
            | a | b |
        """.trimIndent()

        assertEquals(listOf(TextAlign.Start, TextAlign.Start), tableAlignments(markdown, 2))
    }

    @Test
    fun treatsALeadingColonOnlyAsStartAlignment() {
        val markdown = """
            | Name |
            | :-- |
            | a |
        """.trimIndent()

        assertEquals(listOf(TextAlign.Start), tableAlignments(markdown, 1))
    }

    @Test
    fun fillsColumnsBeyondTheSeparatorMarkersWithStartAlignment() {
        val markdown = """
            | Name | Value |
            | :---: | ---: |
            | a | b |
        """.trimIndent()

        assertEquals(
            listOf(TextAlign.Center, TextAlign.End, TextAlign.Start),
            tableAlignments(markdown, 3),
        )
    }
}
