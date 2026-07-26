package com.deerflow.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text

class CitationSourcesTest {
    @Test
    fun extractsAndGroupsCitationLinksWithoutTreatingCodeOrOrdinaryLinksAsCitations() {
        val sources = citationSources(
            """
            Evidence [citation: DeerFlow docs](https://example.com/docs) and
            [citation: Source](https://example.com/docs).

            [ordinary link](https://example.com/ordinary)

            ```text
            [citation: ignored](https://example.com/code)
            ```
            """.trimIndent(),
        )

        assertEquals(1, sources.size)
        assertEquals("DeerFlow docs", sources.single().title)
        assertEquals("example.com", sources.single().domain)
        assertEquals(2, sources.single().count)
        assertTrue(sources.single().url.startsWith("https://example.com/docs"))
    }

    @Test
    fun usesTheDomainForGenericCitationTitles() {
        val source = citationSources("[citation: 来源](https://www.example.com/docs)").single()

        assertEquals("example.com", source.title)
        assertEquals("example.com", source.domain)
    }

    @Test
    fun removesSourcesSectionWithoutReplacingBodyCitationSources() {
        val presentation = citationPresentation(
            """
            Summary [citation: Inline](https://example.com/inline)

            ### Sources
            - [Docs](https://example.com/docs)
            - [API](https://example.com/api)

            ## Follow-up
            This remains in the body.
            """.trimIndent(),
        )

        assertEquals(3, presentation.bodyNodes.size)
        assertEquals(listOf("Follow-up"), headingTitles(presentation.bodyNodes))
        assertEquals(listOf("Inline"), presentation.sources.map(CitationSource::title))
    }

    @Test
    fun movesOrdinarySourceLinksIntoTheCitationCard() {
        val presentation = citationPresentation(
            """
            Summary

            ## Sources
            - [Annual report](https://example.com/report) - Company filing
            - [Careers](https://jobs.example.com/openings) - Open roles
            """.trimIndent(),
        )

        assertEquals(1, presentation.bodyNodes.size)
        assertTrue(headingTitles(presentation.bodyNodes).isEmpty())
        assertEquals(listOf("Annual report", "Careers"), presentation.sources.map(CitationSource::title))
    }

    @Test
    fun keepsSourcesSectionWhenItDoesNotContainExternalLinks() {
        val presentation = citationPresentation(
            """
            Summary

            ## Sources
            These notes do not contain a source link.
            """.trimIndent(),
        )

        assertEquals(3, presentation.bodyNodes.size)
        assertEquals(listOf("Sources"), headingTitles(presentation.bodyNodes))
        assertTrue(presentation.sources.isEmpty())
    }

    @Test
    fun keepsWholeDocumentCitationBehaviorWithoutSourcesSection() {
        val presentation = citationPresentation("Evidence [citation: Docs](https://example.com/docs)")

        assertEquals(1, presentation.bodyNodes.size)
        assertEquals(listOf("Docs"), presentation.sources.map(CitationSource::title))
    }

    private fun headingTitles(nodes: List<Node>): List<String> = nodes
        .filterIsInstance<Heading>()
        .map { (it.firstChild as? Text)?.literal.orEmpty() }
}
