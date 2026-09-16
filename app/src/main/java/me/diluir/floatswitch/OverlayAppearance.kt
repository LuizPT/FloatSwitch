package me.diluir.floatswitch

import android.content.Context

enum class OverlayButtonSize {
    SMALL,
    MEDIUM,
    LARGE,
}

enum class OverlayButtonSpacing {
    COMPACT,
    NORMAL,
    WIDE,
}

data class OverlayAppearance(
    val buttonSize: OverlayButtonSize,
    val buttonSpacing: OverlayButtonSpacing,
)

object OverlayAppearanceRules {
    val defaultAppearance = OverlayAppearance(
        buttonSize = OverlayButtonSize.MEDIUM,
        buttonSpacing = OverlayButtonSpacing.NORMAL,
    )

    fun fromStoredValues(
        buttonSize: String?,
        buttonSpacing: String?,
    ): OverlayAppearance = OverlayAppearance(
        buttonSize = runCatching {
            OverlayButtonSize.valueOf(buttonSize.orEmpty())
        }.getOrDefault(defaultAppearance.buttonSize),
        buttonSpacing = runCatching {
            OverlayButtonSpacing.valueOf(buttonSpacing.orEmpty())
        }.getOrDefault(defaultAppearance.buttonSpacing),
    )
}

class OverlayAppearanceStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): OverlayAppearance = OverlayAppearanceRules.fromStoredValues(
        buttonSize = preferences.getString(KEY_BUTTON_SIZE, null),
        buttonSpacing = preferences.getString(KEY_BUTTON_SPACING, null),
    )

    fun setButtonSize(buttonSize: OverlayButtonSize) {
        preferences.edit()
            .putString(KEY_BUTTON_SIZE, buttonSize.name)
            .apply()
    }

    fun setButtonSpacing(buttonSpacing: OverlayButtonSpacing) {
        preferences.edit()
            .putString(KEY_BUTTON_SPACING, buttonSpacing.name)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "overlay_appearance"
        private const val KEY_BUTTON_SIZE = "button_size"
        private const val KEY_BUTTON_SPACING = "button_spacing"
    }
}
