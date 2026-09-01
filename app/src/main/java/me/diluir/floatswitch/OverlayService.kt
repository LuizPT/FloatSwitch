package me.diluir.floatswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var selectedAppsStore: SelectedAppsStore
    private lateinit var launcherAppsRepository: LauncherAppsRepository
    private var overlayView: View? = null
    private var overlayButtons: List<ImageButton> = emptyList()
    private val permissionHandler = Handler(Looper.getMainLooper())
    private val permissionCheck = object : Runnable {
        override fun run() {
            if (!Settings.canDrawOverlays(this@OverlayService)) {
                stopSelf()
            } else {
                permissionHandler.postDelayed(this, PERMISSION_CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        selectedAppsStore = SelectedAppsStore(this)
        launcherAppsRepository = LauncherAppsRepository(packageManager, packageName)
        createNotificationChannel()
        startForegroundImmediately()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val installedApps = loadSelectedApplications()
        if (installedApps == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        showOrUpdateOverlay(installedApps)
        schedulePermissionCheck()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        permissionHandler.removeCallbacks(permissionCheck)
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // A View pode já ter sido removida pelo sistema após revogar a autorização.
            }
        }
        overlayView = null
        overlayButtons = emptyList()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startForegroundImmediately() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hideIntent = Intent(this, OverlayService::class.java).setAction(ACTION_HIDE)
        val hidePendingIntent = PendingIntent.getService(
            this,
            REQUEST_HIDE,
            hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_floatswitch)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_notification_hide,
                getString(R.string.notification_action_hide),
                hidePendingIntent,
            )
            .build()
    }

    private fun loadSelectedApplications(): List<InstalledLauncherApp>? {
        val installedApps = AppSlot.values().map { slot ->
            val savedSelection = selectedAppsStore.load(slot) ?: return null
            val installedApp = launcherAppsRepository.findInstalledApp(savedSelection)
            if (installedApp == null) {
                selectedAppsStore.clear(slot)
                return null
            }
            if (installedApp.selection != savedSelection) {
                selectedAppsStore.save(slot, installedApp.selection)
            }
            installedApp
        }

        if (
            installedApps[0].selection.packageName ==
            installedApps[1].selection.packageName
        ) {
            selectedAppsStore.clear(AppSlot.SECOND)
            return null
        }
        return installedApps
    }

    private fun showOrUpdateOverlay(installedApps: List<InstalledLauncherApp>) {
        if (overlayView == null) {
            createOverlay(installedApps)
        } else {
            updateOverlayButtons(installedApps)
        }
    }

    private fun createOverlay(installedApps: List<InstalledLauncherApp>) {
        val buttonSize = resources.getDimensionPixelSize(R.dimen.overlay_button_size)
        val buttonSpacing = resources.getDimensionPixelSize(R.dimen.overlay_button_spacing)
        val buttons = installedApps.map { createOverlayButton() }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                buttons[0],
                LinearLayout.LayoutParams(buttonSize, buttonSize),
            )
            addView(
                buttons[1],
                LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                    topMargin = buttonSpacing
                },
            )
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = resources.getDimensionPixelSize(R.dimen.overlay_edge_margin)
            y = 0
            setTitle(getString(R.string.overlay_accessibility_title))
        }

        overlayButtons = buttons
        updateOverlayButtons(installedApps)
        try {
            windowManager.addView(container, layoutParams)
            overlayView = container
        } catch (_: RuntimeException) {
            overlayButtons = emptyList()
            stopSelf()
        }
    }

    private fun createOverlayButton(): ImageButton = ImageButton(this).apply {
        setBackgroundResource(R.drawable.overlay_button_background)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val iconPadding = resources.getDimensionPixelSize(R.dimen.overlay_button_icon_padding)
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        elevation = resources.getDimension(R.dimen.overlay_button_elevation)
    }

    private fun updateOverlayButtons(installedApps: List<InstalledLauncherApp>) {
        if (overlayButtons.size != installedApps.size) return

        overlayButtons.zip(installedApps).forEach { (button, installedApp) ->
            button.imageTintList = null
            button.setImageDrawable(installedApp.icon)
            button.contentDescription = getString(
                R.string.overlay_button_open_application,
                installedApp.selection.displayName,
            )
            button.setOnClickListener {
                openSelectedApplication(installedApp.selection)
            }
        }
    }

    private fun openSelectedApplication(selectedApp: SelectedApp) {
        val explicitLauncherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(selectedApp.packageName, selectedApp.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (tryStartActivity(explicitLauncherIntent)) return

        val fallbackIntent = try {
            packageManager.getLaunchIntentForPackage(selectedApp.packageName)
        } catch (_: SecurityException) {
            null
        }
        if (fallbackIntent != null) {
            fallbackIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            if (tryStartActivity(fallbackIntent)) return
        }

        Toast.makeText(
            this,
            getString(R.string.application_launch_failed, selectedApp.displayName),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun tryStartActivity(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun schedulePermissionCheck() {
        permissionHandler.removeCallbacks(permissionCheck)
        permissionHandler.postDelayed(permissionCheck, PERMISSION_CHECK_INTERVAL_MS)
    }

    companion object {
        const val ACTION_SHOW = "me.diluir.floatswitch.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "me.diluir.floatswitch.action.HIDE_OVERLAY"

        private const val NOTIFICATION_CHANNEL_ID = "floating_shortcuts"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN_APP = 1002
        private const val REQUEST_HIDE = 1003
        private const val PERMISSION_CHECK_INTERVAL_MS = 5_000L
    }
}
