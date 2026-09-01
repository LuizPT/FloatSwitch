package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartDecisionEngineTest {
    @Test
    fun disabledAutoStart_isIgnored() {
        val decision = decide(autoStartEnabled = false)

        assertFalse(decision.shouldStart)
        assertEquals(AutoStartResult.AUTO_START_DISABLED, decision.result)
    }

    @Test
    fun missingOverlayPermission_isIgnored() {
        val decision = decide(overlayPermissionGranted = false)

        assertFalse(decision.shouldStart)
        assertEquals(AutoStartResult.OVERLAY_PERMISSION_MISSING, decision.result)
    }

    @Test
    fun invalidFirstApplication_isIgnored() {
        val decision = decide(firstApplicationValid = false)

        assertFalse(decision.shouldStart)
        assertEquals(AutoStartResult.FIRST_APP_INVALID, decision.result)
    }

    @Test
    fun invalidSecondApplication_isIgnored() {
        val decision = decide(secondApplicationValid = false)

        assertFalse(decision.shouldStart)
        assertEquals(AutoStartResult.SECOND_APP_INVALID, decision.result)
    }

    @Test
    fun completeConfiguration_requestsStart() {
        val decision = decide()

        assertTrue(decision.shouldStart)
        assertEquals(AutoStartEvent.BOOT_COMPLETED, decision.event)
        assertEquals(AutoStartResult.START_REQUESTED, decision.result)
    }

    @Test
    fun unknownAction_isIgnoredBeforeOtherChecks() {
        val decision = decide(
            action = "example.UNKNOWN",
            autoStartEnabled = false,
        )

        assertFalse(decision.shouldStart)
        assertEquals(AutoStartEvent.UNKNOWN, decision.event)
        assertEquals(AutoStartResult.UNKNOWN_ACTION, decision.result)
    }

    @Test
    fun diagnosticValues_areValidated() {
        assertEquals(
            AutoStartDiagnostic(
                AutoStartEvent.MY_PACKAGE_REPLACED,
                1234L,
                AutoStartResult.START_REQUESTED,
            ),
            AutoStartDiagnosticRules.fromStoredValues(
                "MY_PACKAGE_REPLACED",
                1234L,
                "START_REQUESTED",
            ),
        )
        assertNull(
            AutoStartDiagnosticRules.fromStoredValues(
                "SERVICE_RECOVERY",
                1234L,
                "START_REQUESTED",
            ),
        )
        assertNull(
            AutoStartDiagnosticRules.fromStoredValues("INVALID", 0L, "INVALID"),
        )
    }

    private fun decide(
        action: String = AutoStartEvent.BOOT_COMPLETED.intentAction.orEmpty(),
        autoStartEnabled: Boolean = true,
        overlayPermissionGranted: Boolean = true,
        firstApplicationValid: Boolean = true,
        secondApplicationValid: Boolean = true,
    ): AutoStartDecision = AutoStartDecisionEngine.decide(
        action,
        autoStartEnabled,
        overlayPermissionGranted,
        firstApplicationValid,
        secondApplicationValid,
    )
}
