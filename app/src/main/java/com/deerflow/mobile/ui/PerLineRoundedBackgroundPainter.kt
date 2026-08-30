package com.deerflow.mobile.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpanPainter
import com.mikepenz.markdown.compose.extendedspans.SpanDrawInstructions

/**
 * Draws rounded backgrounds behind spans carrying a [SpanStyle.background] —
 * one pill PER LINE. A wrapped inline code span renders as stacked rounded
 * strips with a gap between lines instead of one merged gray block. The
 * background insets INWARD from the span bounds (no outward padding) and is
 * vertically centered inside the line box, pairing with a reduced span font.
 */
class PerLineRoundedBackgroundPainter(
    private val cornerRadius: TextUnit,
    private val horizontalInset: TextUnit = 2.sp,
    private val lineInset: TextUnit = 2.sp,
) : ExtendedSpanPainter() {
    private val path = Path()

    override fun decorate(
        span: SpanStyle,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): SpanStyle {
        if (span.background.isUnspecified) return span
        builder.addStringAnnotation(TAG, encodeColor(span.background), start, end)
        return span.copy(background = Color.Unspecified)
    }

    override fun decorate(
        linkAnnotation: LinkAnnotation,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): LinkAnnotation {
        val styles = linkAnnotation.styles
        val background = styles?.style?.background
        if (styles == null || background == null || background.isUnspecified) return linkAnnotation
        builder.addStringAnnotation(TAG, encodeColor(background), start, end)
        val updated = TextLinkStyles(
            style = styles.style?.copy(background = Color.Unspecified),
            focusedStyle = styles.focusedStyle?.copy(background = Color.Unspecified),
            hoveredStyle = styles.hoveredStyle?.copy(background = Color.Unspecified),
            pressedStyle = styles.pressedStyle?.copy(background = Color.Unspecified),
        )
        return when (linkAnnotation) {
            is LinkAnnotation.Url -> LinkAnnotation.Url(linkAnnotation.url, updated, linkAnnotation.linkInteractionListener)
            is LinkAnnotation.Clickable -> LinkAnnotation.Clickable(linkAnnotation.tag, updated, linkAnnotation.linkInteractionListener)
            else -> linkAnnotation
        }
    }

    override fun drawInstructionsFor(layoutResult: TextLayoutResult, color: Color?): SpanDrawInstructions {
        val text = layoutResult.layoutInput.text
        val annotations = text.getStringAnnotations(TAG, 0, text.length)
        return SpanDrawInstructions {
            val radius = CornerRadius(cornerRadius.toPx())
            val horizontalInsetPx = horizontalInset.toPx()
            val insetPx = lineInset.toPx()
            annotations.forEach { annotation ->
                val background = decodeColor(annotation.item)
                // flatten=false: one box per line of the span, each drawn as a
                // fully rounded pill inset inward from the span bounds; the
                // vertical inset separates stacked lines and keeps the pill
                // vertically centered in the line box.
                layoutResult.getBoundingBoxes(
                    startOffset = annotation.start,
                    endOffset = annotation.end,
                    flattenForFullParagraphs = false,
                ).forEach { box ->
                    path.rewind()
                    path.addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = box.copy(
                                left = box.left + horizontalInsetPx,
                                right = box.right - horizontalInsetPx,
                                top = box.top + insetPx,
                                bottom = box.bottom - insetPx,
                            ),
                            topLeft = radius,
                            topRight = radius,
                            bottomLeft = radius,
                            bottomRight = radius,
                        ),
                    )
                    drawPath(path, background, style = Fill)
                }
            }
        }
    }

    private companion object {
        private const val TAG = "perline_rounded_bg"

        private fun encodeColor(color: Color): String = color.value.toString()

        private fun decodeColor(raw: String): Color = Color(raw.toULong())
    }
}
