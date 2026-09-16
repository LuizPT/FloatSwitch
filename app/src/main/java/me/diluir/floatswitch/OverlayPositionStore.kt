package me.diluir.floatswitch

import android.content.Context
import androidx.core.content.edit

class OverlayPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): OverlayPosition {
        val storedEdge = preferences.safeGetString(KEY_EDGE)
        val storedEdgeFraction = preferences.safeGetFloat(KEY_EDGE_FRACTION)
        val position = OverlayPositionRules.fromStoredValues(
            edge = storedEdge,
            edgeFraction = storedEdgeFraction,
            legacySide = preferences.safeGetString(KEY_LEGACY_SIDE),
            legacyVerticalFraction = preferences.safeGetFloat(KEY_LEGACY_VERTICAL_FRACTION),
        )
        if (
            storedEdge != position.edge.name ||
            storedEdgeFraction != position.edgeFraction
        ) {
            preferences.edit {
                putString(KEY_EDGE, position.edge.name)
                putFloat(KEY_EDGE_FRACTION, position.edgeFraction)
            }
        }
        return position
    }

    fun save(position: OverlayPosition) {
        val validPosition = OverlayPositionRules.sanitize(position)
        preferences.edit {
            putString(KEY_EDGE, validPosition.edge.name)
            putFloat(KEY_EDGE_FRACTION, validPosition.edgeFraction)
        }
    }

    private fun android.content.SharedPreferences.safeGetString(key: String): String? =
        try {
            getString(key, null)
        } catch (_: ClassCastException) {
            null
        }

    private fun android.content.SharedPreferences.safeGetFloat(key: String): Float? =
        try {
            if (contains(key)) getFloat(key, DEFAULT_UNUSED_FRACTION) else null
        } catch (_: ClassCastException) {
            null
        }

    companion object {
        private const val PREFERENCES_NAME = "overlay_position"
        private const val KEY_EDGE = "edge"
        private const val KEY_EDGE_FRACTION = "edge_fraction"
        private const val KEY_LEGACY_SIDE = "side"
        private const val KEY_LEGACY_VERTICAL_FRACTION = "vertical_fraction"
        private const val DEFAULT_UNUSED_FRACTION = 0f
    }
}
