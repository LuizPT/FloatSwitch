package me.diluir.floatswitch

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

class SelectedAppsStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    init {
        migrateLegacySelectionsIfNeeded()
    }

    fun load(): List<SelectedApp> {
        val stored = readCurrentSelections()
        val normalized = SelectedAppsRules.sanitize(stored)
        if (normalized != stored) save(normalized)
        return normalized
    }

    fun save(applications: List<SelectedApp>): List<SelectedApp> {
        val normalized = SelectedAppsRules.sanitize(applications)
        writeSelections(normalized, commit = false)
        return normalized
    }

    fun add(application: SelectedApp): List<SelectedApp> =
        save(SelectedAppsRules.add(load(), application))

    fun replace(index: Int, application: SelectedApp): List<SelectedApp> =
        save(SelectedAppsRules.replace(load(), index, application))

    fun removeAt(index: Int): List<SelectedApp> =
        save(SelectedAppsRules.removeAt(load(), index))

    fun move(fromIndex: Int, toIndex: Int): List<SelectedApp> =
        save(SelectedAppsRules.move(load(), fromIndex, toIndex))

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun migrateLegacySelectionsIfNeeded() {
        if (preferences.contains(KEY_SCHEMA_VERSION)) return

        val legacyValues = AppSlot.entries.map { slot ->
            preferences.safeGetString(slot.storageKey)
        }
        val migrated = SelectedAppsMigration.fromLegacyValues(legacyValues)
        if (!writeSelections(migrated, commit = true, includeSchemaVersion = false)) return

        preferences.edit().putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION).commit()
    }

    private fun readCurrentSelections(): List<SelectedApp> {
        if (!preferences.contains(KEY_SCHEMA_VERSION)) return emptyList()
        val count = preferences.safeGetInt(KEY_APPLICATION_COUNT)
            .coerceIn(0, SelectedAppsRules.MAX_APPLICATIONS)
        return (0 until count).mapNotNull { index ->
            SelectedAppCodec.decode(preferences.safeGetString(itemKey(index)))
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun writeSelections(
        applications: List<SelectedApp>,
        commit: Boolean,
        includeSchemaVersion: Boolean = true,
    ): Boolean {
        val editor = preferences.edit()
        repeat(SelectedAppsRules.MAX_APPLICATIONS) { index ->
            editor.remove(itemKey(index))
        }
        applications.forEachIndexed { index, application ->
            editor.putString(itemKey(index), SelectedAppCodec.encode(application))
        }
        editor.putInt(KEY_APPLICATION_COUNT, applications.size)
        if (includeSchemaVersion) editor.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        return if (commit) editor.commit() else {
            editor.apply()
            true
        }
    }

    private fun SharedPreferences.safeGetString(key: String): String? =
        runCatching { getString(key, null) }.getOrNull()

    private fun SharedPreferences.safeGetInt(key: String): Int =
        runCatching { getInt(key, 0) }.getOrDefault(0)

    companion object {
        internal const val PREFERENCES_NAME = "selected_apps"
        internal const val KEY_SCHEMA_VERSION = "selected_apps_schema_version"
        internal const val KEY_APPLICATION_COUNT = "selected_apps_count"
        internal const val KEY_APPLICATION_PREFIX = "selected_apps_item_"
        internal const val SCHEMA_VERSION = 1

        internal fun itemKey(index: Int): String = "$KEY_APPLICATION_PREFIX$index"
    }
}
