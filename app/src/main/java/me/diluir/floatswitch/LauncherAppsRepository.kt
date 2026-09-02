package me.diluir.floatswitch

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import java.text.Collator
import java.util.Locale

data class InstalledLauncherApp(
    val selection: SelectedApp,
    val icon: Drawable,
)

class LauncherAppsRepository(
    private val packageManager: PackageManager,
    private val ownPackageName: String,
) {
    fun loadInstalledApps(
        excludedPackageNames: Set<String> = emptySet(),
    ): List<InstalledLauncherApp> {
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-PT")).apply {
            strength = Collator.PRIMARY
        }

        return queryLauncherActivities()
            .mapNotNull(::toInstalledApp)
            .filter { installedApp ->
                installedApp.selection.packageName != ownPackageName &&
                    installedApp.selection.packageName !in excludedPackageNames
            }
            .distinctBy { it.selection.packageName }
            .sortedWith { first, second ->
                collator.compare(first.selection.displayName, second.selection.displayName)
            }
    }

    fun resolveInstalledApps(
        selectedApps: List<SelectedApp>,
    ): List<InstalledLauncherApp?> {
        val installedByComponent = queryLauncherActivities()
            .mapNotNull(::toInstalledApp)
            .filter { it.selection.packageName != ownPackageName }
            .associateBy { installedApp ->
                ComponentName(
                    installedApp.selection.packageName,
                    installedApp.selection.activityName,
                )
            }
        return selectedApps.map { selectedApp ->
            installedByComponent[ComponentName(selectedApp.packageName, selectedApp.activityName)]
        }
    }

    fun validateLauncherActivities(selectedApps: List<SelectedApp>): List<Boolean> {
        val availableComponents = queryLauncherActivities()
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                if (!activityInfo.enabled || !activityInfo.applicationInfo.enabled) {
                    return@mapNotNull null
                }
                ComponentName(activityInfo.packageName, activityInfo.name)
            }
            .toSet()
        return selectedApps.map { selectedApp ->
            ComponentName(selectedApp.packageName, selectedApp.activityName) in availableComponents
        }
    }

    private fun queryLauncherActivities(): List<ResolveInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(
                    launcherIntent,
                    0,
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun toInstalledApp(resolveInfo: ResolveInfo): InstalledLauncherApp? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        if (!activityInfo.enabled || !activityInfo.applicationInfo.enabled) return null

        val displayName = try {
            resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
        } catch (_: SecurityException) {
            activityInfo.packageName
        } catch (_: Resources.NotFoundException) {
            activityInfo.packageName
        }.ifBlank { activityInfo.packageName }

        val icon = try {
            resolveInfo.loadIcon(packageManager)
        } catch (_: SecurityException) {
            packageManager.defaultActivityIcon
        } catch (_: Resources.NotFoundException) {
            packageManager.defaultActivityIcon
        }

        return InstalledLauncherApp(
            selection = SelectedApp(
                displayName = displayName,
                packageName = activityInfo.packageName,
                activityName = ComponentName(
                    activityInfo.packageName,
                    activityInfo.name,
                ).className,
            ),
            icon = icon,
        )
    }
}
