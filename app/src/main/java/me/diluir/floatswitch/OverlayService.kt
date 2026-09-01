package me.diluir.floatswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
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

        showOverlayIfNeeded()
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

    private fun showOverlayIfNeeded() {
        if (overlayView != null) return

        val buttonSize = resources.getDimensionPixelSize(R.dimen.overlay_button_size)
        val buttonSpacing = resources.getDimensionPixelSize(R.dimen.overlay_button_spacing)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                createOverlayButton(
                    iconResource = R.drawable.ic_overlay_app,
                    descriptionResource = R.string.overlay_button_open_app,
                ) {
                    openActivity(Intent(this@OverlayService, MainActivity::class.java))
                },
                LinearLayout.LayoutParams(buttonSize, buttonSize),
            )
            addView(
                createOverlayButton(
                    iconResource = R.drawable.ic_overlay_settings,
                    descriptionResource = R.string.overlay_button_open_settings,
                ) {
                    openActivity(Intent(Settings.ACTION_SETTINGS))
                },
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

        try {
            windowManager.addView(container, layoutParams)
            overlayView = container
        } catch (_: RuntimeException) {
            stopSelf()
        }
    }

    private fun createOverlayButton(
        iconResource: Int,
        descriptionResource: Int,
        onClick: () -> Unit,
    ): ImageButton = ImageButton(this).apply {
        setImageResource(iconResource)
        imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this@OverlayService, R.color.overlay_icon),
        )
        setBackgroundResource(R.drawable.overlay_button_background)
        contentDescription = getString(descriptionResource)
        scaleType = ImageView.ScaleType.CENTER
        val iconPadding = resources.getDimensionPixelSize(R.dimen.overlay_button_icon_padding)
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        elevation = resources.getDimension(R.dimen.overlay_button_elevation)
        setOnClickListener { onClick() }
    }

    private fun openActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // As duas actividades são do sistema/aplicação, mas uma ROM pode removê-las.
        }
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
