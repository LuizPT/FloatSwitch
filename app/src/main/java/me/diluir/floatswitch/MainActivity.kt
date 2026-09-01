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
import android.widget.Button
import android.widget.ImageView
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
    private lateinit var firstSlotViews: AppSlotViews
    private lateinit var secondSlotViews: AppSlotViews
    private lateinit var selectedAppsStore: SelectedAppsStore
    private lateinit var launcherAppsRepository: LauncherAppsRepository
    private lateinit var autoStartStateStore: AutoStartStateStore

    private var firstInstalledApp: InstalledLauncherApp? = null
    private var secondInstalledApp: InstalledLauncherApp? = null
    private var updatingAutoStartSwitch = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        updateOverlayPermissionState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showShortcuts()
        } else {
            feedbackText.setText(R.string.notification_permission_denied)
        }
    }

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val slot = AppSlot.fromStorageKey(
            result.data?.getStringExtra(AppPickerActivity.EXTRA_SLOT),
        ) ?: return@registerForActivityResult
        val selectedApp = SelectedAppCodec.decode(
            result.data?.getStringExtra(AppPickerActivity.EXTRA_SELECTION),
        ) ?: return@registerForActivityResult
        val otherSelection = selectedAppsStore.load(slot.other())

        if (!AppSelectionRules.canUseInSlot(selectedApp, otherSelection)) {
            feedbackText.setText(R.string.duplicate_application_not_allowed)
            return@registerForActivityResult
        }

        selectedAppsStore.save(slot, selectedApp)
        refreshSelectedApplications(showInvalidFeedback = false)
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
        firstSlotViews = AppSlotViews(
            icon = findViewById(R.id.applicationOneIcon),
            name = findViewById(R.id.applicationOneName),
            chooseButton = findViewById(R.id.chooseApplicationOneButton),
        )
        secondSlotViews = AppSlotViews(
            icon = findViewById(R.id.applicationTwoIcon),
            name = findViewById(R.id.applicationTwoName),
            chooseButton = findViewById(R.id.chooseApplicationTwoButton),
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.grantOverlayPermissionButton).setOnClickListener {
            openOverlayPermissionSettings()
        }
        firstSlotViews.chooseButton.setOnClickListener {
            openApplicationPicker(AppSlot.FIRST)
        }
        secondSlotViews.chooseButton.setOnClickListener {
            openApplicationPicker(AppSlot.SECOND)
        }
        showShortcutsButton.setOnClickListener {
            showShortcuts()
        }
        findViewById<Button>(R.id.hideShortcutsButton).setOnClickListener {
            hideShortcuts()
        }
        autoStartSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!updatingAutoStartSwitch) {
                changeAutoStartPreference(enabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayPermissionState()
        refreshSelectedApplications()
        updateAutoStartInformation()
    }

    private fun openApplicationPicker(slot: AppSlot) {
        val excludedPackageName = when (slot) {
            AppSlot.FIRST -> secondInstalledApp?.selection?.packageName
            AppSlot.SECOND -> firstInstalledApp?.selection?.packageName
        }
        appPickerLauncher.launch(
            AppPickerActivity.createIntent(this, slot, excludedPackageName),
        )
    }

    private fun refreshSelectedApplications(showInvalidFeedback: Boolean = true) {
        var invalidSelectionRemoved = false
        firstInstalledApp = loadValidSelection(AppSlot.FIRST) {
            invalidSelectionRemoved = true
        }
        secondInstalledApp = loadValidSelection(AppSlot.SECOND) {
            invalidSelectionRemoved = true
        }

        if (
            firstInstalledApp != null &&
            firstInstalledApp?.selection?.packageName == secondInstalledApp?.selection?.packageName
        ) {
            selectedAppsStore.clear(AppSlot.SECOND)
            secondInstalledApp = null
            invalidSelectionRemoved = true
        }

        updateSlotViews(firstSlotViews, firstInstalledApp)
        updateSlotViews(secondSlotViews, secondInstalledApp)
        showShortcutsButton.isEnabled = firstInstalledApp != null && secondInstalledApp != null

        if (invalidSelectionRemoved && showInvalidFeedback) {
            feedbackText.setText(R.string.invalid_application_selection_removed)
        }
    }

    private fun loadValidSelection(
        slot: AppSlot,
        onInvalid: () -> Unit,
    ): InstalledLauncherApp? {
        val savedSelection = selectedAppsStore.load(slot) ?: return null
        val installedApp = launcherAppsRepository.findInstalledApp(savedSelection)
        if (installedApp == null) {
            selectedAppsStore.clear(slot)
            onInvalid()
            return null
        }

        if (installedApp.selection != savedSelection) {
            selectedAppsStore.save(slot, installedApp.selection)
        }
        return installedApp
    }

    private fun updateSlotViews(
        slotViews: AppSlotViews,
        installedApp: InstalledLauncherApp?,
    ) {
        if (installedApp == null) {
            slotViews.icon.setImageResource(R.drawable.ic_app_placeholder)
            slotViews.icon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.overlay_icon),
            )
            slotViews.icon.contentDescription = getString(
                R.string.no_application_icon_description,
            )
            slotViews.name.setText(R.string.no_application_selected)
            slotViews.chooseButton.setText(R.string.button_choose_application)
            return
        }

        slotViews.icon.imageTintList = null
        slotViews.icon.setImageDrawable(installedApp.icon)
        slotViews.icon.contentDescription = getString(
            R.string.application_icon_description,
            installedApp.selection.displayName,
        )
        slotViews.name.text = installedApp.selection.displayName
        slotViews.chooseButton.setText(R.string.button_change_application)
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
            if (permissionGranted) {
                R.string.overlay_permission_granted
            } else {
                R.string.overlay_permission_missing
            },
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
        if (firstInstalledApp == null || secondInstalledApp == null) {
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
        if (firstInstalledApp == null || secondInstalledApp == null) {
            feedbackText.setText(R.string.two_applications_required)
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

    private fun hideShortcuts() {
        autoStartStateStore.setOverlayRequestedActive(false)
        try {
            val stoppedService = startService(
                Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP),
            )
            feedbackText.setText(
                if (stoppedService != null) {
                    R.string.shortcuts_hidden
                } else {
                    R.string.shortcuts_hide_failed
                },
            )
        } catch (_: RuntimeException) {
            feedbackText.setText(R.string.shortcuts_hide_failed)
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
        AutoStartResult.OVERLAY_PERMISSION_MISSING -> {
            R.string.auto_start_result_overlay_permission
        }

        AutoStartResult.FIRST_APP_INVALID -> R.string.auto_start_result_first_app
        AutoStartResult.SECOND_APP_INVALID -> R.string.auto_start_result_second_app
        AutoStartResult.UNKNOWN_ACTION -> R.string.auto_start_result_unknown
        AutoStartResult.FOREGROUND_START_NOT_ALLOWED -> R.string.auto_start_result_not_allowed
        AutoStartResult.SECURITY_EXCEPTION -> R.string.auto_start_result_security
        AutoStartResult.RUNTIME_EXCEPTION -> R.string.auto_start_result_runtime
    }

    private fun AppSlot.other(): AppSlot = when (this) {
        AppSlot.FIRST -> AppSlot.SECOND
        AppSlot.SECOND -> AppSlot.FIRST
    }

    private data class AppSlotViews(
        val icon: ImageView,
        val name: TextView,
        val chooseButton: Button,
    )
}
