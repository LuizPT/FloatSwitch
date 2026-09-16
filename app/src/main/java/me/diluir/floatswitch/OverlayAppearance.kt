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
)

object OverlayAppearanceRules {
    const val MIN_SIZE_PERCENT = 50
    const val MAX_SIZE_PERCENT = 150
    const val SIZE_PERCENT_STEP = 10
    const val DEFAULT_SIZE_PERCENT = 100

    val defaultAppearance = OverlayAppearance(
        buttonSizePercent = DEFAULT_SIZE_PERCENT,
        buttonSpacing = OverlayButtonSpacing.NORMAL,
    )

    fun fromStoredValues(
        buttonSizePercent: Int?,
        legacyButtonSize: String?,
        buttonSpacing: String?,
    ): OverlayAppearance = OverlayAppearance(
        buttonSizePercent = (validSizePercent(buttonSizePercent)
            ?: (if (buttonSizePercent == null) migratedLegacySize(legacyButtonSize) else null)
            ?: DEFAULT_SIZE_PERCENT),
        buttonSpacing = runCatching {
            OverlayButtonSpacing.valueOf(buttonSpacing.orEmpty())
        }.getOrDefault(defaultAppearance.buttonSpacing),
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

    private fun validSizePercent(value: Int?): Int? = value?.takeIf {
        it in MIN_SIZE_PERCENT..MAX_SIZE_PERCENT &&
            (it - MIN_SIZE_PERCENT) % SIZE_PERCENT_STEP == 0
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
        val appearance = OverlayAppearanceRules.fromStoredValues(
            buttonSizePercent = storedSizePercent,
            legacyButtonSize = preferences.safeGetString(KEY_LEGACY_BUTTON_SIZE),
            buttonSpacing = preferences.safeGetString(KEY_BUTTON_SPACING),
        )
        if (storedSizePercent != appearance.buttonSizePercent) {
            preferences.edit {
                putInt(KEY_BUTTON_SIZE_PERCENT, appearance.buttonSizePercent)
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

    companion object {
        private const val PREFERENCES_NAME = "overlay_appearance"
        private const val KEY_BUTTON_SIZE_PERCENT = "button_size_percent"
        private const val KEY_LEGACY_BUTTON_SIZE = "button_size"
        private const val KEY_BUTTON_SPACING = "button_spacing"
    }
}
