package me.diluir.floatswitch

object OverlayGestureRules {
    fun shouldStartDrag(
        positionLocked: Boolean,
        movedBeyondSlop: Boolean,
        hasActivePointer: Boolean,
    ): Boolean = !positionLocked && !movedBeyondSlop && hasActivePointer

    fun shouldPerformClick(
        dragStarted: Boolean,
        movedBeyondSlop: Boolean,
        longPressDetected: Boolean,
    ): Boolean = !dragStarted && !movedBeyondSlop && !longPressDetected
}
