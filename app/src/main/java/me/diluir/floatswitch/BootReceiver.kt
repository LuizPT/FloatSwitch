package me.diluir.floatswitch

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val stateStore = AutoStartStateStore(appContext)
        val event = AutoStartEvent.fromIntentAction(intent.action)
        if (event == AutoStartEvent.UNKNOWN) {
            stateStore.recordDiagnostic(event, AutoStartResult.UNKNOWN_ACTION)
            return
        }
        if (event == AutoStartEvent.BOOT_COMPLETED) {
            stateStore.setOverlayRequestedActive(false)
        }

        val autoStartEnabled = stateStore.isAutoStartEnabled()
        if (!autoStartEnabled) {
            stateStore.recordDiagnostic(event, AutoStartResult.AUTO_START_DISABLED)
            return
        }

        val overlayPermissionGranted = runCatching {
            Settings.canDrawOverlays(appContext)
        }.getOrDefault(false)
        if (!overlayPermissionGranted) {
            stateStore.recordDiagnostic(event, AutoStartResult.OVERLAY_PERMISSION_MISSING)
            return
        }

        val decision = try {
            evaluateConfiguredApplications(appContext, intent.action, autoStartEnabled)
        } catch (_: SecurityException) {
            stateStore.recordDiagnostic(event, AutoStartResult.SECURITY_EXCEPTION)
            return
        } catch (_: RuntimeException) {
            stateStore.recordDiagnostic(event, AutoStartResult.RUNTIME_EXCEPTION)
            return
        }
        if (!decision.shouldStart) {
            stateStore.recordDiagnostic(decision.event, decision.result)
            return
        }

        stateStore.setOverlayRequestedActive(true)
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, OverlayService::class.java).setAction(
                    OverlayService.ACTION_SHOW,
                ),
            )
            stateStore.recordDiagnostic(event, AutoStartResult.START_REQUESTED)
        } catch (_: SecurityException) {
            stateStore.recordDiagnostic(event, AutoStartResult.SECURITY_EXCEPTION)
        } catch (exception: RuntimeException) {
            val result = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                exception is ForegroundServiceStartNotAllowedException
            ) {
                AutoStartResult.FOREGROUND_START_NOT_ALLOWED
            } else {
                AutoStartResult.RUNTIME_EXCEPTION
            }
            stateStore.recordDiagnostic(event, result)
        }
    }

    private fun evaluateConfiguredApplications(
        context: Context,
        action: String?,
        autoStartEnabled: Boolean,
    ): AutoStartDecision {
        val selectedAppsStore = SelectedAppsStore(context)
        val launcherAppsRepository = LauncherAppsRepository(
            context.packageManager,
            context.packageName,
        )
        val selections = selectedAppsStore.load()
        val installedApps = launcherAppsRepository.resolveInstalledApps(selections).filterNotNull()
        val validSelections = installedApps.map(InstalledLauncherApp::selection)
        if (validSelections != selections) selectedAppsStore.save(validSelections)
        return AutoStartDecisionEngine.decide(
            action = action,
            autoStartEnabled = autoStartEnabled,
            overlayPermissionGranted = true,
            validApplicationCount = validSelections.size,
        )
    }
}
