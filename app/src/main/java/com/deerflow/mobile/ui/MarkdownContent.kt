package com.deerflow.mobile.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deerflow.mobile.R
import com.deerflow.mobile.ui.glass.glass
import com.deerflow.mobile.ui.glass.glassEdge
import com.deerflow.mobile.ui.glass.glassFrosted
import com.deerflow.mobile.ui.glass.rememberGlassTints
import com.deerflow.mobile.ui.theme.GeminiColors
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
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.CurrentComponentsBridge
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.drawBehind
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.utils.codeSpanStyle
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import io.ratex.RaTeXView
import java.net.URI
import java.util.UUID
import kotlin.math.roundToInt
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
import org.intellij.markdown.ast.getTextInNode
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
            EnhancedMarkdownContent(bodyMarkdown, Modifier, streaming, onArtifact, onCitationClick)
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

/** Shared extended-spans painter: per-line rounded backgrounds for inline code and citation chips. */
private fun perLineBackgroundPainter(density: androidx.compose.ui.unit.Density) = PerLineRoundedBackgroundPainter(
    cornerRadius = with(density) { 5.dp.toSp() },
    horizontalPadding = 3.sp,
    lineInset = 2.sp,
)

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
 * Top-level entry of the unified renderer. Delegates element dispatch to the
 * library's public [MarkdownElement] and wraps it with the three things chat
 * needs that the stock top level does not offer: block gaps via explicit
 * spacers (heading-aware rhythm, no dead space above the first line), a parse
 * tree remembered per content, and theme locals fed from our design system.
 */
@Composable
private fun EnhancedMarkdownContent(
    markdown: String,
    modifier: Modifier,
    streaming: Boolean,
    onArtifact: (String) -> Unit,
    onCitationClick: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val citationChip = SpanStyle(
        color = primary,
        background = primary.copy(alpha = 0.14f),
        fontWeight = FontWeight.Medium,
    )
    val referenceLinkHandler = remember { ReferenceLinkHandlerImpl() }
    val citationListener = remember(onCitationClick, referenceLinkHandler) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Clickable)?.tag ?: return@LinkInteractionListener
            onCitationClick(referenceLinkHandler.find(url).takeIf { it.isNotEmpty() } ?: url)
        }
    }
    // Inline-render hook: citation links become tinted chips that navigate to the
    // matching source row instead of opening a browser. Every other node falls
    // through to the library's default handling.
    val annotator = remember(citationChip, citationListener) {
        markdownAnnotator { content, node ->
            node.citationChip(content)?.let { citation ->
                pushStringAnnotation(CITATION_ANNOTATION, citation.url)
                withLink(
                    LinkAnnotation.Clickable(
                        tag = citation.url,
                        styles = TextLinkStyles(citationChip),
                        linkInteractionListener = citationListener,
                    )
                ) {
                    append(' ')
                    append(citation.title)
                    append(' ')
                }
                pop()
                true
            } ?: false
        }
    }
    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onSurface,
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
        tableBackground = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    // The m3 defaults size headings for full pages (h1 = displayLarge); chat bubbles
    // need the same compact scale the previous renderer used. h5/h6 drop to a muted
    // tone so deep hierarchies stay distinguishable in a bubble. Body styles share
    // a +10% line height tuned for reading long replies in a chat bubble.
    val chatBody = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.4.sp)
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
        text = chatBody,
        paragraph = chatBody,
        quote = chatBody,
        ordered = chatBody,
        bullet = chatBody,
        list = chatBody,
        // Inline code gets a text color that reads apart from body copy and links
        // (links stay primary): a light gray over the tinted rounded background.
        inlineCode = chatBody.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        textLink = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        ),
        table = MaterialTheme.typography.bodyMedium,
    )
    val components = if (streaming) streamingMarkdownComponents(onArtifact) else defaultMarkdownComponents(onArtifact)
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides referenceLinkHandler,
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides typography,
        LocalMarkdownPadding provides markdownPadding(),
        LocalMarkdownDimens provides markdownDimens(tableCellPadding = 12.dp),
        LocalImageTransformer provides NoOpImageTransformerImpl(),
        LocalMarkdownAnnotator provides annotator,
        LocalMarkdownExtendedSpans provides markdownExtendedSpans {
            // Library-rendered text (headings) gets the same rounded inline-code
            // treatment as the custom paragraph path.
            val density = LocalDensity.current
            ExtendedSpans(perLineBackgroundPainter(density))
        },
        LocalMarkdownComponents provides components,
        LocalMarkdownAnimations provides markdownAnimations(),
    ) {
        Column(modifier.fillMaxWidth().testTag(UiTags.EnhancedMarkdown)) {
            val tree = remember(markdown) { MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown) }
            var previousType: IElementType? = null
            tree.children.forEach { node ->
                val gap = blockGap(previousType, node.type)
                if (gap > 0.dp) Spacer(Modifier.height(gap))
                if (node.type == MarkdownElementTypes.LINK_DEFINITION) {
                    // Reference definitions have no visual output; the library stores
                    // them in its state layer, which this custom dispatcher bypasses.
                    val label = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)?.getUnescapedTextInNode(markdown)
                    if (label != null) {
                        val destination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                            ?.getUnescapedTextInNode(markdown)
                        referenceLinkHandler.store(label, destination)
                    }
                } else {
                    MarkdownElement(node = node, components = components, content = markdown, includeSpacer = false)
                }
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

private fun defaultMarkdownComponents(onArtifact: (String) -> Unit): MarkdownComponents = markdownComponents(
    codeBlock = highlightedCodeComponent,
    codeFence = highlightedCodeComponent,
    table = borderedTableComponent,
    paragraph = { model -> MarkdownFlowParagraph(model.content, model.node, model.typography.paragraph, onArtifact) },
    image = { model -> MarkdownMessageImageFromNode(model.content, model.node, onArtifact) },
    blockQuote = { model -> MarkdownRichQuote(model.content, model.node, model.typography.quote, onArtifact) },
    checkbox = { model -> MarkdownTaskCheckbox(model.content, model.node) },
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
    checkbox = { model -> MarkdownTaskCheckbox(model.content, model.node) },
)

/** Task-list marker rendered as a checkbox icon aligned with the first text line. */
@Composable
private fun MarkdownTaskCheckbox(content: String, node: ASTNode) {
    val marker = node.getTextInNode(content).trim().toString()
    val checked = marker.equals("[x]", ignoreCase = true)
    Icon(
        if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
        contentDescription = null,
        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 3.dp).size(18.dp),
    )
}

/** Wraps a markdown component so elements that appear mid-stream blur-fade in once. */
private fun revealOnAppear(component: MarkdownComponent): MarkdownComponent = { model ->
    StreamingReveal(animate = true) { component(model) }
}

/** Renders GFM tables with full cell borders and separator-row column alignment. */
private val borderedTableComponent: MarkdownComponent = { model ->
    EnhancedMarkdownTable(model.content, model.node, model.typography.table)
}

// region Code blocks — highlighted content with a language + copy header

/** Fenced and indented code share the same chrome; both carry a language hint when present. */
private val highlightedCodeComponent: MarkdownComponent = { model ->
    MarkdownCodeContent(model.content, model.node) { code, language, _ -> MarkdownCodeSurface(code, language) }
}

@Composable
private fun MarkdownCodeContent(
    content: String,
    node: ASTNode,
    block: @Composable (String, String?, TextStyle) -> Unit,
) {
    if (node.type == MarkdownElementTypes.CODE_FENCE) {
        MarkdownCodeFence(content, node, block = block)
    } else {
        MarkdownCodeBlock(content, node, block = block)
    }
}

/**
 * Chat code block: syntax-highlighted content that scrolls horizontally instead of
 * wrapping, under a header naming the language with a one-tap copy action.
 */
@Composable
internal fun MarkdownCodeSurface(code: String, language: String?) {
    val codeBackground = LocalMarkdownColors.current.codeBackground
    val codeText = LocalMarkdownColors.current.text
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
    Surface(color = codeBackground, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
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

// region Paragraph — display math, block-split images, citation chips

@Composable
private fun MarkdownFlowParagraph(content: String, node: ASTNode, style: TextStyle, onArtifact: (String) -> Unit) {
    val raw = content.substring(node.startOffset, node.endOffset.coerceAtMost(content.length))
    // Quote continuation markers ("> " prefixes) would hide the math delimiters.
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
    val linkStyle = LocalMarkdownTypography.current.textLink
    val codeSpanStyle = LocalMarkdownTypography.current.codeSpanStyle
    val annotator = LocalMarkdownAnnotator.current
    val referenceLinkHandler = LocalReferenceLinkHandler.current
    val uriHandler = LocalUriHandler.current
    // Links carry native [LinkAnnotation]s: clicks resolve through the reference
    // handler, and artifact paths route into the artifact viewer instead of a
    // browser. Text stays selectable inside SelectionContainer.
    val linkListener = remember(referenceLinkHandler, uriHandler, onArtifact) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            val resolved = referenceLinkHandler.find(url).takeIf { it.isNotEmpty() } ?: url
            if (resolved.isArtifactPath()) {
                onArtifact(resolved)
            } else {
                runCatching { uriHandler.openUri(resolved) }
            }
        }
    }
    val settings = remember(linkStyle, codeSpanStyle, annotator, referenceLinkHandler, linkListener) {
        DefaultAnnotatorSettings(
            linkTextSpanStyle = linkStyle,
            codeSpanStyle = codeSpanStyle,
            annotator = annotator,
            referenceLinkHandler = referenceLinkHandler,
            linkInteractionListener = linkListener,
        )
    }
    val density = LocalDensity.current
    // Per-line rounded backgrounds for spans that carry one (inline code,
    // citation chips): wrapped spans render as one rounded strip per line with a
    // gap between lines instead of a merged block.
    val extendedSpans = remember { ExtendedSpans(perLineBackgroundPainter(density)) }
    val value = remember(content, children, settings, style) {
        buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            buildMarkdownAnnotatedString(content = content, children = children, annotatorSettings = settings)
            pop()
        }
    }.let { annotated ->
        // Markdown paragraphs never render meaningful leading whitespace, so trim it.
        val firstNonWhitespace = annotated.text.indexOfFirst { !it.isWhitespace() }
        when {
            annotated.text.isEmpty() -> null
            firstNonWhitespace > 0 -> annotated.subSequence(firstNonWhitespace, annotated.length)
            else -> annotated
        }
    } ?: return
    // extend() must see the final string (it stamps a marker at offset 0 that
    // onTextLayout validates), so it runs after the trim above.
    val extended = remember(value, extendedSpans) { extendedSpans.extend(value) }
    val hasCitations = extended.getStringAnnotations(CITATION_ANNOTATION, 0, extended.length).isNotEmpty()
    val textNode: @Composable () -> Unit = {
        Text(
            text = extended,
            style = style,
            color = LocalMarkdownColors.current.text,
            onTextLayout = { extendedSpans.onTextLayout(it) },
            modifier = Modifier.drawBehind(extendedSpans),
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
    val cellSettings = DefaultAnnotatorSettings(
        linkTextSpanStyle = LocalMarkdownTypography.current.textLink,
        codeSpanStyle = LocalMarkdownTypography.current.codeSpanStyle,
        annotator = LocalMarkdownAnnotator.current,
        referenceLinkHandler = LocalReferenceLinkHandler.current,
    )
    val density = LocalDensity.current
    val cellSpans = remember(density) { ExtendedSpans(perLineBackgroundPainter(density)) }
    val rows = node.children.filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = LocalMarkdownColors.current.tableBackground,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, dividerColor, MaterialTheme.shapes.medium),
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
                    cellSettings = cellSettings,
                    cellSpans = cellSpans,
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
    cellSettings: DefaultAnnotatorSettings,
    cellSpans: ExtendedSpans,
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
            val cellText = buildAnnotatedString {
                pushStyle(style.toSpanStyle())
                buildMarkdownAnnotatedString(content = content, node = cell, annotatorSettings = cellSettings)
                pop()
            }.trimCellText()
            MarkdownBasicText(
                text = remember(cellText, cellSpans) { cellSpans.extend(cellText) },
                style = style,
                color = LocalMarkdownColors.current.text,
                textAlign = alignments.getOrElse(column) { TextAlign.Start },
                onTextLayout = { cellSpans.onTextLayout(it) },
                modifier = Modifier
                    .width(enhancedTableCellWidth)
                    .drawBehind(cellSpans)
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
                .heightIn(min = 32.dp)
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

/**
 * Hosts citation source cards OUTSIDE the recorded conversation layer so they
 * can be real backdrop-sampling liquid glass. [CitationSources] renders an
 * invisible in-flow card that reserves the exact layout space and reports its
 * bounds; [CitationCardOverlayHost] draws the glass card over it. Screens
 * without a host (tests, sheets) fall back to the frosted in-flow card.
 */
@Stable
class CitationCardHostState internal constructor() {
    internal val entries = mutableStateMapOf<String, CitationCardEntry>()

    internal fun put(key: String, entry: CitationCardEntry) {
        entries[key] = entry
    }

    internal fun remove(key: String) {
        entries.remove(key)
    }
}

@Stable
internal class CitationCardEntry {
    var bounds by mutableStateOf<Rect?>(null)
    var content: (@Composable () -> Unit)? = null
}

val LocalCitationCardHost = compositionLocalOf<CitationCardHostState?> { null }

@Composable
fun rememberCitationCardHostState(): CitationCardHostState = remember { CitationCardHostState() }

/** Draws one real-glass card per registered citation entry, anchored to its in-flow placeholder. */
@Composable
fun CitationCardOverlayHost(state: CitationCardHostState, modifier: Modifier = Modifier) {
    if (state.entries.isEmpty()) return
    var overlayBounds by remember { mutableStateOf<Rect?>(null) }
    Box(modifier.fillMaxSize().onGloballyPositioned { overlayBounds = it.boundsInRoot() }) {
        val overlay = overlayBounds ?: return@Box
        val density = LocalDensity.current
        val shape = MaterialTheme.shapes.medium
        val tints = rememberGlassTints()
        state.entries.forEach { (_, entry) ->
            val bounds = entry.bounds ?: return@forEach
            val content = entry.content ?: return@forEach
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (bounds.left - overlay.left).roundToInt(),
                            (bounds.top - overlay.top).roundToInt(),
                        )
                    }
                    .width(with(density) { bounds.width.toDp() })
                    .glass(shape, tint = tints.veil, useLens = true)
                    .glassEdge(shape),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CitationSources(
    sources: List<CitationSource>,
    sourceRequesters: Map<String, BringIntoViewRequester>,
) {
    if (sources.isEmpty()) return
    val host = LocalCitationCardHost.current
    if (host == null) {
        // Fallback for screens without an overlay host: frosted in-flow card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassFrosted(MaterialTheme.shapes.medium)
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            0f to GeminiColors.Blue.copy(alpha = 0.06f),
                            0.5f to GeminiColors.Violet.copy(alpha = 0.06f),
                            1f to GeminiColors.Pink.copy(alpha = 0.06f),
                        ),
                    )
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SourcesCardContent(sources, sourceRequesters, interactive = true)
        }
        return
    }
    // Real liquid glass: an invisible in-flow copy reserves the exact space (and
    // keeps bring-into-view working), while the overlay host draws the glass card.
    val key = remember { UUID.randomUUID().toString() }
    DisposableEffect(Unit) { onDispose { host.remove(key) } }
    val entry = remember { CitationCardEntry() }
    entry.content = { SourcesCardContent(sources, sourceRequesters, interactive = false) }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                if (entry.bounds != bounds) entry.bounds = bounds
            }
            .graphicsLayer { alpha = 0f },
    ) {
        SourcesCardContent(sources, sourceRequesters, interactive = false)
    }
    SideEffect { host.put(key, entry) }
}

@Composable
private fun SourcesCardContent(
    sources: List<CitationSource>,
    sourceRequesters: Map<String, BringIntoViewRequester>,
    interactive: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        // Card padding lives here so the frosted fallback, the invisible
        // in-flow placeholder, and the glass overlay card all share one geometry.
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.citation_sources, sources.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sources.forEachIndexed { index, source ->
            val link = remember(source, primary, interactive) {
                buildAnnotatedString {
                    val titleStyle = SpanStyle(color = primary, textDecoration = TextDecoration.Underline)
                    if (interactive) {
                        withLink(
                            LinkAnnotation.Url(
                                url = source.url,
                                styles = TextLinkStyles(titleStyle),
                            )
                        ) {
                            append(source.title)
                        }
                    } else {
                        pushStyle(titleStyle)
                        append(source.title)
                        pop()
                    }
                    append(" · ${source.domain}")
                    if (source.count > 1) append(" ×${source.count}")
                }
            }
            Box(
                modifier = if (interactive) {
                    (sourceRequesters[source.url]?.let { requester ->
                        Modifier.bringIntoViewRequester(requester)
                    } ?: Modifier).testTag(UiTags.CitationSourcePrefix + index)
                } else {
                    Modifier
                },
            ) {
                Text(
                    link,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
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

private fun Node.children(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}
