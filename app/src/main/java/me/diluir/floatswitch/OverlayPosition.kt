package me.diluir.floatswitch

import kotlin.math.abs
import kotlin.math.roundToInt

enum class OverlayEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

enum class OverlayLayoutAxis {
    VERTICAL,
    HORIZONTAL,
}

data class OverlayPosition(
    val edge: OverlayEdge,
    val edgeFraction: Float,
)

data class OverlayMovementBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
) {
    init {
        require(minX <= maxX)
        require(minY <= maxY)
    }
}

data class OverlayCoordinates(
    val x: Int,
    val y: Int,
)

object OverlayPositionRules {
    val defaultPosition = OverlayPosition(
        edge = OverlayEdge.RIGHT,
        edgeFraction = 0.5f,
    )

    fun fromStoredValues(
        edge: String?,
        edgeFraction: Float?,
        legacySide: String? = null,
        legacyVerticalFraction: Float? = null,
    ): OverlayPosition {
        val validEdge = if (edge == null) {
            migrateLegacySide(legacySide)
        } else {
            parseEdge(edge)
        } ?: defaultPosition.edge
        val storedFraction = edgeFraction
            ?: if (edge == null) legacyVerticalFraction else null
        val validFraction = sanitizeFraction(storedFraction)
        return OverlayPosition(validEdge, validFraction)
    }

    fun sanitize(position: OverlayPosition): OverlayPosition = OverlayPosition(
        edge = position.edge,
        edgeFraction = sanitizeFraction(position.edgeFraction),
    )

    fun sanitizeFraction(fraction: Float?): Float = when {
        fraction == null || !fraction.isFinite() -> defaultPosition.edgeFraction
        else -> fraction.coerceIn(0f, 1f)
    }

    private fun parseEdge(value: String): OverlayEdge? =
        runCatching { OverlayEdge.valueOf(value) }.getOrNull()

    private fun migrateLegacySide(value: String?): OverlayEdge? = when (value) {
        OverlayEdge.LEFT.name -> OverlayEdge.LEFT
        OverlayEdge.RIGHT.name -> OverlayEdge.RIGHT
        else -> null
    }
}

object OverlayPositionMath {
    fun movementBounds(
        screenLeft: Int,
        screenTop: Int,
        screenRight: Int,
        screenBottom: Int,
        overlayWidth: Int,
        overlayHeight: Int,
        margin: Int,
    ): OverlayMovementBounds {
        val horizontal = axisBounds(screenLeft, screenRight, overlayWidth, margin)
        val vertical = axisBounds(screenTop, screenBottom, overlayHeight, margin)
        return OverlayMovementBounds(
            minX = horizontal.first,
            maxX = horizontal.second,
            minY = vertical.first,
            maxY = vertical.second,
        )
    }

    fun clamp(
        x: Int,
        y: Int,
        bounds: OverlayMovementBounds,
    ): OverlayCoordinates = OverlayCoordinates(
        x = x.coerceIn(bounds.minX, bounds.maxX),
        y = y.coerceIn(bounds.minY, bounds.maxY),
    )

    fun nearestEdge(
        x: Int,
        y: Int,
        bounds: OverlayMovementBounds,
    ): OverlayEdge {
        val position = clamp(x, y, bounds)
        return listOf(
            OverlayEdge.LEFT to abs(position.x - bounds.minX),
            OverlayEdge.RIGHT to abs(bounds.maxX - position.x),
            OverlayEdge.TOP to abs(position.y - bounds.minY),
            OverlayEdge.BOTTOM to abs(bounds.maxY - position.y),
        ).minBy { it.second }.first
    }

    fun snapPosition(
        x: Int,
        y: Int,
        bounds: OverlayMovementBounds,
    ): OverlayPosition {
        val limitedPosition = clamp(x, y, bounds)
        val edge = nearestEdge(limitedPosition.x, limitedPosition.y, bounds)
        return OverlayPosition(
            edge = edge,
            edgeFraction = normalizeForEdge(
                edge,
                limitedPosition.x,
                limitedPosition.y,
                bounds,
            ),
        )
    }

    fun layoutAxisForEdge(edge: OverlayEdge): OverlayLayoutAxis = when (edge) {
        OverlayEdge.LEFT,
        OverlayEdge.RIGHT,
        -> OverlayLayoutAxis.VERTICAL

        OverlayEdge.TOP,
        OverlayEdge.BOTTOM,
        -> OverlayLayoutAxis.HORIZONTAL
    }

    fun normalizeForEdge(
        edge: OverlayEdge,
        x: Int,
        y: Int,
        bounds: OverlayMovementBounds,
    ): Float = when (edge) {
        OverlayEdge.LEFT,
        OverlayEdge.RIGHT,
        -> normalizeVertical(y, bounds)

        OverlayEdge.TOP,
        OverlayEdge.BOTTOM,
        -> normalizeHorizontal(x, bounds)
    }

    fun restorePosition(
        position: OverlayPosition,
        bounds: OverlayMovementBounds,
    ): OverlayCoordinates {
        val validPosition = OverlayPositionRules.sanitize(position)
        return when (validPosition.edge) {
            OverlayEdge.LEFT -> OverlayCoordinates(
                bounds.minX,
                restoreVertical(validPosition.edgeFraction, bounds),
            )

            OverlayEdge.RIGHT -> OverlayCoordinates(
                bounds.maxX,
                restoreVertical(validPosition.edgeFraction, bounds),
            )

            OverlayEdge.TOP -> OverlayCoordinates(
                restoreHorizontal(validPosition.edgeFraction, bounds),
                bounds.minY,
            )

            OverlayEdge.BOTTOM -> OverlayCoordinates(
                restoreHorizontal(validPosition.edgeFraction, bounds),
                bounds.maxY,
            )
        }
    }

    fun normalizeHorizontal(x: Int, bounds: OverlayMovementBounds): Float =
        normalizeAxis(x, bounds.minX, bounds.maxX)

    fun restoreHorizontal(edgeFraction: Float, bounds: OverlayMovementBounds): Int =
        restoreAxis(edgeFraction, bounds.minX, bounds.maxX)

    fun normalizeVertical(y: Int, bounds: OverlayMovementBounds): Float =
        normalizeAxis(y, bounds.minY, bounds.maxY)

    fun restoreVertical(edgeFraction: Float, bounds: OverlayMovementBounds): Int =
        restoreAxis(edgeFraction, bounds.minY, bounds.maxY)

    private fun normalizeAxis(value: Int, minimum: Int, maximum: Int): Float {
        val travel = maximum - minimum
        if (travel == 0) return 0f
        return (value.coerceIn(minimum, maximum) - minimum).toFloat() / travel.toFloat()
    }

    private fun restoreAxis(edgeFraction: Float, minimum: Int, maximum: Int): Int {
        val validFraction = OverlayPositionRules.sanitizeFraction(edgeFraction)
        val travel = maximum - minimum
        return minimum + (travel * validFraction).roundToInt()
    }

    private fun axisBounds(
        screenStart: Int,
        screenEnd: Int,
        overlaySize: Int,
        margin: Int,
    ): Pair<Int, Int> {
        val lastFullyVisiblePosition = (screenEnd - overlaySize).coerceAtLeast(screenStart)
        val preferredStart = screenStart + margin
        val preferredEnd = lastFullyVisiblePosition - margin
        return if (preferredStart <= preferredEnd) {
            preferredStart to preferredEnd
        } else {
            val centredPosition = screenStart +
                ((lastFullyVisiblePosition - screenStart) / 2)
            centredPosition to centredPosition
        }
    }
}
