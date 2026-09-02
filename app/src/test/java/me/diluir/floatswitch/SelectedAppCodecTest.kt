package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

}
