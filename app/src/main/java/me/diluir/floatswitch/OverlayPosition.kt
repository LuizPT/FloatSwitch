package me.diluir.floatswitch

import kotlin.math.abs
import kotlin.math.roundToInt

enum class OverlaySide {
    LEFT,
    RIGHT,
}

data class OverlayPosition(
    val side: OverlaySide,
    val verticalFraction: Float,
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
        side = OverlaySide.RIGHT,
        verticalFraction = 0.5f,
    )

    fun fromStoredValues(side: String?, verticalFraction: Float?): OverlayPosition {
        val validSide = runCatching { OverlaySide.valueOf(side.orEmpty()) }
            .getOrDefault(defaultPosition.side)
        val validFraction = when {
            verticalFraction == null || !verticalFraction.isFinite() -> {
                defaultPosition.verticalFraction
            }

            else -> verticalFraction.coerceIn(0f, 1f)
        }
        return OverlayPosition(validSide, validFraction)
    }

    fun sanitize(position: OverlayPosition): OverlayPosition = fromStoredValues(
        position.side.name,
        position.verticalFraction,
    )
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

    fun nearestSide(x: Int, bounds: OverlayMovementBounds): OverlaySide =
        if (abs(x - bounds.minX) <= abs(bounds.maxX - x)) {
            OverlaySide.LEFT
        } else {
            OverlaySide.RIGHT
        }

    fun xForSide(side: OverlaySide, bounds: OverlayMovementBounds): Int = when (side) {
        OverlaySide.LEFT -> bounds.minX
        OverlaySide.RIGHT -> bounds.maxX
    }

    fun normalizeVertical(y: Int, bounds: OverlayMovementBounds): Float {
        val verticalTravel = bounds.maxY - bounds.minY
        if (verticalTravel == 0) return 0f
        return (y.coerceIn(bounds.minY, bounds.maxY) - bounds.minY).toFloat() /
            verticalTravel.toFloat()
    }

    fun restoreVertical(verticalFraction: Float, bounds: OverlayMovementBounds): Int {
        val validFraction = OverlayPositionRules.fromStoredValues(
            side = OverlaySide.RIGHT.name,
            verticalFraction = verticalFraction,
        ).verticalFraction
        val verticalTravel = bounds.maxY - bounds.minY
        return bounds.minY + (verticalTravel * validFraction).roundToInt()
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
