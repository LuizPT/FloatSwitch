package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAppearanceRulesTest {
    @Test
    fun defaults_match_current_overlay_appearance() {
        assertEquals(
            OverlayAppearance(
                buttonSizePercent = 100,
                buttonSpacing = OverlayButtonSpacing.NORMAL,
                backgroundEnabled = false,
                backgroundOpacityPercent = 50,
            ),
            OverlayAppearanceRules.defaultAppearance,
        )
    }

    @Test
    fun stored_values_are_restored() {
        assertEquals(
            OverlayAppearance(
                buttonSizePercent = 150,
                buttonSpacing = OverlayButtonSpacing.WIDE,
                backgroundEnabled = true,
                backgroundOpacityPercent = 80,
            ),
            OverlayAppearanceRules.fromStoredValues(150, null, "WIDE", true, 80),
        )
    }

    @Test
    fun invalid_values_fall_back_to_current_defaults() {
        assertEquals(
            OverlayAppearanceRules.defaultAppearance,
            OverlayAppearanceRules.fromStoredValues(55, "LARGE", "INVALID"),
        )
    }

    @Test
    fun missing_values_fall_back_to_current_defaults() {
        assertEquals(
            OverlayAppearanceRules.defaultAppearance,
            OverlayAppearanceRules.fromStoredValues(null, null, null),
        )
    }

    @Test
    fun legacy_sizes_are_migrated_to_nearest_ten_percent_step() {
        assertEquals(
            80,
            OverlayAppearanceRules.fromStoredValues(null, "SMALL", null).buttonSizePercent,
        )
        assertEquals(
            100,
            OverlayAppearanceRules.fromStoredValues(null, "MEDIUM", null).buttonSizePercent,
        )
        assertEquals(
            130,
            OverlayAppearanceRules.fromStoredValues(null, "LARGE", null).buttonSizePercent,
        )
    }

    @Test
    fun size_changes_use_ten_percent_steps_and_stop_at_bounds() {
        assertEquals(90, OverlayAppearanceRules.changeSize(100, -1))
        assertEquals(110, OverlayAppearanceRules.changeSize(100, 1))
        assertEquals(50, OverlayAppearanceRules.changeSize(50, -1))
        assertEquals(150, OverlayAppearanceRules.changeSize(150, 1))
    }

    @Test
    fun visual_size_scales_from_64_and_touch_target_never_drops_below_48() {
        assertEquals(32, OverlayAppearanceRules.visualSize(64, 50))
        assertEquals(64, OverlayAppearanceRules.visualSize(64, 100))
        assertEquals(96, OverlayAppearanceRules.visualSize(64, 150))
        assertEquals(48, OverlayAppearanceRules.touchTargetSize(32, 48))
        assertEquals(64, OverlayAppearanceRules.touchTargetSize(64, 48))
    }

    @Test
    fun invalid_background_values_fall_back_to_disabled_at_fifty_percent() {
        val appearance = OverlayAppearanceRules.fromStoredValues(
            buttonSizePercent = 100,
            legacyButtonSize = null,
            buttonSpacing = "NORMAL",
            backgroundEnabled = null,
            backgroundOpacityPercent = 55,
        )

        assertEquals(false, appearance.backgroundEnabled)
        assertEquals(50, appearance.backgroundOpacityPercent)
    }

    @Test
    fun background_opacity_changes_in_ten_percent_steps_and_stops_at_bounds() {
        assertEquals(50, OverlayAppearanceRules.changeBackgroundOpacity(60, -1))
        assertEquals(70, OverlayAppearanceRules.changeBackgroundOpacity(60, 1))
        assertEquals(10, OverlayAppearanceRules.changeBackgroundOpacity(10, -1))
        assertEquals(100, OverlayAppearanceRules.changeBackgroundOpacity(100, 1))
    }
}
