package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionMathTest {
    private val bounds = OverlayMovementBounds(
        minX = 12,
        maxX = 188,
        minY = 24,
        maxY = 276,
    )

    @Test
    fun nearestSide_snapsToLeftAndRight() {
        assertEquals(OverlaySide.LEFT, OverlayPositionMath.nearestSide(40, bounds))
        assertEquals(OverlaySide.RIGHT, OverlayPositionMath.nearestSide(170, bounds))
    }

    @Test
    fun clamp_limitsAllFourExtremes() {
        assertEquals(
            OverlayCoordinates(bounds.minX, bounds.minY),
            OverlayPositionMath.clamp(Int.MIN_VALUE, Int.MIN_VALUE, bounds),
        )
        assertEquals(
            OverlayCoordinates(bounds.maxX, bounds.maxY),
            OverlayPositionMath.clamp(Int.MAX_VALUE, Int.MAX_VALUE, bounds),
        )
        assertEquals(
            OverlayCoordinates(bounds.minX, 100),
            OverlayPositionMath.clamp(-1, 100, bounds),
        )
        assertEquals(
            OverlayCoordinates(bounds.maxX, 100),
            OverlayPositionMath.clamp(500, 100, bounds),
        )
    }

    @Test
    fun movementBounds_accountForInsetsMarginAndOverlaySize() {
        assertEquals(
            OverlayMovementBounds(
                minX = 22,
                maxX = 718,
                minY = 32,
                maxY = 428,
            ),
            OverlayPositionMath.movementBounds(
                screenLeft = 10,
                screenTop = 20,
                screenRight = 810,
                screenBottom = 570,
                overlayWidth = 80,
                overlayHeight = 130,
                margin = 12,
            ),
        )
    }

    @Test
    fun normalizeAndRestore_preserveVerticalPosition() {
        val y = 150
        val fraction = OverlayPositionMath.normalizeVertical(y, bounds)

        assertEquals(y, OverlayPositionMath.restoreVertical(fraction, bounds))
        assertEquals(0f, OverlayPositionMath.normalizeVertical(bounds.minY, bounds), 0f)
        assertEquals(1f, OverlayPositionMath.normalizeVertical(bounds.maxY, bounds), 0f)
    }

    @Test
    fun storedValues_areValidatedAndLimited() {
        assertEquals(
            OverlayPosition(OverlaySide.RIGHT, 0.5f),
            OverlayPositionRules.fromStoredValues("INVALID", Float.NaN),
        )
        assertEquals(
            OverlayPosition(OverlaySide.LEFT, 0f),
            OverlayPositionRules.fromStoredValues("LEFT", -2f),
        )
        assertEquals(
            OverlayPosition(OverlaySide.RIGHT, 1f),
            OverlayPositionRules.fromStoredValues("RIGHT", 3f),
        )
    }

    @Test
    fun zeroVerticalTravel_isStable() {
        val fixedBounds = OverlayMovementBounds(0, 100, 42, 42)

        assertEquals(0f, OverlayPositionMath.normalizeVertical(500, fixedBounds), 0f)
        assertEquals(42, OverlayPositionMath.restoreVertical(0.75f, fixedBounds))
    }
}
