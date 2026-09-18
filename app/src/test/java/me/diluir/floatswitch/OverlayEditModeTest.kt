package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayEditModeTest {
    @Test
    fun editMode_becomesVisibleAndExpiresAfterTimeout() {
        val state = OverlayEditModeState(timeoutMs = 5_000L)

        assertFalse(state.isVisibleAt(0L))
        state.activate(1_000L)
        assertTrue(state.isVisibleAt(5_999L))
        assertFalse(state.isVisibleAt(6_000L))
    }

    @Test
    fun interaction_restartsTimeoutWhileVisible() {
        val state = OverlayEditModeState(timeoutMs = 5_000L)

        state.activate(1_000L)
        state.recordInteraction(4_000L)

        assertTrue(state.isVisibleAt(8_999L))
        assertFalse(state.isVisibleAt(9_000L))
    }

    @Test
    fun hiddenEditMode_isNotReactivatedByInteraction() {
        val state = OverlayEditModeState(timeoutMs = 5_000L)

        state.recordInteraction(1_000L)

        assertFalse(state.isVisibleAt(1_001L))
        assertEquals(0L, state.remainingTimeMs(1_001L))
    }

    @Test
    fun longPress_accessIsIndependentFromPositionLock() {
        assertTrue(
            OverlayEditModeRules.shouldActivate(
                movedBeyondSlop = false,
                hasActivePointer = true,
            ),
        )
        assertFalse(
            OverlayGestureRules.shouldStartDrag(
                positionLocked = true,
                movedBeyondSlop = false,
                hasActivePointer = true,
            ),
        )
    }

    @Test
    fun movementBeforeLongPress_preventsEditMode() {
        assertFalse(
            OverlayEditModeRules.shouldActivate(
                movedBeyondSlop = true,
                hasActivePointer = true,
            ),
        )
    }

    @Test
    fun settingsButton_isPlacedInsideEachPhysicalEdge() {
        val bounds = OverlayMovementBounds(minX = 0, maxX = 452, minY = 0, maxY = 252)

        assertEquals(
            OverlayCoordinates(80, 76),
            positionFor(OverlayEdge.LEFT, overlayX = 12, overlayY = 50),
        )
        assertEquals(
            OverlayCoordinates(372, 76),
            positionFor(OverlayEdge.RIGHT, overlayX = 428, overlayY = 50),
        )
        assertEquals(
            OverlayCoordinates(76, 80),
            positionFor(OverlayEdge.TOP, overlayX = 50, overlayY = 12),
        )
        assertEquals(
            OverlayCoordinates(76, 172),
            positionFor(OverlayEdge.BOTTOM, overlayX = 50, overlayY = 228),
        )

        fun assertInside(position: OverlayCoordinates) {
            assertTrue(position.x in bounds.minX..bounds.maxX)
            assertTrue(position.y in bounds.minY..bounds.maxY)
        }
        assertInside(positionFor(OverlayEdge.LEFT, overlayX = 12, overlayY = 50))
        assertInside(positionFor(OverlayEdge.RIGHT, overlayX = 428, overlayY = 50))
        assertInside(positionFor(OverlayEdge.TOP, overlayX = 50, overlayY = 12))
        assertInside(positionFor(OverlayEdge.BOTTOM, overlayX = 50, overlayY = 228))
    }

    @Test
    fun settingsButton_isClampedInSmallBounds() {
        val position = OverlaySettingsShortcutMath.position(
            edge = OverlayEdge.BOTTOM,
            overlayX = 0,
            overlayY = 0,
            overlayWidth = 100,
            overlayHeight = 100,
            buttonWidth = 48,
            buttonHeight = 48,
            gap = 8,
            buttonBounds = OverlayMovementBounds(10, 10, 20, 20),
        )

        assertEquals(OverlayCoordinates(10, 20), position)
    }

    private fun positionFor(
        edge: OverlayEdge,
        overlayX: Int,
        overlayY: Int,
    ): OverlayCoordinates {
        val isVertical = edge == OverlayEdge.LEFT || edge == OverlayEdge.RIGHT
        return OverlaySettingsShortcutMath.position(
            edge = edge,
            overlayX = overlayX,
            overlayY = overlayY,
            overlayWidth = if (isVertical) 60 else 100,
            overlayHeight = if (isVertical) 100 else 60,
            buttonWidth = 48,
            buttonHeight = 48,
            gap = 8,
            buttonBounds = OverlayMovementBounds(0, 452, 0, 252),
        )
    }
}
