package me.diluir.floatswitch

enum class AutoStartEvent(val intentAction: String?) {
    BOOT_COMPLETED("android.intent.action.BOOT_COMPLETED"),
    MY_PACKAGE_REPLACED("android.intent.action.MY_PACKAGE_REPLACED"),
    SERVICE_RECOVERY(null),
    UNKNOWN(null);

    companion object {
        fun fromIntentAction(action: String?): AutoStartEvent = entries.firstOrNull {
            it.intentAction != null && it.intentAction == action
        } ?: UNKNOWN
    }
}

enum class AutoStartResult {
    START_REQUESTED,
    AUTO_START_DISABLED,
    OVERLAY_PERMISSION_MISSING,
    NO_VALID_APPLICATIONS,
    TOO_MANY_APPLICATIONS,
    FIRST_APP_INVALID,
    SECOND_APP_INVALID,
    UNKNOWN_ACTION,
    FOREGROUND_START_NOT_ALLOWED,
    SECURITY_EXCEPTION,
    RUNTIME_EXCEPTION,
}

data class AutoStartDecision(
    val event: AutoStartEvent,
    val result: AutoStartResult,
) {
    val shouldStart: Boolean
        get() = result == AutoStartResult.START_REQUESTED
}

object AutoStartDecisionEngine {
    fun decide(
        action: String?,
        autoStartEnabled: Boolean,
        overlayPermissionGranted: Boolean,
        validApplicationCount: Int,
    ): AutoStartDecision {
        val event = AutoStartEvent.fromIntentAction(action)
        val result = when {
            event == AutoStartEvent.UNKNOWN -> AutoStartResult.UNKNOWN_ACTION
            !autoStartEnabled -> AutoStartResult.AUTO_START_DISABLED
            !overlayPermissionGranted -> AutoStartResult.OVERLAY_PERMISSION_MISSING
            validApplicationCount <= 0 -> AutoStartResult.NO_VALID_APPLICATIONS
            validApplicationCount > SelectedAppsRules.MAX_APPLICATIONS -> {
                AutoStartResult.TOO_MANY_APPLICATIONS
            }
            else -> AutoStartResult.START_REQUESTED
        }
        return AutoStartDecision(event, result)
    }
}

data class AutoStartDiagnostic(
    val event: AutoStartEvent,
    val timestampMillis: Long,
    val result: AutoStartResult,
)

object AutoStartDiagnosticRules {
    fun fromStoredValues(
        eventValue: String?,
        timestampMillis: Long,
        resultValue: String?,
    ): AutoStartDiagnostic? {
        if (timestampMillis <= 0L) return null
        val event = enumValueOrNull<AutoStartEvent>(eventValue) ?: return null
        val result = enumValueOrNull<AutoStartResult>(resultValue) ?: return null
        if (!isValidCombination(event, result)) return null
        return AutoStartDiagnostic(event, timestampMillis, result)
    }

    private fun isValidCombination(
        event: AutoStartEvent,
        result: AutoStartResult,
    ): Boolean = when (event) {
        AutoStartEvent.BOOT_COMPLETED,
        AutoStartEvent.MY_PACKAGE_REPLACED,
        -> result != AutoStartResult.UNKNOWN_ACTION

        AutoStartEvent.SERVICE_RECOVERY -> result == AutoStartResult.OVERLAY_PERMISSION_MISSING ||
            result == AutoStartResult.NO_VALID_APPLICATIONS ||
            result == AutoStartResult.TOO_MANY_APPLICATIONS ||
            result == AutoStartResult.FIRST_APP_INVALID ||
            result == AutoStartResult.SECOND_APP_INVALID ||
            result == AutoStartResult.SECURITY_EXCEPTION ||
            result == AutoStartResult.RUNTIME_EXCEPTION

        AutoStartEvent.UNKNOWN -> result == AutoStartResult.UNKNOWN_ACTION
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrNull()
}
