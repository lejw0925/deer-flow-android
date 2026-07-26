package com.deerflow.mobile.ui

internal const val BROWSER_REMOTE_VIEWPORT_WIDTH = 1280f
internal const val BROWSER_REMOTE_VIEWPORT_HEIGHT = 720f
internal const val BROWSER_REMOTE_VIEWPORT_ASPECT_RATIO =
    BROWSER_REMOTE_VIEWPORT_WIDTH / BROWSER_REMOTE_VIEWPORT_HEIGHT

internal data class BrowserNormalizedPoint(
    val nx: Float,
    val ny: Float,
)

/** The portion of a Compose viewport occupied by a frame rendered with [ContentScale.Fit]. */
internal data class BrowserFrameBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun normalizedPointAt(x: Float, y: Float): BrowserNormalizedPoint? {
        if (!x.isFinite() || !y.isFinite() || x < left || y < top || x > left + width || y > top + height) {
            return null
        }
        return BrowserNormalizedPoint(
            nx = ((x - left) / width).coerceIn(0f, 1f),
            ny = ((y - top) / height).coerceIn(0f, 1f),
        )
    }
}

internal fun browserFrameBounds(
    containerWidth: Float,
    containerHeight: Float,
    frameWidth: Float = BROWSER_REMOTE_VIEWPORT_WIDTH,
    frameHeight: Float = BROWSER_REMOTE_VIEWPORT_HEIGHT,
): BrowserFrameBounds? {
    if (
        !containerWidth.isFinite() || !containerHeight.isFinite() ||
        !frameWidth.isFinite() || !frameHeight.isFinite() ||
        containerWidth <= 0f || containerHeight <= 0f || frameWidth <= 0f || frameHeight <= 0f
    ) {
        return null
    }

    val scale = minOf(containerWidth / frameWidth, containerHeight / frameHeight)
    val width = frameWidth * scale
    val height = frameHeight * scale
    return BrowserFrameBounds(
        left = (containerWidth - width) / 2f,
        top = (containerHeight - height) / 2f,
        width = width,
        height = height,
    )
}
