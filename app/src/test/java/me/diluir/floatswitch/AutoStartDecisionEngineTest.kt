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
    fun zeroValidApplications_isIgnored() {
        val decision = decide(validApplicationCount = 0)

        assertFalse(decision.shouldStart)
        assertEquals(AutoStartResult.NO_VALID_APPLICATIONS, decision.result)
    }

    @Test
    fun oneValidApplication_requestsStart() {
        assertTrue(decide(validApplicationCount = 1).shouldStart)
    }

    @Test
    fun sixValidApplications_requestStart() {
        assertTrue(decide(validApplicationCount = 6).shouldStart)
    }

    @Test
    fun moreThanSixApplications_isIgnored() {
        val decision = decide(validApplicationCount = 7)

        assertFalse(decision.shouldStart)
        assertEquals(AutoStartResult.TOO_MANY_APPLICATIONS, decision.result)
    }

    @Test
    fun oneUnavailableAndOneValidApplication_requestsStart() {
        val configured = listOf(application(1), application(2))
        val available = SelectedAppsRules.availableApplications(
            configured,
            listOf(false, true),
        )

        assertTrue(decide(validApplicationCount = available.size).shouldStart)
    }

    @Test
    fun allUnavailableApplications_areIgnored() {
        val configured = listOf(application(1), application(2))
        val available = SelectedAppsRules.availableApplications(
            configured,
            listOf(false, false),
        )

        assertEquals(
            AutoStartResult.NO_VALID_APPLICATIONS,
            decide(validApplicationCount = available.size).result,
        )
    }

    @Test
    fun unknownAction_isIgnoredBeforeOtherChecks() {
        val decision = decide(action = "example.UNKNOWN", autoStartEnabled = false)

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
        assertNull(AutoStartDiagnosticRules.fromStoredValues("INVALID", 0L, "INVALID"))
    }

    private fun decide(
        action: String = AutoStartEvent.BOOT_COMPLETED.intentAction.orEmpty(),
        autoStartEnabled: Boolean = true,
        overlayPermissionGranted: Boolean = true,
        validApplicationCount: Int = 1,
    ): AutoStartDecision = AutoStartDecisionEngine.decide(
        action,
        autoStartEnabled,
        overlayPermissionGranted,
        validApplicationCount,
    )

    private fun application(number: Int): SelectedApp = SelectedApp(
        displayName = "Aplicação $number",
        packageName = "com.example.app$number",
        activityName = "com.example.app$number.MainActivity",
    )
}
