package me.diluir.floatswitch

import android.content.Context
import androidx.core.content.edit
import kotlin.math.roundToInt

enum class OverlayButtonSpacing {
    COMPACT,
    NORMAL,
    WIDE,
}

data class OverlayAppearance(
    val buttonSizePercent: Int,
    val buttonSpacing: OverlayButtonSpacing,
    val backgroundEnabled: Boolean,
    val backgroundOpacityPercent: Int,
)

object OverlayAppearanceRules {
    const val MIN_SIZE_PERCENT = 50
    const val MAX_SIZE_PERCENT = 150
    const val SIZE_PERCENT_STEP = 10
    const val DEFAULT_SIZE_PERCENT = 100
    const val MIN_BACKGROUND_OPACITY_PERCENT = 10
    const val MAX_BACKGROUND_OPACITY_PERCENT = 100
    const val BACKGROUND_OPACITY_PERCENT_STEP = 10
    const val DEFAULT_BACKGROUND_OPACITY_PERCENT = 50

    val defaultAppearance = OverlayAppearance(
        buttonSizePercent = DEFAULT_SIZE_PERCENT,
        buttonSpacing = OverlayButtonSpacing.NORMAL,
        backgroundEnabled = false,
        backgroundOpacityPercent = DEFAULT_BACKGROUND_OPACITY_PERCENT,
    )

    fun fromStoredValues(
        buttonSizePercent: Int?,
        legacyButtonSize: String?,
        buttonSpacing: String?,
        backgroundEnabled: Boolean? = null,
        backgroundOpacityPercent: Int? = null,
    ): OverlayAppearance = OverlayAppearance(
        buttonSizePercent = (validSizePercent(buttonSizePercent)
            ?: (if (buttonSizePercent == null) migratedLegacySize(legacyButtonSize) else null)
            ?: DEFAULT_SIZE_PERCENT),
        buttonSpacing = runCatching {
            OverlayButtonSpacing.valueOf(buttonSpacing.orEmpty())
        }.getOrDefault(defaultAppearance.buttonSpacing),
        backgroundEnabled = backgroundEnabled ?: defaultAppearance.backgroundEnabled,
        backgroundOpacityPercent = validBackgroundOpacityPercent(backgroundOpacityPercent)
            ?: DEFAULT_BACKGROUND_OPACITY_PERCENT,
    )

    fun changeSize(currentPercent: Int, stepCount: Int): Int =
        (currentPercent + stepCount * SIZE_PERCENT_STEP).coerceIn(
            MIN_SIZE_PERCENT,
            MAX_SIZE_PERCENT,
        )

    fun visualSize(baseSize: Int, sizePercent: Int): Int =
        (baseSize * sizePercent / 100f).roundToInt()

    fun touchTargetSize(visualSize: Int, minimumTouchSize: Int): Int =
        maxOf(visualSize, minimumTouchSize)

    fun changeBackgroundOpacity(currentPercent: Int, stepCount: Int): Int =
        (currentPercent + stepCount * BACKGROUND_OPACITY_PERCENT_STEP).coerceIn(
            MIN_BACKGROUND_OPACITY_PERCENT,
            MAX_BACKGROUND_OPACITY_PERCENT,
        )

    private fun validSizePercent(value: Int?): Int? = value?.takeIf {
        it in MIN_SIZE_PERCENT..MAX_SIZE_PERCENT &&
            (it - MIN_SIZE_PERCENT) % SIZE_PERCENT_STEP == 0
    }

    private fun validBackgroundOpacityPercent(value: Int?): Int? = value?.takeIf {
        it in MIN_BACKGROUND_OPACITY_PERCENT..MAX_BACKGROUND_OPACITY_PERCENT &&
            it % BACKGROUND_OPACITY_PERCENT_STEP == 0
    }

    private fun migratedLegacySize(value: String?): Int? = when (value) {
        "SMALL" -> 80
        "MEDIUM" -> DEFAULT_SIZE_PERCENT
        "LARGE" -> 130
        else -> null
    }
}

class OverlayAppearanceStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): OverlayAppearance {
        val storedSizePercent = preferences.safeGetInt(KEY_BUTTON_SIZE_PERCENT)
        val storedBackgroundEnabled = preferences.safeGetBoolean(KEY_BACKGROUND_ENABLED)
        val storedBackgroundOpacityPercent = preferences.safeGetInt(
            KEY_BACKGROUND_OPACITY_PERCENT,
        )
        val appearance = OverlayAppearanceRules.fromStoredValues(
            buttonSizePercent = storedSizePercent,
            legacyButtonSize = preferences.safeGetString(KEY_LEGACY_BUTTON_SIZE),
            buttonSpacing = preferences.safeGetString(KEY_BUTTON_SPACING),
            backgroundEnabled = storedBackgroundEnabled,
            backgroundOpacityPercent = storedBackgroundOpacityPercent,
        )
        if (
            storedSizePercent != appearance.buttonSizePercent ||
            storedBackgroundEnabled != appearance.backgroundEnabled ||
            storedBackgroundOpacityPercent != appearance.backgroundOpacityPercent
        ) {
            preferences.edit {
                putInt(KEY_BUTTON_SIZE_PERCENT, appearance.buttonSizePercent)
                putBoolean(KEY_BACKGROUND_ENABLED, appearance.backgroundEnabled)
                putInt(
                    KEY_BACKGROUND_OPACITY_PERCENT,
                    appearance.backgroundOpacityPercent,
                )
            }
        }
        return appearance
    }

    fun setButtonSizePercent(buttonSizePercent: Int) {
        val validSizePercent = OverlayAppearanceRules.fromStoredValues(
            buttonSizePercent = buttonSizePercent,
            legacyButtonSize = null,
            buttonSpacing = null,
        ).buttonSizePercent
        preferences.edit { putInt(KEY_BUTTON_SIZE_PERCENT, validSizePercent) }
    }

    fun setButtonSpacing(buttonSpacing: OverlayButtonSpacing) =
        preferences.edit { putString(KEY_BUTTON_SPACING, buttonSpacing.name) }

    fun setBackgroundEnabled(backgroundEnabled: Boolean) =
        preferences.edit { putBoolean(KEY_BACKGROUND_ENABLED, backgroundEnabled) }

    fun setBackgroundOpacityPercent(backgroundOpacityPercent: Int) {
        val validOpacityPercent = OverlayAppearanceRules.fromStoredValues(
            buttonSizePercent = null,
            legacyButtonSize = null,
            buttonSpacing = null,
            backgroundOpacityPercent = backgroundOpacityPercent,
        ).backgroundOpacityPercent
        preferences.edit {
            putInt(KEY_BACKGROUND_OPACITY_PERCENT, validOpacityPercent)
        }
    }

    private fun android.content.SharedPreferences.safeGetInt(key: String): Int? =
        try {
            if (contains(key)) getInt(key, OverlayAppearanceRules.DEFAULT_SIZE_PERCENT) else null
        } catch (_: ClassCastException) {
            null
        }

    private fun android.content.SharedPreferences.safeGetString(key: String): String? =
        try {
            getString(key, null)
        } catch (_: ClassCastException) {
            null
        }

    private fun android.content.SharedPreferences.safeGetBoolean(key: String): Boolean? =
        try {
            if (contains(key)) getBoolean(key, false) else null
        } catch (_: ClassCastException) {
            null
        }

    companion object {
        private const val PREFERENCES_NAME = "overlay_appearance"
        private const val KEY_BUTTON_SIZE_PERCENT = "button_size_percent"
        private const val KEY_LEGACY_BUTTON_SIZE = "button_size"
        private const val KEY_BUTTON_SPACING = "button_spacing"
        private const val KEY_BACKGROUND_ENABLED = "background_enabled"
        private const val KEY_BACKGROUND_OPACITY_PERCENT = "background_opacity_percent"
    }
}
