package com.deerflow.mobile.ui

import org.commonmark.node.Paragraph
import org.commonmark.node.BlockQuote
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMathTest {
    private val parser = Parser.builder()
        .includeSourceSpans(IncludeSourceSpans.BLOCKS)
        .build()

    @Test
    fun extractsDollarDelimitedDisplayMathFromAParagraph() {
        val source = listOf(
            "$$",
            "\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
            "$$",
        ).joinToString(separator = "\n")
        val document = parser.parse(source)

        assertEquals(
            "\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
            displayMathFormula(document.firstChild as Paragraph, source),
        )
    }

    @Test
    fun extractsBracketDelimitedDisplayMathFromOriginalParagraphSource() {
        val source = listOf(
            "\\[",
            "\\int_0^1 x^2 \\, dx",
            "\\]",
        ).joinToString(separator = "\n")
        val document = parser.parse(source)

        assertEquals(
            "\\int_0^1 x^2 \\, dx",
            displayMathFormula(document.firstChild as Paragraph, source),
        )
    }

    @Test
    fun extractsBracketDelimitedDisplayMathInsideBlockQuotes() {
        val source = listOf(
            "> \\[",
            "> x^2",
            "> \\]",
        ).joinToString(separator = "\n")
        val document = parser.parse(source)
        val quote = document.firstChild as BlockQuote

        assertEquals("x^2", displayMathFormula(quote.firstChild as Paragraph, source))
    }

    @Test
    fun leavesUnescapedBracketsToTheNormalMarkdownRenderer() {
        val source = "[x^2]"
        val document = parser.parse(source)

        assertNull(displayMathFormula(document.firstChild as Paragraph, source))
    }

    @Test
    fun leavesInlineMathAndIncompleteBlocksToTheNormalMarkdownRenderer() {
        assertNull(displayMathSource("The result is \$x^2\$."))
        assertNull(displayMathSource("$$\\frac{1}{2}"))
        assertNull(displayMathSource("$$$$"))
    }

    @Test
    fun keepsSpecialConversationContentOnTheCustomRenderer() {
        assertTrue(requiresCustomMarkdownRenderer("$$\\frac{1}{2}$$"))
        assertTrue(requiresCustomMarkdownRenderer("[Report](/mnt/user-data/report.md)"))
        assertTrue(requiresCustomMarkdownRenderer("[citation: Docs](https://example.com/docs)"))
    }

    @Test
    fun sendsStandardGfmToTheEnhancedRenderer() {
        assertFalse(
            requiresCustomMarkdownRenderer(
                """
                ## Status

                | Name | Value |
                | --- | --- |
                | DeerFlow | Ready |

                - [x] Complete
                """.trimIndent(),
            ),
        )
    }
}
