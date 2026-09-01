package me.diluir.floatswitch

import android.content.Context
import androidx.core.content.edit

class OverlayPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): OverlayPosition {
        val storedSide = runCatching {
            preferences.getString(KEY_SIDE, null)
        }.getOrNull()
        val storedFraction = runCatching {
            if (preferences.contains(KEY_VERTICAL_FRACTION)) {
                preferences.getFloat(KEY_VERTICAL_FRACTION, DEFAULT_UNUSED_FRACTION)
            } else {
                null
            }
        }.getOrNull()
        return OverlayPositionRules.fromStoredValues(storedSide, storedFraction)
    }

    fun save(position: OverlayPosition) {
        val validPosition = OverlayPositionRules.sanitize(position)
        preferences.edit {
            putString(KEY_SIDE, validPosition.side.name)
            putFloat(KEY_VERTICAL_FRACTION, validPosition.verticalFraction)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "overlay_position"
        private const val KEY_SIDE = "side"
        private const val KEY_VERTICAL_FRACTION = "vertical_fraction"
        private const val DEFAULT_UNUSED_FRACTION = 0f
    }
}
