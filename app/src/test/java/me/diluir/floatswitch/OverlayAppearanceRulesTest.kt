package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAppearanceRulesTest {
    @Test
    fun defaults_match_current_overlay_appearance() {
        assertEquals(
            OverlayAppearance(
                buttonSize = OverlayButtonSize.MEDIUM,
                buttonSpacing = OverlayButtonSpacing.NORMAL,
            ),
            OverlayAppearanceRules.defaultAppearance,
        )
    }

    @Test
    fun stored_values_are_restored() {
        assertEquals(
            OverlayAppearance(
                buttonSize = OverlayButtonSize.LARGE,
                buttonSpacing = OverlayButtonSpacing.WIDE,
            ),
            OverlayAppearanceRules.fromStoredValues("LARGE", "WIDE"),
        )
    }

    @Test
    fun invalid_values_fall_back_to_current_defaults() {
        assertEquals(
            OverlayAppearanceRules.defaultAppearance,
            OverlayAppearanceRules.fromStoredValues("INVALID", "INVALID"),
        )
    }

    @Test
    fun missing_values_fall_back_to_current_defaults() {
        assertEquals(
            OverlayAppearanceRules.defaultAppearance,
            OverlayAppearanceRules.fromStoredValues(null, null),
        )
    }
}
