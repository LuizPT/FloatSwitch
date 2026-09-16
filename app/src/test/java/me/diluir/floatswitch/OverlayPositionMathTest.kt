package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPositionMathTest {
    private val bounds = OverlayMovementBounds(
        minX = 12,
        maxX = 188,
        minY = 24,
        maxY = 276,
    )

    @Test
    fun defaultPosition_isRightAndCentred() {
        assertEquals(
            OverlayPosition(OverlayEdge.RIGHT, 0.5f),
            OverlayPositionRules.defaultPosition,
        )
    }

    @Test
    fun legacyLeft_isMigratedWithoutLosingVerticalFraction() {
        assertEquals(
            OverlayPosition(OverlayEdge.LEFT, 0.25f),
            OverlayPositionRules.fromStoredValues(
                edge = null,
                edgeFraction = null,
                legacySide = "LEFT",
                legacyVerticalFraction = 0.25f,
            ),
        )
    }

    @Test
    fun legacyRight_isMigratedWithoutLosingVerticalFraction() {
        assertEquals(
            OverlayPosition(OverlayEdge.RIGHT, 0.75f),
            OverlayPositionRules.fromStoredValues(
                edge = null,
                edgeFraction = null,
                legacySide = "RIGHT",
                legacyVerticalFraction = 0.75f,
            ),
        )
    }

    @Test
    fun invalidStoredValues_areSanitized() {
        assertEquals(
            OverlayPosition(OverlayEdge.RIGHT, 0.5f),
            OverlayPositionRules.fromStoredValues("INVALID", Float.NaN),
        )
        assertEquals(
            OverlayPosition(OverlayEdge.TOP, 0f),
            OverlayPositionRules.fromStoredValues("TOP", -2f),
        )
        assertEquals(
            OverlayPosition(OverlayEdge.BOTTOM, 1f),
            OverlayPositionRules.fromStoredValues("BOTTOM", 3f),
        )
    }

    @Test
    fun nearestEdge_selectsAllFourEdges() {
        assertEquals(OverlayEdge.LEFT, OverlayPositionMath.nearestEdge(20, 150, bounds))
        assertEquals(OverlayEdge.RIGHT, OverlayPositionMath.nearestEdge(180, 150, bounds))
        assertEquals(OverlayEdge.TOP, OverlayPositionMath.nearestEdge(100, 30, bounds))
        assertEquals(OverlayEdge.BOTTOM, OverlayPositionMath.nearestEdge(100, 270, bounds))
    }

    @Test
    fun leftAndRightAreVertical_topAndBottomAreHorizontal() {
        assertEquals(
            OverlayLayoutAxis.VERTICAL,
            OverlayPositionMath.layoutAxisForEdge(OverlayEdge.LEFT),
        )
        assertEquals(
            OverlayLayoutAxis.VERTICAL,
            OverlayPositionMath.layoutAxisForEdge(OverlayEdge.RIGHT),
        )
        assertEquals(
            OverlayLayoutAxis.HORIZONTAL,
            OverlayPositionMath.layoutAxisForEdge(OverlayEdge.TOP),
        )
        assertEquals(
            OverlayLayoutAxis.HORIZONTAL,
            OverlayPositionMath.layoutAxisForEdge(OverlayEdge.BOTTOM),
        )
    }

    @Test
    fun snapPosition_returnsEachEdgeAndItsCorrectAxisFraction() {
        assertEquals(
            OverlayPosition(OverlayEdge.LEFT, 0.5f),
            OverlayPositionMath.snapPosition(20, 150, bounds),
        )
        assertEquals(
            OverlayPosition(OverlayEdge.RIGHT, 0.5f),
            OverlayPositionMath.snapPosition(180, 150, bounds),
        )
        assertEquals(
            OverlayPosition(OverlayEdge.TOP, 0.5f),
            OverlayPositionMath.snapPosition(100, 30, bounds),
        )
        assertEquals(
            OverlayPosition(OverlayEdge.BOTTOM, 0.5f),
            OverlayPositionMath.snapPosition(100, 270, bounds),
        )
    }

    @Test
    fun normalizeAndRestore_preserveHorizontalPositionForTopAndBottom() {
        val x = 100
        val fraction = OverlayPositionMath.normalizeHorizontal(x, bounds)

        assertEquals(x, OverlayPositionMath.restoreHorizontal(fraction, bounds))
        assertEquals(0f, OverlayPositionMath.normalizeHorizontal(bounds.minX, bounds), 0f)
        assertEquals(1f, OverlayPositionMath.normalizeHorizontal(bounds.maxX, bounds), 0f)
        assertEquals(
            fraction,
            OverlayPositionMath.normalizeForEdge(OverlayEdge.TOP, x, 200, bounds),
            0f,
        )
        assertEquals(
            fraction,
            OverlayPositionMath.normalizeForEdge(OverlayEdge.BOTTOM, x, 50, bounds),
            0f,
        )
    }

    @Test
    fun normalizeAndRestore_preserveVerticalPositionForLeftAndRight() {
        val y = 150
        val fraction = OverlayPositionMath.normalizeVertical(y, bounds)

        assertEquals(y, OverlayPositionMath.restoreVertical(fraction, bounds))
        assertEquals(0f, OverlayPositionMath.normalizeVertical(bounds.minY, bounds), 0f)
        assertEquals(1f, OverlayPositionMath.normalizeVertical(bounds.maxY, bounds), 0f)
        assertEquals(
            fraction,
            OverlayPositionMath.normalizeForEdge(OverlayEdge.LEFT, 100, y, bounds),
            0f,
        )
        assertEquals(
            fraction,
            OverlayPositionMath.normalizeForEdge(OverlayEdge.RIGHT, 50, y, bounds),
            0f,
        )
    }

    @Test
    fun restorePosition_reachesAllCorners() {
        assertEquals(
            OverlayCoordinates(bounds.minX, bounds.minY),
            OverlayPositionMath.restorePosition(OverlayPosition(OverlayEdge.LEFT, 0f), bounds),
        )
        assertEquals(
            OverlayCoordinates(bounds.minX, bounds.maxY),
            OverlayPositionMath.restorePosition(OverlayPosition(OverlayEdge.LEFT, 1f), bounds),
        )
        assertEquals(
            OverlayCoordinates(bounds.maxX, bounds.minY),
            OverlayPositionMath.restorePosition(OverlayPosition(OverlayEdge.TOP, 1f), bounds),
        )
        assertEquals(
            OverlayCoordinates(bounds.maxX, bounds.maxY),
            OverlayPositionMath.restorePosition(OverlayPosition(OverlayEdge.BOTTOM, 1f), bounds),
        )
    }

    @Test
    fun nearestEdge_hasStableCornerTieBreaking() {
        assertEquals(
            OverlayEdge.LEFT,
            OverlayPositionMath.nearestEdge(bounds.minX, bounds.minY, bounds),
        )
        assertEquals(
            OverlayEdge.RIGHT,
            OverlayPositionMath.nearestEdge(bounds.maxX, bounds.maxY, bounds),
        )
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
    fun boundsSmallerThanOverlay_produceStableFixedPosition() {
        val fixedBounds = OverlayPositionMath.movementBounds(
            screenLeft = 0,
            screenTop = 0,
            screenRight = 40,
            screenBottom = 30,
            overlayWidth = 100,
            overlayHeight = 80,
            margin = 12,
        )

        assertEquals(OverlayMovementBounds(0, 0, 0, 0), fixedBounds)
        assertEquals(0f, OverlayPositionMath.normalizeHorizontal(500, fixedBounds), 0f)
        assertEquals(0f, OverlayPositionMath.normalizeVertical(500, fixedBounds), 0f)
        assertEquals(0, OverlayPositionMath.restoreHorizontal(0.75f, fixedBounds))
        assertEquals(0, OverlayPositionMath.restoreVertical(0.75f, fixedBounds))
    }

    @Test
    fun appearanceReconstruction_preservesEdgeAndApproximateFraction() {
        val storedPosition = OverlayPosition(OverlayEdge.TOP, 0.35f)
        val boundsBefore = OverlayPositionMath.movementBounds(
            screenLeft = 0,
            screenTop = 0,
            screenRight = 1920,
            screenBottom = 720,
            overlayWidth = 300,
            overlayHeight = 72,
            margin = 12,
        )
        val boundsAfter = OverlayPositionMath.movementBounds(
            screenLeft = 0,
            screenTop = 0,
            screenRight = 1920,
            screenBottom = 720,
            overlayWidth = 500,
            overlayHeight = 112,
            margin = 12,
        )

        val before = OverlayPositionMath.restorePosition(storedPosition, boundsBefore)
        val after = OverlayPositionMath.restorePosition(storedPosition, boundsAfter)
        val beforeFraction = OverlayPositionMath.normalizeForEdge(
            storedPosition.edge,
            before.x,
            before.y,
            boundsBefore,
        )
        val afterFraction = OverlayPositionMath.normalizeForEdge(
            storedPosition.edge,
            after.x,
            after.y,
            boundsAfter,
        )

        assertEquals(boundsBefore.minY, before.y)
        assertEquals(boundsAfter.minY, after.y)
        assertTrue(kotlin.math.abs(beforeFraction - storedPosition.edgeFraction) < 0.001f)
        assertTrue(kotlin.math.abs(afterFraction - storedPosition.edgeFraction) < 0.001f)
    }
}
