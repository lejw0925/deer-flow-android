package com.deerflow.mobile.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deerflow.mobile.R
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownAnnotator
import com.mikepenz.markdown.compose.LocalMarkdownAnimations
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.components.CurrentComponentsBridge
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.model.DefaultMarkdownAnnotator
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.utils.MARKDOWN_TAG_URL
import com.mikepenz.markdown.utils.buildMarkdownAnnotatedString
import com.mikepenz.markdown.utils.codeSpanStyle
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.mikepenz.markdown.utils.linkTextSpanStyle
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import io.ratex.RaTeXView
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private val markdownParser = Parser.builder()
    .extensions(listOf(StrikethroughExtension.create(), TablesExtension.create()))
    .includeSourceSpans(IncludeSourceSpans.BLOCKS)
    .build()

private const val CITATION_ANNOTATION = "CITATION"
private val LocalCitationNavigator = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    onArtifact: (String) -> Unit = {},
    streaming: Boolean = false,
) {
    // While streaming, a cheap string pre-filter skips the full citationPresentation
    // parse per delta (the parser re-reads the ENTIRE accumulated text every refresh —
    // O(n²) over a long reply). The pre-filter is a conservative superset of the
    // presentation triggers, so the decision can only be optimistic; once the stream
    // settles the full parse runs and the sources footer replaces the inline section.
    // Math, images and artifact links render through the enhanced renderer's custom
    // components and never need the presentation.
    val presentation = remember(markdown, streaming) {
        if (streaming && !markdown.mayNeedCitationPresentation) null else citationPresentation(markdown)
    }
    val bodyMarkdown = remember(markdown, presentation) {
        presentation?.takeIf { it.sourcesSectionStripped }?.let { sourcesStrippedMarkdown(markdown, it.bodyNodes) }
            ?: markdown
    }
    val sourceRequesters = remember(presentation?.sources) {
        presentation?.sources.orEmpty().associate { source -> source.url to BringIntoViewRequester() }
    }
    val scope = rememberCoroutineScope()
    val onCitationClick = remember(sourceRequesters, scope) {
        { url: String ->
            sourceRequesters[url]?.let { requester ->
                scope.launch { requester.bringIntoView() }
            }
            Unit
        }
    }
    CompositionLocalProvider(LocalCitationNavigator provides onCitationClick) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EnhancedMarkdownContent(bodyMarkdown, Modifier, streaming, onArtifact)
            presentation?.let { CitationSources(it.sources, sourceRequesters) }
        }
    }
}

/**
 * Conservative superset of [citationPresentation]'s triggers: citation links
 * ("citation:" titles) and a trailing "Sources" heading whose plain links are
 * lifted into the sources footer. Everything the enhanced renderer needs for
 * math, images and artifact links is derived from its own parse.
 */
private val sourcesHeadingRegex = Regex("(?m)^#{1,6}\\s*sources:?\\s*$", RegexOption.IGNORE_CASE)

internal val String.mayNeedCitationPresentation: Boolean
    get() = contains("citation:", ignoreCase = true) || sourcesHeadingRegex.containsMatchIn(this)

/** Rebuilds the document source without the stripped "Sources" section. */
internal fun sourcesStrippedMarkdown(markdown: String, bodyNodes: List<Node>): String {
    if (bodyNodes.isEmpty()) return ""
    return bodyNodes.joinToString(separator = "\n\n") { node -> node.sourceText(markdown) }
}

private fun Node.sourceText(markdown: String): String {
    val parts = mutableListOf<String>()
    for (span in sourceSpans) {
        val start = span.inputIndex
        val length = span.length
        if (start < 0 || length < 0 || start > markdown.length || length > markdown.length - start) return ""
        parts += markdown.substring(start, start + length)
    }
    return parts.joinToString(separator = "")
}

/**
 * Top-level entry of the unified renderer. Mirrors the library's own dispatcher
 * (which is internal) with three differences that matter for chat: block gaps
 * come from `spacedBy` instead of a Spacer before every element — so no dead
 * space above the first line — the parse tree is remembered per content, and
 * the spacing/theme locals are fed from our design system.
 */
@Composable
private fun EnhancedMarkdownContent(
    markdown: String,
    modifier: Modifier,
    streaming: Boolean,
    onArtifact: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val citationChip = SpanStyle(
        color = primary,
        background = primary.copy(alpha = 0.14f),
        fontWeight = FontWeight.Medium,
    )
    // Inline-render hook: citation links become tinted chips that navigate to the
    // matching source row instead of opening a browser. Every other node falls
    // through to the library's default handling.
    val annotator = remember(citationChip) {
        DefaultMarkdownAnnotator { content, node ->
            node.citationChip(content)?.let { citation ->
                pushStringAnnotation(CITATION_ANNOTATION, citation.url)
                pushStyle(citationChip)
                append(' ')
                append(citation.title)
                append(' ')
                pop()
                pop()
                true
            } ?: false
        }
    }
    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onSurface,
        codeText = MaterialTheme.colorScheme.onSurface,
        inlineCodeText = MaterialTheme.colorScheme.onSurface,
        linkText = MaterialTheme.colorScheme.primary,
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
        tableText = MaterialTheme.colorScheme.onSurface,
        tableBackground = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    // The m3 defaults size headings for full pages (h1 = displayLarge); chat bubbles
    // need the same compact scale the previous renderer used. h5/h6 drop to a muted
    // tone so deep hierarchies stay distinguishable in a bubble.
    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        h2 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        h3 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        h4 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        h5 = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        h6 = TextStyle(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        quote = MaterialTheme.typography.bodyLarge,
    )
    val components = if (streaming) streamingMarkdownComponents(onArtifact) else defaultMarkdownComponents(onArtifact)
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides remember { ReferenceLinkHandlerImpl() },
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides typography,
        LocalMarkdownPadding provides markdownPadding(),
        LocalMarkdownDimens provides markdownDimens(tableCellPadding = 12.dp),
        LocalImageTransformer provides NoOpImageTransformerImpl(),
        LocalMarkdownAnnotator provides annotator,
        LocalMarkdownExtendedSpans provides markdownExtendedSpans(),
        LocalMarkdownComponents provides components,
        LocalMarkdownAnimations provides markdownAnimations(),
    ) {
        Column(modifier.fillMaxWidth().testTag(UiTags.EnhancedMarkdown)) {
            val tree = remember(markdown) { MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown) }
            var previousType: IElementType? = null
            tree.children.forEach { node ->
                val gap = blockGap(previousType, node.type)
                if (gap > 0.dp) Spacer(Modifier.height(gap))
                MarkdownFlowNode(markdown, node, components)
                previousType = node.type
            }
        }
    }
}

/** Headings breathe: extra space above, tighter below; everything else 8dp. */
private fun blockGap(previous: IElementType?, current: IElementType?): Dp {
    if (previous == null || current == MarkdownTokenTypes.EOL) return 0.dp
    val afterHeading = previous == MarkdownElementTypes.ATX_1 || previous == MarkdownElementTypes.ATX_2 ||
        previous == MarkdownElementTypes.ATX_3 || previous == MarkdownElementTypes.ATX_4 ||
        previous == MarkdownElementTypes.ATX_5 || previous == MarkdownElementTypes.ATX_6
    val beforeBigHeading = current == MarkdownElementTypes.ATX_1 || current == MarkdownElementTypes.ATX_2 ||
        current == MarkdownElementTypes.ATX_3
    return when {
        afterHeading -> 4.dp
        beforeBigHeading -> 14.dp
        else -> 8.dp
    }
}

@Composable
private fun ColumnScope.MarkdownFlowNode(content: String, node: ASTNode, components: MarkdownComponents) {
    val model = MarkdownComponentModel(content, node, LocalMarkdownTypography.current)
    when (node.type) {
        MarkdownElementTypes.ATX_1 -> components.heading1(this, model)
        MarkdownElementTypes.ATX_2 -> components.heading2(this, model)
        MarkdownElementTypes.ATX_3 -> components.heading3(this, model)
        MarkdownElementTypes.ATX_4 -> components.heading4(this, model)
        MarkdownElementTypes.ATX_5 -> components.heading5(this, model)
        MarkdownElementTypes.ATX_6 -> components.heading6(this, model)
        MarkdownElementTypes.SETEXT_1 -> components.setextHeading1(this, model)
        MarkdownElementTypes.SETEXT_2 -> components.setextHeading2(this, model)
        MarkdownElementTypes.BLOCK_QUOTE -> components.blockQuote(this, model)
        MarkdownElementTypes.PARAGRAPH -> components.paragraph(this, model)
        MarkdownElementTypes.ORDERED_LIST -> components.orderedList(this, model)
        MarkdownElementTypes.UNORDERED_LIST -> components.unorderedList(this, model)
        MarkdownElementTypes.CODE_FENCE -> components.codeFence(this, model)
        MarkdownElementTypes.CODE_BLOCK -> components.codeBlock(this, model)
        MarkdownElementTypes.IMAGE -> components.image(this, model)
        MarkdownElementTypes.LINK_DEFINITION -> components.linkDefinition(this, model)
        MarkdownTokenTypes.HORIZONTAL_RULE -> components.horizontalRule(this, model)
        MarkdownTokenTypes.TEXT -> components.text(this, model)
        MarkdownTokenTypes.EOL -> components.eol(this, model)
        GFMElementTypes.TABLE -> components.table(this, model)
        else -> node.children.forEach { child -> MarkdownFlowNode(content, child, components) }
    }
}

private fun defaultMarkdownComponents(onArtifact: (String) -> Unit): MarkdownComponents = markdownComponents(
    codeBlock = highlightedCodeComponent,
    codeFence = highlightedCodeComponent,
    table = borderedTableComponent,
    paragraph = { model -> MarkdownFlowParagraph(model.content, model.node, model.typography.paragraph, onArtifact) },
    image = { model -> MarkdownMessageImageFromNode(model.content, model.node, onArtifact) },
    blockQuote = { model -> MarkdownRichQuote(model.content, model.node, model.typography.quote, onArtifact) },
)

private fun streamingMarkdownComponents(onArtifact: (String) -> Unit): MarkdownComponents = markdownComponents(
    codeBlock = highlightedCodeComponent,
    codeFence = highlightedCodeComponent,
    table = revealOnAppear(borderedTableComponent),
    paragraph = revealOnAppear { model ->
        MarkdownFlowParagraph(model.content, model.node, model.typography.paragraph, onArtifact)
    },
    heading1 = revealOnAppear(CurrentComponentsBridge.heading1),
    heading2 = revealOnAppear(CurrentComponentsBridge.heading2),
    heading3 = revealOnAppear(CurrentComponentsBridge.heading3),
    heading4 = revealOnAppear(CurrentComponentsBridge.heading4),
    heading5 = revealOnAppear(CurrentComponentsBridge.heading5),
    heading6 = revealOnAppear(CurrentComponentsBridge.heading6),
    orderedList = revealOnAppear(CurrentComponentsBridge.orderedList),
    unorderedList = revealOnAppear(CurrentComponentsBridge.unorderedList),
    blockQuote = revealOnAppear { model ->
        MarkdownRichQuote(model.content, model.node, model.typography.quote, onArtifact)
    },
    image = { model -> MarkdownMessageImageFromNode(model.content, model.node, onArtifact) },
)

/** Wraps a markdown component so elements that appear mid-stream blur-fade in once. */
private fun revealOnAppear(component: MarkdownComponent): MarkdownComponent = { model ->
    StreamingReveal(animate = true) { component(model) }
}

/** Renders GFM tables with full cell borders and separator-row column alignment. */
private val borderedTableComponent: MarkdownComponent = { model ->
    EnhancedMarkdownTable(model.content, model.node, model.typography.text)
}

// region Code blocks — highlighted content with a language + copy header

/** Fenced and indented code share the same chrome; both carry a language hint when present. */
private val highlightedCodeComponent: MarkdownComponent = { model ->
    MarkdownCodeContent(model.content, model.node) { code, language -> MarkdownCodeSurface(code, language) }
}

@Composable
private fun MarkdownCodeContent(
    content: String,
    node: ASTNode,
    block: @Composable (String, String?) -> Unit,
) {
    if (node.type == MarkdownElementTypes.CODE_FENCE) {
        MarkdownCodeFence(content, node, block)
    } else {
        MarkdownCodeBlock(content, node, block)
    }
}

/**
 * Chat code block: syntax-highlighted content that scrolls horizontally instead of
 * wrapping, under a header naming the language with a one-tap copy action.
 */
@Composable
private fun MarkdownCodeSurface(code: String, language: String?) {
    val codeBackground = LocalMarkdownColors.current.codeBackground
    val codeText = LocalMarkdownColors.current.codeText
    val dividerColor = LocalMarkdownColors.current.dividerColor
    val codeStyle = LocalMarkdownTypography.current.code
    val syntaxLanguage = remember(language) { language?.let { SyntaxLanguage.getByName(it) } }
    val highlighted = remember(code, syntaxLanguage) {
        val highlights = Highlights.Builder()
            .code(code)
            .let { builder -> syntaxLanguage?.let { builder.language(it) } ?: builder }
            .build()
        buildAnnotatedString {
            append(highlights.getCode())
            highlights.getHighlights()
                .filterIsInstance<ColorHighlight>()
                .forEach { addStyle(SpanStyle(color = Color(it.rgb).copy(alpha = 1f)), it.location.start, it.location.end) }
            highlights.getHighlights()
                .filterIsInstance<BoldHighlight>()
                .forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.location.start, it.location.end) }
        }
    }
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val clipboard = LocalClipboardManager.current
    Surface(color = codeBackground, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language.orEmpty(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            HorizontalDivider(color = dividerColor.copy(alpha = 0.4f), thickness = 0.5.dp)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Text(
                    highlighted,
                    style = codeStyle,
                    color = codeText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// endregion

// region Paragraph — display math, task lists, block-split images, citation chips

private val taskListPrefixRegex = Regex("^\\s*\\[( |x|X)\\]\\s+")

/** True/false for a GFM task-list item paragraph (only inside list items); null otherwise. */
private fun ASTNode.taskListState(content: String): Boolean? {
    if (parent?.type != MarkdownElementTypes.LIST_ITEM) return null
    val match = taskListPrefixRegex.find(content.substring(startOffset, endOffset.coerceAtMost(content.length))) ?: return null
    return match.groupValues[1] != " "
}

/** Blanks the `[x] ` marker in place so the inline node offsets stay valid. */
private fun ASTNode.strippedTaskContent(content: String): String {
    val source = content.substring(startOffset, endOffset.coerceAtMost(content.length))
    val match = taskListPrefixRegex.find(source) ?: return content
    val start = startOffset + match.range.first
    return content.replaceRange(start, start + match.value.length, " ".repeat(match.value.length))
}

@Composable
private fun MarkdownFlowParagraph(content: String, node: ASTNode, style: TextStyle, onArtifact: (String) -> Unit) {
    val taskState = node.taskListState(content)
    if (taskState != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                if (taskState) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (taskState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp).size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                MarkdownParagraphBody(node.strippedTaskContent(content), node, style, onArtifact)
            }
        }
        return
    }
    MarkdownParagraphBody(content, node, style, onArtifact)
}

@Composable
private fun MarkdownParagraphBody(content: String, node: ASTNode, style: TextStyle, onArtifact: (String) -> Unit) {
    val raw = content.substring(node.startOffset, node.endOffset.coerceAtMost(content.length))
    // Quote continuation markers ("| > " prefixes) would hide the math delimiters.
    val source = raw.lines().joinToString(separator = "\n") { line ->
        when {
            line.startsWith("> ") -> line.removePrefix("> ")
            line.startsWith(">") -> line.removePrefix(">")
            else -> line
        }
    }
    displayMathSource(source)?.let { formula ->
        MarkdownMathBlock(formula)
        return
    }
    val children = node.children.toList()
    if (children.none { it.type == MarkdownElementTypes.IMAGE }) {
        MarkdownInlineText(content, children, style, onArtifact)
        return
    }
    // Split paragraph around images so they render as block media like the web client.
    val segments = remember(node) {
        buildList {
            var current = mutableListOf<ASTNode>()
            children.forEach { child ->
                if (child.type == MarkdownElementTypes.IMAGE) {
                    if (current.isNotEmpty()) {
                        add(current.toList())
                        current = mutableListOf()
                    }
                    add(listOf(child))
                } else {
                    current += child
                }
            }
            if (current.isNotEmpty()) add(current.toList())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            val only = segment.singleOrNull()
            if (only != null && only.type == MarkdownElementTypes.IMAGE) {
                MarkdownMessageImageFromNode(content, only, onArtifact)
            } else {
                MarkdownInlineText(content, segment, style, onArtifact)
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(content: String, children: List<ASTNode>, style: TextStyle, onArtifact: (String) -> Unit) {
    val linkSpanStyle = LocalMarkdownTypography.current.linkTextSpanStyle
    val codeSpanStyle = LocalMarkdownTypography.current.codeSpanStyle
    val annotator = LocalMarkdownAnnotator.current
    val value = remember(content, children, linkSpanStyle, codeSpanStyle, annotator, style) {
        buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            buildMarkdownAnnotatedString(content, children, linkSpanStyle, codeSpanStyle, annotator)
            pop()
        }
    }.let { annotated ->
        // Blank task markers leave leading spaces behind; markdown paragraphs never
        // render meaningful leading whitespace, so trim it.
        val firstNonWhitespace = annotated.text.indexOfFirst { !it.isWhitespace() }
        when {
            annotated.text.isEmpty() -> null
            firstNonWhitespace > 0 -> annotated.subSequence(firstNonWhitespace, annotated.length)
            else -> annotated
        }
    }
    value ?: return
    val text = MaterialTheme.colorScheme.onSurface
    val uriHandler = LocalUriHandler.current
    val referenceLinkHandler = LocalReferenceLinkHandler.current
    val onCitationClick = LocalCitationNavigator.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasCitations = value.getStringAnnotations(CITATION_ANNOTATION, 0, value.length).isNotEmpty()
    val clickable = hasCitations || value.getStringAnnotations(MARKDOWN_TAG_URL, 0, value.length).isNotEmpty()
    val textNode: @Composable () -> Unit = {
        Text(
            text = value,
            style = style,
            color = text,
            onTextLayout = { layoutResult.value = it },
            modifier = if (clickable) {
                Modifier.pointerInput(value, onCitationClick, onArtifact) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val offset = layoutResult.value?.getOffsetForPosition(down.position) ?: return@awaitEachGesture
                        val citation = value.getStringAnnotations(CITATION_ANNOTATION, offset, offset).firstOrNull()
                        val url = when {
                            citation != null -> referenceLinkHandler.find(citation.item)
                            else -> value.getStringAnnotations(MARKDOWN_TAG_URL, offset, offset)
                                .reversed()
                                .firstOrNull()
                                ?.let { referenceLinkHandler.find(it.item) }
                        } ?: return@awaitEachGesture
                        down.consume()
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        up.consume()
                        if (citation != null) {
                            onCitationClick(url)
                        } else if (url.isArtifactPath()) {
                            onArtifact(url)
                        } else {
                            runCatching { uriHandler.openUri(url) }
                        }
                    }
                }
            } else {
                Modifier
            },
        )
    }
    if (hasCitations) {
        Box(Modifier.testTag(UiTags.CitationInline)) { textNode() }
    } else {
        textNode()
    }
}

private data class InlineCitation(val url: String, val title: String)

/** Recognizes `[citation: Title](https://…)` links produced by the Gateway. */
private fun ASTNode.citationChip(content: String): InlineCitation? {
    val isLink = type == MarkdownElementTypes.INLINE_LINK ||
        type == MarkdownElementTypes.SHORT_REFERENCE_LINK ||
        type == MarkdownElementTypes.FULL_REFERENCE_LINK
    if (!isLink) return null
    val linkText = findChildOfType(MarkdownElementTypes.LINK_TEXT) ?: return null
    val raw = linkText.children
        .dropBrackets()
        .joinToString(separator = "") { it.getUnescapedTextInNode(content) }
        .trim()
    if (!raw.startsWith("citation:", ignoreCase = true)) return null
    val url = findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getUnescapedTextInNode(content)
        ?: findChildOfType(MarkdownElementTypes.LINK_LABEL)?.getUnescapedTextInNode(content)
        ?: return null
    val domain = sourceDomain(url)
    val title = raw.substringAfter(':').trim()
        .takeUnless { it.equals("source", ignoreCase = true) || it == "来源" }
        .orEmpty()
        .ifBlank { domain }
    return InlineCitation(url = url, title = title)
}

private fun List<ASTNode>.dropBrackets(): List<ASTNode> =
    if (size >= 2 && first().type == org.intellij.markdown.MarkdownTokenTypes.LBRACKET) {
        subList(1, size - 1)
    } else {
        this
    }

@Composable
private fun MarkdownMessageImageFromNode(content: String, node: ASTNode, onArtifact: (String) -> Unit) {
    val destination = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
        ?.getUnescapedTextInNode(content)
    val title = node.findChildOfType(MarkdownElementTypes.LINK_TITLE)?.getUnescapedTextInNode(content)
    MarkdownMessageImage(destination, title, onArtifact)
}

private fun ASTNode.findChildOfTypeRecursive(type: org.intellij.markdown.IElementType): ASTNode? {
    children.forEach { child ->
        if (child.type == type) return child
        val nested = child.findChildOfTypeRecursive(type)
        if (nested != null) return nested
    }
    return null
}

// endregion

// region Block quote — intrinsic-height accent bar, all child block types

@Composable
private fun MarkdownRichQuote(content: String, node: ASTNode, style: TextStyle, onArtifact: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.children.forEach { child ->
                when (child.type) {
                    MarkdownElementTypes.PARAGRAPH -> MarkdownFlowParagraph(content, child, style, onArtifact)
                    MarkdownElementTypes.BLOCK_QUOTE -> MarkdownRichQuote(content, child, style, onArtifact)
                    MarkdownElementTypes.UNORDERED_LIST -> MarkdownBulletList(content, child, style)
                    MarkdownElementTypes.ORDERED_LIST -> MarkdownOrderedList(content, child, style)
                    MarkdownElementTypes.CODE_FENCE -> MarkdownCodeFence(content, child)
                    else -> MarkdownInlineText(content, child.children.toList(), style, onArtifact)
                }
            }
        }
    }
}

// endregion

private val enhancedTableCellWidth = 160.dp
private val enhancedTableDividerWidth = 0.5.dp

@Composable
private fun EnhancedMarkdownTable(content: String, node: ASTNode, style: TextStyle) {
    val header = node.findChildOfType(GFMElementTypes.HEADER) ?: return
    val columns = header.children.count { it.type == GFMTokenTypes.CELL }
    if (columns == 0) return
    val alignments = remember(content, node) { tableColumnAlignments(content, node, columns) }
    val dividerColor = LocalMarkdownColors.current.dividerColor
    val rows = node.children.filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = LocalMarkdownColors.current.tableBackground,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, dividerColor, MaterialTheme.shapes.small),
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            rows.forEachIndexed { index, row ->
                EnhancedMarkdownTableRow(
                    content = content,
                    row = row,
                    style = if (row.type == GFMElementTypes.HEADER) style.copy(fontWeight = FontWeight.Bold) else style,
                    alignments = alignments,
                    dividerColor = dividerColor,
                    drawBottomDivider = index < rows.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun EnhancedMarkdownTableRow(
    content: String,
    row: ASTNode,
    style: TextStyle,
    alignments: List<TextAlign>,
    dividerColor: Color,
    drawBottomDivider: Boolean,
) {
    // Draw the horizontal divider at the bottom of the row itself: the Row has a
    // determined width (sum of its cells), so drawLine spans the full table
    // width. The previous HorizontalDivider used fillMaxWidth(), which resolves
    // to 0 inside a horizontalScroll (unbounded width) and so never appeared.
    Row(
        Modifier
            .height(IntrinsicSize.Max)
            .drawBehind {
                if (drawBottomDivider) {
                    val sw = enhancedTableDividerWidth.toPx()
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, size.height - sw / 2f),
                        end = Offset(size.width, size.height - sw / 2f),
                        strokeWidth = sw,
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.children.filter { it.type == GFMTokenTypes.CELL }.forEachIndexed { column, cell ->
            if (column > 0) {
                Box(
                    Modifier
                        .width(enhancedTableDividerWidth)
                        .fillMaxHeight()
                        .background(dividerColor),
                )
            }
            MarkdownBasicText(
                text = content.buildMarkdownAnnotatedString(cell, style).trimCellText(),
                style = style,
                color = LocalMarkdownColors.current.tableText,
                textAlign = alignments.getOrElse(column) { TextAlign.Start },
                modifier = Modifier
                    .width(enhancedTableCellWidth)
                    .padding(LocalMarkdownDimens.current.tableCellPadding),
            )
        }
    }
}

/** GFM cell source ranges include surrounding padding spaces; trim them from the rendered text. */
private fun AnnotatedString.trimCellText(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() }
    return subSequence(start, end + 1)
}

/** Reads the GFM separator row (`:---`, `:---:`, `---:`) for per-column text alignment. */
internal fun tableColumnAlignments(content: String, table: ASTNode, columns: Int): List<TextAlign> {
    val separator = table.children.firstOrNull { it.type == GFMTokenTypes.TABLE_SEPARATOR }
        ?: return List(columns) { TextAlign.Start }
    val markers = content.substring(separator.startOffset, separator.endOffset)
        .split('|')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return List(columns) { index ->
        val marker = markers.getOrNull(index).orEmpty()
        when {
            marker.startsWith(":") && marker.endsWith(":") -> TextAlign.Center
            marker.endsWith(":") -> TextAlign.End
            else -> TextAlign.Start
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
        MarkdownCodeSurface(latex, "latex")
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

private fun paragraphSource(node: Node, markdown: String): String? {
    val spans = node.sourceSpans
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
private fun CitationSources(
    sources: List<CitationSource>,
    sourceRequesters: Map<String, BringIntoViewRequester>,
) {
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
        sources.forEachIndexed { index, source ->
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
            Box(
                modifier = (sourceRequesters[source.url]?.let { requester ->
                    Modifier.bringIntoViewRequester(requester)
                } ?: Modifier).testTag(UiTags.CitationSourcePrefix + index),
            ) {
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
}

internal fun markdownImageLabel(title: String?, destination: String?): String {
    val label = title?.takeIf { it.isNotBlank() } ?: destination.orEmpty()
    return label
}

internal fun citationSources(markdown: String): List<CitationSource> {
    return citationSources(linkedNodes(markdownParser.parse(markdown)))
}

internal fun citationPresentation(markdown: String): MarkdownCitationPresentation =
    markdownParser.parse(markdown).citationPresentation()

private fun Node.citationPresentation(): MarkdownCitationPresentation {
    val blocks = children().toList()
    for (index in blocks.indices.reversed()) {
        val heading = blocks[index] as? Heading ?: continue
        if (!heading.isSourcesHeading()) continue

        val sectionEnd = blocks.sectionEndAfter(index, heading.level)
        val sourceSectionSources = blocks.subList(index + 1, sectionEnd).sourceSectionSources()
        if (sourceSectionSources.isNotEmpty()) {
            val bodyNodes = buildList {
                addAll(blocks.subList(0, index))
                addAll(blocks.subList(sectionEnd, blocks.size))
            }
            return MarkdownCitationPresentation(
                bodyNodes = bodyNodes,
                sources = bodyNodes.citationSources().ifEmpty { sourceSectionSources },
                sourcesSectionStripped = true,
            )
        }
    }
    return MarkdownCitationPresentation(blocks, citationSources())
}

private fun Heading.isSourcesHeading(): Boolean = inlineText(this)
    .trim()
    .trimEnd(':')
    .trim()
    .equals("sources", ignoreCase = true)

private fun List<Node>.sectionEndAfter(startIndex: Int, level: Int): Int {
    for (index in startIndex + 1 until size) {
        val heading = this[index] as? Heading ?: continue
        if (heading.level <= level) return index
    }
    return size
}

private fun Iterable<Node>.citationSources(): List<CitationSource> =
    citationSources(asSequence().flatMap { linkedNodes(it) })

private fun Iterable<Node>.sourceSectionSources(): List<CitationSource> =
    sourceSectionSources(asSequence().flatMap { linkedNodes(it) })

private fun citationSources(links: Sequence<Link>): List<CitationSource> {
    return links
        .mapNotNull(Link::citationSource)
        .groupedSources()
}

private fun sourceSectionSources(links: Sequence<Link>): List<CitationSource> {
    return links
        .mapNotNull(Link::sourceSectionSource)
        .groupedSources()
}

private fun Sequence<CitationSource>.groupedSources(): List<CitationSource> {
    val sources = groupBy { it.url }
    return sources.values.map { matches ->
        val source = matches.first()
        source.copy(count = matches.size)
    }
}

private fun Link.sourceSectionSource(): CitationSource? {
    citationSource()?.let { return it }
    val url = destination.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
    val domain = sourceDomain(url)
    return CitationSource(
        title = inlineText(this).trim().ifBlank { domain },
        url = url,
        domain = domain,
        count = 1,
    )
}

private fun sourceDomain(url: String): String =
    runCatching { URI(url).host.removePrefix("www.") }.getOrNull().orEmpty().ifBlank { url }

private fun Node.citationSources(): List<CitationSource> = citationSources(linkedNodes(this))

private fun linkedNodes(node: Node): Sequence<Link> = sequence {
    if (node is Link) yield(node)
    node.children().forEach { child -> yieldAll(linkedNodes(child)) }
}

private fun Link.citationSource(): CitationSource? {
    val url = destination.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
    val rawTitle = inlineText(this).trim()
    if (!rawTitle.startsWith("citation:", ignoreCase = true)) return null
    val domain = sourceDomain(url)
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

internal data class CitationSource(val title: String, val url: String, val domain: String, val count: Int)

internal data class MarkdownCitationPresentation(
    val bodyNodes: List<Node>,
    val sources: List<CitationSource>,
    val sourcesSectionStripped: Boolean = false,
)

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
