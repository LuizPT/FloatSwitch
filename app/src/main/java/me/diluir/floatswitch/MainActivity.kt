package me.diluir.floatswitch

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var permissionStateText: TextView
    private lateinit var feedbackText: TextView
    private lateinit var autoStartDiagnosticText: TextView
    private lateinit var autoStartSwitch: SwitchCompat
    private lateinit var showShortcutsButton: Button
    private lateinit var addApplicationButton: Button
    private lateinit var applicationCountText: TextView
    private lateinit var applicationsContainer: LinearLayout
    private lateinit var selectedAppsStore: SelectedAppsStore
    private lateinit var launcherAppsRepository: LauncherAppsRepository
    private lateinit var autoStartStateStore: AutoStartStateStore

    private var installedApps: List<InstalledLauncherApp> = emptyList()
    private var updatingAutoStartSwitch = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        updateOverlayPermissionState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showShortcuts()
        else feedbackText.setText(R.string.notification_permission_denied)
    }

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val selectionIndex = result.data?.getIntExtra(
            AppPickerActivity.EXTRA_SELECTION_INDEX,
            Int.MIN_VALUE,
        ) ?: return@registerForActivityResult
        val selectedApp = SelectedAppCodec.decode(
            result.data?.getStringExtra(AppPickerActivity.EXTRA_SELECTION),
        ) ?: return@registerForActivityResult
        val current = selectedAppsStore.load()
        if (
            selectionIndex != AppPickerActivity.APPEND_INDEX &&
            selectionIndex !in current.indices
        ) {
            return@registerForActivityResult
        }
        val duplicate = current.withIndex().any { (index, application) ->
            index != selectionIndex && application.packageName == selectedApp.packageName
        }
        if (duplicate) {
            feedbackText.setText(R.string.duplicate_application_not_allowed)
            return@registerForActivityResult
        }

        val updated = if (selectionIndex == AppPickerActivity.APPEND_INDEX) {
            SelectedAppsRules.add(current, selectedApp)
        } else {
            SelectedAppsRules.replace(current, selectionIndex, selectedApp)
        }
        if (updated == current && selectionIndex == AppPickerActivity.APPEND_INDEX) {
            return@registerForActivityResult
        }

        selectedAppsStore.save(updated)
        refreshSelectedApplications(showInvalidFeedback = false)
        refreshOverlayIfActive()
        feedbackText.text = getString(
            R.string.application_selection_saved,
            selectedApp.displayName,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        selectedAppsStore = SelectedAppsStore(this)
        launcherAppsRepository = LauncherAppsRepository(packageManager, packageName)
        autoStartStateStore = AutoStartStateStore(this)
        permissionStateText = findViewById(R.id.overlayPermissionState)
        feedbackText = findViewById(R.id.feedbackText)
        autoStartDiagnosticText = findViewById(R.id.autoStartDiagnostic)
        autoStartSwitch = findViewById(R.id.autoStartSwitch)
        showShortcutsButton = findViewById(R.id.showShortcutsButton)
        addApplicationButton = findViewById(R.id.addApplicationButton)
        applicationCountText = findViewById(R.id.applicationCountText)
        applicationsContainer = findViewById(R.id.selectedApplicationsContainer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.grantOverlayPermissionButton).setOnClickListener {
            openOverlayPermissionSettings()
        }
        addApplicationButton.setOnClickListener {
            openApplicationPicker(AppPickerActivity.APPEND_INDEX)
        }
        showShortcutsButton.setOnClickListener { showShortcuts() }
        findViewById<Button>(R.id.hideShortcutsButton).setOnClickListener { hideShortcuts() }
        autoStartSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!updatingAutoStartSwitch) changeAutoStartPreference(enabled)
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayPermissionState()
        refreshSelectedApplications()
        updateAutoStartInformation()
    }

    private fun openApplicationPicker(selectionIndex: Int) {
        val excludedPackages = installedApps.mapIndexedNotNull { index, app ->
            app.selection.packageName.takeIf { selectionIndex != index }
        }
        appPickerLauncher.launch(
            AppPickerActivity.createIntent(this, selectionIndex, excludedPackages),
        )
    }

    private fun refreshSelectedApplications(showInvalidFeedback: Boolean = true) {
        val stored = selectedAppsStore.load()
        val resolved = launcherAppsRepository.resolveInstalledApps(stored)
        installedApps = resolved.filterNotNull()
        val refreshedSelections = installedApps.map(InstalledLauncherApp::selection)
        val invalidSelectionRemoved = refreshedSelections.size != stored.size
        if (refreshedSelections != stored) selectedAppsStore.save(refreshedSelections)

        renderApplications()
        showShortcutsButton.isEnabled = installedApps.isNotEmpty()
        addApplicationButton.isEnabled = installedApps.size < SelectedAppsRules.MAX_APPLICATIONS

        if (invalidSelectionRemoved && showInvalidFeedback) {
            feedbackText.setText(R.string.invalid_application_selection_removed)
        }
        if (installedApps.isEmpty() && autoStartStateStore.isOverlayRequestedActive()) {
            hideShortcuts(announce = false)
        }
    }

    private fun renderApplications() {
        applicationsContainer.removeAllViews()
        applicationCountText.text = getString(
            R.string.application_count,
            installedApps.size,
            SelectedAppsRules.MAX_APPLICATIONS,
        )
        installedApps.forEachIndexed { index, installedApp ->
            val row = LayoutInflater.from(this).inflate(
                R.layout.item_selected_application,
                applicationsContainer,
                false,
            )
            row.findViewById<TextView>(R.id.applicationPosition).text = getString(
                R.string.application_position_number,
                index + 1,
            )
            row.findViewById<ImageView>(R.id.selectedApplicationIcon).apply {
                imageTintList = null
                setImageDrawable(installedApp.icon)
                contentDescription = getString(
                    R.string.application_icon_description,
                    installedApp.selection.displayName,
                )
            }
            row.findViewById<TextView>(R.id.selectedApplicationName).text =
                installedApp.selection.displayName
            row.findViewById<Button>(R.id.changeApplicationButton).apply {
                contentDescription = getString(
                    R.string.change_application_description,
                    installedApp.selection.displayName,
                )
                setOnClickListener { openApplicationPicker(index) }
            }
            row.findViewById<ImageButton>(R.id.removeApplicationButton).apply {
                contentDescription = getString(
                    R.string.remove_application_description,
                    installedApp.selection.displayName,
                )
                setOnClickListener { removeApplication(index) }
            }
            row.findViewById<ImageButton>(R.id.moveApplicationUpButton).apply {
                isEnabled = index > 0
                contentDescription = getString(
                    R.string.move_application_up_description,
                    installedApp.selection.displayName,
                )
                setOnClickListener { moveApplication(index, index - 1) }
            }
            row.findViewById<ImageButton>(R.id.moveApplicationDownButton).apply {
                isEnabled = index < installedApps.lastIndex
                contentDescription = getString(
                    R.string.move_application_down_description,
                    installedApp.selection.displayName,
                )
                setOnClickListener { moveApplication(index, index + 1) }
            }
            applicationsContainer.addView(row)
        }
    }

    private fun removeApplication(index: Int) {
        val updated = selectedAppsStore.removeAt(index)
        if (updated.isEmpty()) autoStartStateStore.setOverlayRequestedActive(false)
        refreshSelectedApplications(showInvalidFeedback = false)
        if (updated.isEmpty()) {
            hideShortcuts(announce = false)
            feedbackText.setText(R.string.last_application_removed)
        } else {
            refreshOverlayIfActive()
            feedbackText.setText(R.string.application_removed)
        }
    }

    private fun moveApplication(fromIndex: Int, toIndex: Int) {
        selectedAppsStore.move(fromIndex, toIndex)
        refreshSelectedApplications(showInvalidFeedback = false)
        refreshOverlayIfActive()
    }

    private fun refreshOverlayIfActive() {
        if (!autoStartStateStore.isOverlayRequestedActive() || installedApps.isEmpty()) return
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW),
            )
        } catch (_: RuntimeException) {
            feedbackText.setText(R.string.shortcuts_start_failed)
        }
    }

    private fun openOverlayPermissionSettings() {
        val packageSettingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        )
        try {
            overlayPermissionLauncher.launch(packageSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun updateOverlayPermissionState() {
        val permissionGranted = Settings.canDrawOverlays(this)
        val backgroundColor = if (permissionGranted) {
            R.color.status_granted_background
        } else {
            R.color.status_missing_background
        }
        val textColor = if (permissionGranted) {
            R.color.status_granted_text
        } else {
            R.color.status_missing_text
        }
        permissionStateText.setText(
            if (permissionGranted) R.string.overlay_permission_granted
            else R.string.overlay_permission_missing,
        )
        permissionStateText.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, backgroundColor),
        )
        permissionStateText.setTextColor(ContextCompat.getColor(this, textColor))
    }

    private fun changeAutoStartPreference(enabled: Boolean) {
        if (!enabled) {
            autoStartStateStore.setAutoStartEnabled(false)
            feedbackText.setText(R.string.auto_start_disabled_feedback)
            return
        }

        refreshSelectedApplications()
        if (!Settings.canDrawOverlays(this)) {
            autoStartStateStore.setAutoStartEnabled(false)
            setAutoStartSwitchChecked(false)
            updateOverlayPermissionState()
            feedbackText.setText(R.string.auto_start_requires_overlay)
            return
        }
        if (installedApps.isEmpty()) {
            autoStartStateStore.setAutoStartEnabled(false)
            setAutoStartSwitchChecked(false)
            feedbackText.setText(R.string.auto_start_requires_apps)
            return
        }

        autoStartStateStore.setAutoStartEnabled(true)
        feedbackText.setText(R.string.auto_start_enabled_feedback)
    }

    private fun updateAutoStartInformation() {
        setAutoStartSwitchChecked(autoStartStateStore.isAutoStartEnabled())
        val diagnostic = autoStartStateStore.loadDiagnostic()
        if (diagnostic == null) {
            autoStartDiagnosticText.setText(R.string.auto_start_diagnostic_never)
            return
        }
        val formattedDate = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        ).format(Date(diagnostic.timestampMillis))
        autoStartDiagnosticText.text = getString(
            R.string.auto_start_diagnostic_format,
            formattedDate,
            getString(diagnostic.event.displayNameResource()),
            getString(diagnostic.result.displayNameResource()),
        )
    }

    private fun setAutoStartSwitchChecked(checked: Boolean) {
        updatingAutoStartSwitch = true
        autoStartSwitch.isChecked = checked
        updatingAutoStartSwitch = false
    }

    private fun showShortcuts() {
        refreshSelectedApplications()
        if (installedApps.isEmpty()) {
            feedbackText.setText(R.string.at_least_one_application_required)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            updateOverlayPermissionState()
            feedbackText.setText(R.string.overlay_permission_required)
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            feedbackText.setText(R.string.notification_permission_explanation)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW),
            )
            autoStartStateStore.setOverlayRequestedActive(true)
            feedbackText.setText(R.string.shortcuts_start_requested)
        } catch (_: RuntimeException) {
            feedbackText.setText(R.string.shortcuts_start_failed)
        }
    }

    private fun hideShortcuts(announce: Boolean = true) {
        autoStartStateStore.setOverlayRequestedActive(false)
        try {
            val stoppedService = startService(
                Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP),
            )
            if (announce) {
                feedbackText.setText(
                    if (stoppedService != null) R.string.shortcuts_hidden
                    else R.string.shortcuts_hide_failed,
                )
            }
        } catch (_: RuntimeException) {
            if (announce) feedbackText.setText(R.string.shortcuts_hide_failed)
        }
    }

    private fun AutoStartEvent.displayNameResource(): Int = when (this) {
        AutoStartEvent.BOOT_COMPLETED -> R.string.auto_start_event_boot
        AutoStartEvent.MY_PACKAGE_REPLACED -> R.string.auto_start_event_package_replaced
        AutoStartEvent.SERVICE_RECOVERY -> R.string.auto_start_event_service_recovery
        AutoStartEvent.UNKNOWN -> R.string.auto_start_event_unknown
    }

    private fun AutoStartResult.displayNameResource(): Int = when (this) {
        AutoStartResult.START_REQUESTED -> R.string.auto_start_result_requested
        AutoStartResult.AUTO_START_DISABLED -> R.string.auto_start_result_disabled
        AutoStartResult.OVERLAY_PERMISSION_MISSING -> R.string.auto_start_result_overlay_permission
        AutoStartResult.NO_VALID_APPLICATIONS -> R.string.auto_start_result_no_applications
        AutoStartResult.TOO_MANY_APPLICATIONS -> R.string.auto_start_result_too_many_applications
        AutoStartResult.FIRST_APP_INVALID -> R.string.auto_start_result_first_app
        AutoStartResult.SECOND_APP_INVALID -> R.string.auto_start_result_second_app
        AutoStartResult.UNKNOWN_ACTION -> R.string.auto_start_result_unknown
        AutoStartResult.FOREGROUND_START_NOT_ALLOWED -> R.string.auto_start_result_not_allowed
        AutoStartResult.SECURITY_EXCEPTION -> R.string.auto_start_result_security
        AutoStartResult.RUNTIME_EXCEPTION -> R.string.auto_start_result_runtime
    }
}
