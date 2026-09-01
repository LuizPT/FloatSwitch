package me.diluir.floatswitch

import android.content.Context
import androidx.core.content.edit

class SelectedAppsStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(slot: AppSlot): SelectedApp? = SelectedAppCodec.decode(
        preferences.getString(slot.storageKey, null),
    )

    fun save(slot: AppSlot, selectedApp: SelectedApp) {
        preferences.edit {
            putString(slot.storageKey, SelectedAppCodec.encode(selectedApp))
        }
    }

    fun clear(slot: AppSlot) {
        preferences.edit {
            remove(slot.storageKey)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "selected_apps"
    }
}
