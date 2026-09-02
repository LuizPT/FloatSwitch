package me.diluir.floatswitch

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal interface BooleanPreferenceStorage {
    fun readBoolean(key: String, defaultValue: Boolean): Boolean

    fun writeBoolean(key: String, value: Boolean)
}

private class SharedPreferencesBooleanStorage(
    private val preferences: SharedPreferences,
) : BooleanPreferenceStorage {
    override fun readBoolean(key: String, defaultValue: Boolean): Boolean = runCatching {
        preferences.getBoolean(key, defaultValue)
    }.getOrDefault(defaultValue)

    override fun writeBoolean(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }
}

class PositionLockStore internal constructor(
    private val storage: BooleanPreferenceStorage,
) {
    constructor(context: Context) : this(
        SharedPreferencesBooleanStorage(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun isPositionLocked(): Boolean = storage.readBoolean(KEY_POSITION_LOCKED, false)

    fun setPositionLocked(locked: Boolean) {
        storage.writeBoolean(KEY_POSITION_LOCKED, locked)
    }

    companion object {
        internal const val PREFERENCES_NAME = "overlay_position_lock"
        internal const val KEY_POSITION_LOCKED = "position_locked"
    }
}
