package me.diluir.floatswitch

import android.content.Context
import androidx.core.content.edit

class AutoStartStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun isAutoStartEnabled(): Boolean = readBoolean(KEY_AUTO_START_ENABLED)

    fun setAutoStartEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTO_START_ENABLED, enabled) }
    }

    fun isOverlayRequestedActive(): Boolean = readBoolean(KEY_OVERLAY_REQUESTED_ACTIVE)

    fun setOverlayRequestedActive(active: Boolean) {
        preferences.edit { putBoolean(KEY_OVERLAY_REQUESTED_ACTIVE, active) }
    }

    fun loadDiagnostic(): AutoStartDiagnostic? {
        val event = runCatching {
            preferences.getString(KEY_LAST_EVENT, null)
        }.getOrNull()
        val timestamp = runCatching {
            preferences.getLong(KEY_LAST_EVENT_TIMESTAMP, 0L)
        }.getOrDefault(0L)
        val result = runCatching {
            preferences.getString(KEY_LAST_EVENT_RESULT, null)
        }.getOrNull()
        return AutoStartDiagnosticRules.fromStoredValues(event, timestamp, result)
    }

    fun recordDiagnostic(
        event: AutoStartEvent,
        result: AutoStartResult,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val diagnostic = AutoStartDiagnosticRules.fromStoredValues(
            event.name,
            timestampMillis,
            result.name,
        ) ?: return
        preferences.edit {
            putString(KEY_LAST_EVENT, diagnostic.event.name)
            putLong(KEY_LAST_EVENT_TIMESTAMP, diagnostic.timestampMillis)
            putString(KEY_LAST_EVENT_RESULT, diagnostic.result.name)
        }
    }

    private fun readBoolean(key: String): Boolean = runCatching {
        preferences.getBoolean(key, false)
    }.getOrDefault(false)

    companion object {
        private const val PREFERENCES_NAME = "auto_start_state"
        private const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
        private const val KEY_OVERLAY_REQUESTED_ACTIVE = "overlay_requested_active"
        private const val KEY_LAST_EVENT = "last_auto_start_event"
        private const val KEY_LAST_EVENT_TIMESTAMP = "last_auto_start_timestamp"
        private const val KEY_LAST_EVENT_RESULT = "last_auto_start_result"
    }
}
