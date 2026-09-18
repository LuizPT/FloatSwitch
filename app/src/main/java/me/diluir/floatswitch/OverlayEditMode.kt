package me.diluir.floatswitch

class OverlayEditModeState(
    private val timeoutMs: Long,
) {
    private var hideAtMs: Long? = null

    fun activate(nowMs: Long) {
        hideAtMs = nowMs + timeoutMs
    }

    fun recordInteraction(nowMs: Long) {
        if (hideAtMs != null) hideAtMs = nowMs + timeoutMs
    }

    fun hide() {
        hideAtMs = null
    }

    fun isVisibleAt(nowMs: Long): Boolean = hideAtMs?.let { nowMs < it } == true

    fun remainingTimeMs(nowMs: Long): Long =
        hideAtMs?.minus(nowMs)?.coerceAtLeast(0L) ?: 0L
}

object OverlayEditModeRules {
    fun shouldActivate(
        movedBeyondSlop: Boolean,
        hasActivePointer: Boolean,
    ): Boolean = !movedBeyondSlop && hasActivePointer
}

object OverlaySettingsShortcutMath {
    fun position(
        edge: OverlayEdge,
        overlayX: Int,
        overlayY: Int,
        overlayWidth: Int,
        overlayHeight: Int,
        buttonWidth: Int,
        buttonHeight: Int,
        gap: Int,
        buttonBounds: OverlayMovementBounds,
    ): OverlayCoordinates {
        val centredX = overlayX + (overlayWidth - buttonWidth) / 2
        val centredY = overlayY + (overlayHeight - buttonHeight) / 2
        val candidate = when (edge) {
            OverlayEdge.LEFT -> OverlayCoordinates(
                x = overlayX + overlayWidth + gap,
                y = centredY,
            )

            OverlayEdge.RIGHT -> OverlayCoordinates(
                x = overlayX - buttonWidth - gap,
                y = centredY,
            )

            OverlayEdge.TOP -> OverlayCoordinates(
                x = centredX,
                y = overlayY + overlayHeight + gap,
            )

            OverlayEdge.BOTTOM -> OverlayCoordinates(
                x = centredX,
                y = overlayY - buttonHeight - gap,
            )
        }
        return OverlayPositionMath.clamp(candidate.x, candidate.y, buttonBounds)
    }
}
