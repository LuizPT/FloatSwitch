package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionLockStoreTest {
    @Test
    fun initialValue_isUnlocked() {
        val store = PositionLockStore(MapBooleanPreferenceStorage())

        assertFalse(store.isPositionLocked())
    }

    @Test
    fun enabledValue_persistsAcrossStoreInstances() {
        val values = mutableMapOf<String, Any>()
        PositionLockStore(MapBooleanPreferenceStorage(values)).setPositionLocked(true)

        assertTrue(PositionLockStore(MapBooleanPreferenceStorage(values)).isPositionLocked())
    }

    @Test
    fun disabledValue_persistsAfterEnabledValue() {
        val values = mutableMapOf<String, Any>()
        val store = PositionLockStore(MapBooleanPreferenceStorage(values))
        store.setPositionLocked(true)
        store.setPositionLocked(false)

        assertFalse(PositionLockStore(MapBooleanPreferenceStorage(values)).isPositionLocked())
    }

    @Test
    fun changingPositionLock_doesNotChangeOtherStateKeys() {
        val values = mutableMapOf<String, Any>(
            "auto_start_enabled" to true,
            "overlay_requested_active" to true,
        )
        val store = PositionLockStore(MapBooleanPreferenceStorage(values))

        store.setPositionLocked(true)
        store.setPositionLocked(false)

        assertEquals(true, values["auto_start_enabled"])
        assertEquals(true, values["overlay_requested_active"])
    }

    @Test
    fun changingPositionLock_doesNotChangeStoredPosition() {
        val values = mutableMapOf<String, Any>(
            "side" to "LEFT",
            "vertical_fraction" to 0.75f,
        )
        val store = PositionLockStore(MapBooleanPreferenceStorage(values))

        store.setPositionLocked(true)
        store.setPositionLocked(false)

        assertEquals("LEFT", values["side"])
        assertEquals(0.75f, values["vertical_fraction"])
    }

    @Test
    fun preferenceContract_usesDedicatedFileAndKey() {
        assertEquals("overlay_position_lock", PositionLockStore.PREFERENCES_NAME)
        assertEquals("position_locked", PositionLockStore.KEY_POSITION_LOCKED)
    }

    private class MapBooleanPreferenceStorage(
        private val values: MutableMap<String, Any> = mutableMapOf(),
    ) : BooleanPreferenceStorage {
        override fun readBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun writeBoolean(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
