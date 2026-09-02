package me.diluir.floatswitch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGestureRulesTest {
    @Test
    fun locked_shortTouch_performsClick() {
        assertTrue(
            OverlayGestureRules.shouldPerformClick(
                dragStarted = false,
                movedBeyondSlop = false,
                longPressDetected = false,
            ),
        )
    }

    @Test
    fun locked_longPress_doesNotStartDrag() {
        assertFalse(
            OverlayGestureRules.shouldStartDrag(
                positionLocked = true,
                movedBeyondSlop = false,
                hasActivePointer = true,
            ),
        )
        assertFalse(
            OverlayGestureRules.shouldPerformClick(
                dragStarted = false,
                movedBeyondSlop = false,
                longPressDetected = true,
            ),
        )
    }

    @Test
    fun movementBeyondTouchSlop_cancelsClick() {
        assertFalse(
            OverlayGestureRules.shouldPerformClick(
                dragStarted = false,
                movedBeyondSlop = true,
                longPressDetected = false,
            ),
        )
    }

    @Test
    fun unlockedLongPress_startsDrag() {
        assertTrue(
            OverlayGestureRules.shouldStartDrag(
                positionLocked = false,
                movedBeyondSlop = false,
                hasActivePointer = true,
            ),
        )
    }
}
