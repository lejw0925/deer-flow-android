package com.deerflow.mobile.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.deerflow.mobile.R
import io.ratex.RaTeXView
import java.net.URI
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

private val markdownParser = Parser.builder()
    .extensions(listOf(StrikethroughExtension.create(), TablesExtension.create()))
    .includeSourceSpans(IncludeSourceSpans.BLOCKS)
    .build()

@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier, onArtifact: (String) -> Unit = {}) {
    val document = remember(markdown) { markdownParser.parse(markdown) }
    val citations = remember(markdown) { document.citationSources() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        document.children().forEach { MarkdownBlock(it, markdown, onArtifact) }
        CitationSources(citations)
    }
}

@Composable
private fun MarkdownBlock(node: Node, markdown: String, onArtifact: (String) -> Unit) {
    when (node) {
        is Heading -> MarkdownInline(
            node,
            style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }.copy(fontWeight = FontWeight.SemiBold),
            onArtifact = onArtifact,
        )
        is Paragraph -> MarkdownParagraph(node, markdown, onArtifact)
        is FencedCodeBlock -> MarkdownCode(node.literal, node.info.takeIf { it.isNotBlank() })
        is IndentedCodeBlock -> MarkdownCode(node.literal, null)
        is BlockQuote -> Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).height(48.dp).background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                node.children().forEach { MarkdownBlock(it, markdown, onArtifact) }
            }
        }
        is BulletList -> MarkdownList(node, markdown, ordered = false, onArtifact = onArtifact)
        is OrderedList -> MarkdownList(node, markdown, ordered = true, start = node.markerStartNumber, onArtifact = onArtifact)
        is TableBlock -> MarkdownTable(node, onArtifact)
        is ThematicBreak -> HorizontalDivider()
        is Image -> MarkdownMessageImage(node.destination, node.title, onArtifact)
        else -> node.children().forEach { MarkdownBlock(it, markdown, onArtifact) }
    }
}

@Composable
private fun MarkdownParagraph(paragraph: Paragraph, markdown: String, onArtifact: (String) -> Unit) {
    displayMathFormula(paragraph, markdown)?.let { formula ->
        MarkdownMathBlock(formula)
        return
    }
    val children = paragraph.children().toList()
    if (children.none { it is Image }) {
        MarkdownInline(paragraph, MaterialTheme.typography.bodyLarge, onArtifact)
        return
    }
    // Split paragraph around images so they render as block media like the web client.
    val segments = mutableListOf<MutableList<Node>>()
    var current = mutableListOf<Node>()
    fun flush() {
        if (current.isNotEmpty()) {
            segments += current
            current = mutableListOf()
        }
    }
    children.forEach { child ->
        if (child is Image) {
            flush()
            segments += mutableListOf(child)
        } else {
            current += child
        }
    }
    flush()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            val only = segment.singleOrNull()
            if (only is Image) {
                MarkdownMessageImage(only.destination, only.title, onArtifact)
            } else {
                MarkdownInlineNodes(segment, MaterialTheme.typography.bodyLarge, onArtifact)
            }
        }
    }
}

@Composable
private fun MarkdownMathBlock(latex: String) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val fontSizeDp = MaterialTheme.typography.bodyLarge.fontSize.value
    val scroll = rememberScrollState()
    var invalidFormula by remember(latex) { mutableStateOf(false) }
    if (invalidFormula) {
        MarkdownCode(latex, "latex")
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        contentAlignment = Alignment.CenterStart,
    ) {
        AndroidView(
            factory = {
                val mathView = RaTeXView(context)
                mathView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                mathView.contentDescription = latex
                mathView.displayMode = true
                mathView.fontSize = fontSizeDp
                mathView.color = color
                mathView.onError = { invalidFormula = true }
                mathView.latex = latex
                mathView
            },
            update = { view ->
                view.displayMode = true
                view.fontSize = fontSizeDp
                view.color = color
                view.latex = latex
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

internal fun displayMathFormula(paragraph: Paragraph, markdown: String? = null): String? {
    val source = markdown?.let { paragraphSource(paragraph, it) }
    if (source != null) return displayMathSource(source)
    return displayMathFormulaFromNodes(paragraph)
}

private fun paragraphSource(paragraph: Paragraph, markdown: String): String? {
    val spans = paragraph.sourceSpans
    if (spans.isEmpty()) return null
    val parts = mutableListOf<String>()
    for (span in spans) {
        val start = span.inputIndex
        val length = span.length
        if (start < 0 || length < 0 || start > markdown.length || length > markdown.length - start) return null
        parts += markdown.substring(start, start + length)
    }
    return parts.joinToString(separator = "\n")
}

private fun displayMathFormulaFromNodes(paragraph: Paragraph): String? {
    val source = buildString {
        paragraph.children().forEach { child ->
            when (child) {
                is MarkdownTextNode -> append(child.literal)
                is SoftLineBreak, is HardLineBreak -> append('\n')
                else -> return null
            }
        }
    }
    return displayMathSource(source)
}

internal fun displayMathSource(source: String): String? {
    val trimmed = source.trim()
    val delimiters = when {
        trimmed.startsWith("$$") && trimmed.endsWith("$$") -> "$$" to "$$"
        trimmed.startsWith("\\[") && trimmed.endsWith("\\]") -> "\\[" to "\\]"
        else -> return null
    }
    if (trimmed.length <= delimiters.first.length + delimiters.second.length) return null
    return trimmed
        .substring(delimiters.first.length, trimmed.length - delimiters.second.length)
        .trim()
        .takeIf(String::isNotEmpty)
}

@Composable
private fun MarkdownList(node: Node, markdown: String, ordered: Boolean, start: Int = 1, onArtifact: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        node.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    if (ordered) "${start + index}." else "\u2022",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(28.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.children().forEach { MarkdownBlock(it, markdown, onArtifact) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: TableBlock, onArtifact: (String) -> Unit) {
    val scroll = rememberScrollState()
    Surface(
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        Column(Modifier.horizontalScroll(scroll)) {
            table.children()
                .flatMap { section -> section.children() }
                .filterIsInstance<TableRow>()
                .forEach { row ->
                    val header = row.children().filterIsInstance<TableCell>().firstOrNull()?.isHeader == true
                    Row(Modifier.fillMaxWidth()) {
                        row.children().filterIsInstance<TableCell>().forEach { cell ->
                            Column(
                                modifier = Modifier
                                    .width(160.dp)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(8.dp),
                            ) {
                                MarkdownInline(
                                    cell,
                                    (if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium),
                                    onArtifact,
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun MarkdownCode(code: String, language: String?) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            language?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            Text(code.trimEnd(), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MarkdownInline(node: Node, style: TextStyle, onArtifact: (String) -> Unit = {}) {
    MarkdownInlineNodes(node.children().toList(), style, onArtifact)
}

@Suppress("DEPRECATION")
@Composable
private fun MarkdownInlineNodes(nodes: List<Node>, style: TextStyle, onArtifact: (String) -> Unit = {}) {
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val value = remember(nodes, primary, codeBackground) {
        buildAnnotatedString {
            nodes.forEach { appendInlineNode(it, primary, codeBackground) }
        }
    }
    if (value.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    ClickableText(
        text = value,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            value.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { link ->
                if (link.item.isArtifactPath()) onArtifact(link.item)
                else runCatching { uriHandler.openUri(link.item) }
            }
        },
    )
}

internal fun markdownImageLabel(title: String?, destination: String?): String {
    val label = title?.takeIf { it.isNotBlank() } ?: destination.orEmpty()
    return label
}

private fun AnnotatedString.Builder.appendInlineNode(child: Node, primary: Color, codeBackground: Color) {
    when (child) {
        is MarkdownTextNode -> append(child.literal)
        is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(child.literal) }
        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            child.children().forEach { appendInlineNode(it, primary, codeBackground) }
        }
        is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            child.children().forEach { appendInlineNode(it, primary, codeBackground) }
        }
        is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            child.children().forEach { appendInlineNode(it, primary, codeBackground) }
        }
        is Link -> {
            val citation = child.citationSource()
            if (citation != null) {
                withStyle(
                    SpanStyle(
                        color = primary,
                        background = primary.copy(alpha = 0.14f),
                        fontWeight = FontWeight.Medium,
                    ),
                ) {
                    append(" ${citation.title} ")
                }
            } else {
                pushStringAnnotation("URL", child.destination)
                withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                    child.children().forEach { appendInlineNode(it, primary, codeBackground) }
                }
                pop()
            }
        }
        // Images are rendered as block media by MarkdownParagraph / MarkdownBlock.
        is Image -> Unit
        is SoftLineBreak -> append(' ')
        is HardLineBreak -> append('\n')
        is HtmlInline -> append(child.literal)
        else -> child.children().forEach { appendInlineNode(it, primary, codeBackground) }
    }
}

internal data class CitationSource(val title: String, val url: String, val domain: String, val count: Int)

@Composable
private fun CitationSources(sources: List<CitationSource>) {
    if (sources.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.citation_sources, sources.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sources.forEach { source ->
            val link = remember(source, primary) {
                buildAnnotatedString {
                    pushStringAnnotation("URL", source.url)
                    withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                        append(source.title)
                    }
                    pop()
                    append(" · ${source.domain}")
                    if (source.count > 1) append(" ×${source.count}")
                }
            }
            ClickableText(
                text = link,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = { offset ->
                    link.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { reference ->
                        runCatching { uriHandler.openUri(reference.item) }
                    }
                },
            )
        }
    }
}

internal fun citationSources(markdown: String): List<CitationSource> {
    val sources = linkedNodes(markdownParser.parse(markdown))
        .mapNotNull(Link::citationSource)
        .groupBy { it.url }
    return sources.values.map { matches ->
        val source = matches.first()
        source.copy(count = matches.size)
    }
}

private fun Node.citationSources(): List<CitationSource> = linkedNodes(this)
    .mapNotNull(Link::citationSource)
    .groupBy { it.url }
    .values
    .map { matches -> matches.first().copy(count = matches.size) }

private fun linkedNodes(node: Node): Sequence<Link> = sequence {
    if (node is Link) yield(node)
    node.children().forEach { child -> yieldAll(linkedNodes(child)) }
}

private fun Link.citationSource(): CitationSource? {
    val url = destination.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
    val rawTitle = inlineText(this).trim()
    if (!rawTitle.startsWith("citation:", ignoreCase = true)) return null
    val domain = runCatching { URI(url).host.removePrefix("www.") }.getOrNull().orEmpty().ifBlank { url }
    val title = rawTitle.substringAfter(':').trim()
        .takeUnless { it.equals("source", ignoreCase = true) || it == "来源" }
        .orEmpty()
        .ifBlank { domain }
    return CitationSource(title = title, url = url, domain = domain, count = 1)
}

private fun inlineText(node: Node): String = buildString {
    node.children().forEach { child ->
        when (child) {
            is MarkdownTextNode -> append(child.literal)
            is Code -> append(child.literal)
            is SoftLineBreak, is HardLineBreak -> append(' ')
            else -> append(inlineText(child))
        }
    }
}

private inline fun AnnotatedString.Builder.withStyle(style: SpanStyle, block: AnnotatedString.Builder.() -> Unit) {
    pushStyle(style)
    try {
        block()
    } finally {
        pop()
    }
}

private fun Node.children(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}
