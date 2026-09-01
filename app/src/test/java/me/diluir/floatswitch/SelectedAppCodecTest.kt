package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAppCodecTest {
    @Test
    fun encodeAndDecode_preservesAllFields() {
        val selectedApp = SelectedApp(
            displayName = "Câmara & Música | PT",
            packageName = "com.example.media",
            activityName = "com.example.media.LauncherActivity",
        )

        assertEquals(selectedApp, SelectedAppCodec.decode(SelectedAppCodec.encode(selectedApp)))
    }

    @Test
    fun decode_rejectsMissingOrMalformedValues() {
        assertNull(SelectedAppCodec.decode(null))
        assertNull(SelectedAppCodec.decode(""))
        assertNull(SelectedAppCodec.decode("not|enough"))
        assertNull(SelectedAppCodec.decode("invalid|base64|%%%"))
    }

    @Test
    fun selectionRules_preventSamePackageInBothSlots() {
        val first = SelectedApp("Primeira", "com.example.same", "com.example.same.First")
        val samePackage = SelectedApp("Segunda", "com.example.same", "com.example.same.Second")
        val differentPackage = SelectedApp("Outra", "com.example.other", "com.example.other.Main")

        assertFalse(AppSelectionRules.canUseInSlot(samePackage, first))
        assertTrue(AppSelectionRules.canUseInSlot(differentPackage, first))
        assertTrue(AppSelectionRules.canUseInSlot(first, null))
    }
}
