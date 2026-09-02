package me.diluir.floatswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.hypot
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var selectedAppsStore: SelectedAppsStore
    private lateinit var launcherAppsRepository: LauncherAppsRepository
    private lateinit var overlayPositionStore: OverlayPositionStore
    private lateinit var positionLockStore: PositionLockStore
    private lateinit var autoStartStateStore: AutoStartStateStore
    private var overlayView: View? = null
    private var overlayButtons: List<ImageButton> = emptyList()
    private var displayedSelections: List<SelectedApp> = emptyList()
    private var currentPosition = OverlayPositionRules.defaultPosition
    private var positionLocked = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var gestureView: View? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var touchMovedBeyondSlop = false
    private var longPressDetected = false
    private var dragStarted = false
    private val gestureHandler = Handler(Looper.getMainLooper())
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val longPressAction = Runnable {
        longPressDetected = true
        startDragging()
    }
    private val stateCheckHandler = Handler(Looper.getMainLooper())
    private val stateCheck = object : Runnable {
        override fun run() {
            if (!autoStartStateStore.isOverlayRequestedActive()) {
                stopSelf()
                return
            }
            if (!Settings.canDrawOverlays(this@OverlayService)) {
                stopForInvalidState(AutoStartResult.OVERLAY_PERMISSION_MISSING)
                return
            }
            val validSelections = try {
                validateStoredApplications()
            } catch (_: SecurityException) {
                stopForInvalidState(AutoStartResult.SECURITY_EXCEPTION)
                return
            } catch (_: RuntimeException) {
                stopForInvalidState(AutoStartResult.RUNTIME_EXCEPTION)
                return
            }
            if (validSelections.isEmpty()) {
                stopForInvalidState(AutoStartResult.NO_VALID_APPLICATIONS)
                return
            }
            if (validSelections != displayedSelections) {
                val applications = try {
                    loadSelectedApplications()
                } catch (_: SecurityException) {
                    stopForInvalidState(AutoStartResult.SECURITY_EXCEPTION)
                    return
                } catch (_: RuntimeException) {
                    stopForInvalidState(AutoStartResult.RUNTIME_EXCEPTION)
                    return
                }
                when (applications) {
                    is SelectedApplicationsResult.Invalid -> {
                        stopForInvalidState(applications.reason)
                        return
                    }

                    is SelectedApplicationsResult.Valid -> {
                        showOrUpdateOverlay(applications.applications)
                    }
                }
            }
            stateCheckHandler.postDelayed(this, STATE_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        autoStartStateStore = AutoStartStateStore(this)
        createNotificationChannel()
        startForegroundImmediately()
        selectedAppsStore = SelectedAppsStore(this)
        launcherAppsRepository = LauncherAppsRepository(packageManager, packageName)
        overlayPositionStore = OverlayPositionStore(this)
        positionLockStore = PositionLockStore(this)
        currentPosition = overlayPositionStore.load()
        positionLocked = positionLockStore.isPositionLocked()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            autoStartStateStore.setOverlayRequestedActive(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent != null && intent.action != ACTION_SHOW) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent == null && !autoStartStateStore.isOverlayRequestedActive()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopForInvalidState(AutoStartResult.OVERLAY_PERMISSION_MISSING)
            return START_NOT_STICKY
        }

        updatePositionLockState()

        val applications = try {
            loadSelectedApplications()
        } catch (_: SecurityException) {
            stopForInvalidState(AutoStartResult.SECURITY_EXCEPTION)
            return START_NOT_STICKY
        } catch (_: RuntimeException) {
            stopForInvalidState(AutoStartResult.RUNTIME_EXCEPTION)
            return START_NOT_STICKY
        }
        when (applications) {
            is SelectedApplicationsResult.Invalid -> {
                stopForInvalidState(applications.reason)
                return START_NOT_STICKY
            }

            is SelectedApplicationsResult.Valid -> {
                showOrUpdateOverlay(applications.applications)
            }
        }

        scheduleStateCheck()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.post { restoreOverlayPosition() }
    }

    override fun onDestroy() {
        stateCheckHandler.removeCallbacks(stateCheck)
        gestureHandler.removeCallbacksAndMessages(null)
        resetGestureState()
        removeOverlay()
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
        val stopIntent = Intent(this, OverlayService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            stopIntent,
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
                stopPendingIntent,
            )
            .build()
    }

    private fun loadSelectedApplications(): SelectedApplicationsResult {
        val storedSelections = selectedAppsStore.load()
        if (storedSelections.isEmpty()) {
            return SelectedApplicationsResult.Invalid(AutoStartResult.NO_VALID_APPLICATIONS)
        }
        val installedApps = launcherAppsRepository.resolveInstalledApps(storedSelections)
            .filterNotNull()
        val validSelections = installedApps.map(InstalledLauncherApp::selection)
        if (validSelections != storedSelections) selectedAppsStore.save(validSelections)
        if (installedApps.isEmpty()) {
            return SelectedApplicationsResult.Invalid(AutoStartResult.NO_VALID_APPLICATIONS)
        }
        return SelectedApplicationsResult.Valid(installedApps)
    }

    private fun validateStoredApplications(): List<SelectedApp> {
        val storedSelections = selectedAppsStore.load()
        val activityValidity = launcherAppsRepository.validateLauncherActivities(storedSelections)
        val validSelections = SelectedAppsRules.availableApplications(
            storedSelections,
            activityValidity,
        )
        if (validSelections != storedSelections) selectedAppsStore.save(validSelections)
        return validSelections
    }

    private fun showOrUpdateOverlay(installedApps: List<InstalledLauncherApp>) {
        if (overlayView == null) {
            createOverlay(installedApps)
        } else if (overlayButtons.size != installedApps.size) {
            resetGestureState()
            removeOverlay()
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
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            buttons.forEachIndexed { index, button ->
                addView(
                    button,
                    LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                        if (index > 0) topMargin = buttonSpacing
                    },
                )
            }
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
            setTitle(getString(R.string.overlay_accessibility_title))
        }

        container.addOnLayoutChangeListener { _, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom,
            ->
            val sizeChanged = right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            if (sizeChanged && !dragStarted) {
                container.post { restoreOverlayPosition() }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            if (!dragStarted) {
                container.post { restoreOverlayPosition() }
            }
            insets
        }

        overlayButtons = buttons
        updateOverlayButtons(installedApps)
        try {
            windowManager.addView(container, layoutParams)
            overlayView = container
            ViewCompat.requestApplyInsets(container)
            container.post { restoreOverlayPosition() }
        } catch (_: SecurityException) {
            overlayButtons = emptyList()
            stopForInvalidState(AutoStartResult.OVERLAY_PERMISSION_MISSING)
        } catch (_: RuntimeException) {
            overlayButtons = emptyList()
            stopForInvalidState(AutoStartResult.RUNTIME_EXCEPTION)
        }
    }

    private fun createOverlayButton(): ImageButton = OverlayImageButton(
        ContextThemeWrapper(this, R.style.Theme_FloatSwitch),
    ).apply {
        setBackgroundResource(R.drawable.overlay_button_background)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val iconPadding = resources.getDimensionPixelSize(R.dimen.overlay_button_icon_padding)
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        elevation = resources.getDimension(R.dimen.overlay_button_elevation)
    }

    private fun updateOverlayButtons(installedApps: List<InstalledLauncherApp>) {
        if (overlayButtons.size != installedApps.size) return

        displayedSelections = installedApps.map(InstalledLauncherApp::selection)
        overlayButtons.zip(installedApps).forEach { (button, installedApp) ->
            button.imageTintList = null
            button.setImageDrawable(installedApp.icon)
            button.contentDescription = getString(
                if (positionLocked) R.string.overlay_button_open_application_locked
                else R.string.overlay_button_open_application,
                installedApp.selection.displayName,
            )
            button.setOnClickListener {
                openSelectedApplication(installedApp.selection)
            }
        }
    }

    private fun handleOverlayTouch(view: View, event: MotionEvent): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginGesture(view, event)
                false
            }

            MotionEvent.ACTION_MOVE -> {
                updateGesture(event)
                false
            }

            MotionEvent.ACTION_POINTER_DOWN -> false
            MotionEvent.ACTION_POINTER_UP -> {
                handlePointerUp(event)
                false
            }

            MotionEvent.ACTION_UP -> finishGesture(event)
            MotionEvent.ACTION_CANCEL -> {
                cancelGesture()
                false
            }

            else -> false
        }

    private fun beginGesture(view: View, event: MotionEvent) {
        resetGestureState()
        activePointerId = event.getPointerId(0)
        gestureView = view
        downRawX = rawX(event, 0)
        downRawY = rawY(event, 0)
        view.isPressed = true
        gestureHandler.postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun updateGesture(event: MotionEvent) {
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) {
            cancelGesture()
            return
        }

        val currentRawX = rawX(event, pointerIndex)
        val currentRawY = rawY(event, pointerIndex)
        if (!dragStarted) {
            val distance = hypot(
                (currentRawX - downRawX).toDouble(),
                (currentRawY - downRawY).toDouble(),
            )
            if (distance > touchSlop) {
                touchMovedBeyondSlop = true
                gestureView?.isPressed = false
                gestureHandler.removeCallbacks(longPressAction)
            }
            return
        }

        moveOverlayTo(
            x = dragStartX + (currentRawX - downRawX).roundToInt(),
            y = dragStartY + (currentRawY - downRawY).roundToInt(),
        )
    }

    private fun handlePointerUp(event: MotionEvent) {
        val pointerIndex = event.actionIndex
        if (event.getPointerId(pointerIndex) != activePointerId) return

        if (dragStarted) {
            moveOverlayTo(
                x = dragStartX + (rawX(event, pointerIndex) - downRawX).roundToInt(),
                y = dragStartY + (rawY(event, pointerIndex) - downRawY).roundToInt(),
            )
            finishDragging()
        } else {
            resetGestureState()
        }
    }

    private fun finishGesture(event: MotionEvent): Boolean {
        if (event.getPointerId(event.actionIndex) != activePointerId) {
            resetGestureState()
            return false
        }

        return if (dragStarted) {
            updateGesture(event)
            finishDragging()
            false
        } else {
            val shouldClick = OverlayGestureRules.shouldPerformClick(
                dragStarted = dragStarted,
                movedBeyondSlop = touchMovedBeyondSlop,
                longPressDetected = longPressDetected,
            )
            resetGestureState()
            shouldClick
        }
    }

    private fun cancelGesture() {
        if (dragStarted) {
            finishDragging()
        } else {
            resetGestureState()
        }
    }

    private fun startDragging() {
        val container = overlayView ?: return
        val params = container.layoutParams as? WindowManager.LayoutParams ?: return
        if (
            !OverlayGestureRules.shouldStartDrag(
                positionLocked = positionLocked,
                movedBeyondSlop = touchMovedBeyondSlop,
                hasActivePointer = activePointerId != MotionEvent.INVALID_POINTER_ID,
            )
        ) {
            return
        }

        dragStarted = true
        dragStartX = params.x
        dragStartY = params.y
        gestureView?.isPressed = false
        gestureView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        container.alpha = DRAGGING_ALPHA
    }

    private fun moveOverlayTo(x: Int, y: Int) {
        val container = overlayView ?: return
        if (container.width == 0 || container.height == 0) return
        val params = container.layoutParams as? WindowManager.LayoutParams ?: return
        val bounds = calculateMovementBounds(container)
        val limitedPosition = OverlayPositionMath.clamp(x, y, bounds)
        params.x = limitedPosition.x
        params.y = limitedPosition.y
        updateOverlayLayout(container, params)
    }

    private fun finishDragging() {
        val container = overlayView
        val params = container?.layoutParams as? WindowManager.LayoutParams
        if (container != null && params != null && container.width > 0 && container.height > 0) {
            val bounds = calculateMovementBounds(container)
            val limitedPosition = OverlayPositionMath.clamp(params.x, params.y, bounds)
            val side = OverlayPositionMath.nearestSide(limitedPosition.x, bounds)
            params.x = OverlayPositionMath.xForSide(side, bounds)
            params.y = limitedPosition.y
            updateOverlayLayout(container, params)

            currentPosition = OverlayPosition(
                side = side,
                verticalFraction = OverlayPositionMath.normalizeVertical(params.y, bounds),
            )
            overlayPositionStore.save(currentPosition)
        }
        resetGestureState()
    }

    private fun resetGestureState() {
        gestureHandler.removeCallbacks(longPressAction)
        gestureView?.isPressed = false
        overlayView?.alpha = 1f
        activePointerId = MotionEvent.INVALID_POINTER_ID
        gestureView = null
        touchMovedBeyondSlop = false
        longPressDetected = false
        dragStarted = false
    }

    private fun updatePositionLockState() {
        val storedPositionLocked = positionLockStore.isPositionLocked()
        if (storedPositionLocked == positionLocked) return

        resetGestureState()
        positionLocked = storedPositionLocked
    }

    private fun restoreOverlayPosition() {
        val container = overlayView ?: return
        if (dragStarted || container.width == 0 || container.height == 0) return
        val params = container.layoutParams as? WindowManager.LayoutParams ?: return
        val bounds = calculateMovementBounds(container)
        currentPosition = OverlayPositionRules.sanitize(currentPosition)
        params.x = OverlayPositionMath.xForSide(currentPosition.side, bounds)
        params.y = OverlayPositionMath.restoreVertical(
            currentPosition.verticalFraction,
            bounds,
        )
        updateOverlayLayout(container, params)
    }

    private fun calculateMovementBounds(view: View): OverlayMovementBounds {
        val usableBounds = calculateUsableScreenBounds(view)
        val margin = resources.getDimensionPixelSize(R.dimen.overlay_edge_margin)
        return OverlayPositionMath.movementBounds(
            screenLeft = usableBounds.left,
            screenTop = usableBounds.top,
            screenRight = usableBounds.right,
            screenBottom = usableBounds.bottom,
            overlayWidth = view.width,
            overlayHeight = view.height,
            margin = margin,
        )
    }

    private fun calculateUsableScreenBounds(view: View): Rect {
        val screenBounds = getScreenBounds()
        val insets = ViewCompat.getRootWindowInsets(view)?.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val left = screenBounds.left + (insets?.left ?: 0)
        val top = screenBounds.top + (insets?.top ?: 0)
        val right = (screenBounds.right - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (screenBounds.bottom - (insets?.bottom ?: 0)).coerceAtLeast(top)
        return Rect(left, top, right, bottom)
    }

    @Suppress("DEPRECATION")
    private fun getScreenBounds(): Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Rect(windowManager.currentWindowMetrics.bounds)
    } else {
        val displaySize = Point()
        windowManager.defaultDisplay.getRealSize(displaySize)
        Rect(0, 0, displaySize.x, displaySize.y)
    }

    private fun updateOverlayLayout(
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            stopForInvalidState(AutoStartResult.RUNTIME_EXCEPTION)
        } catch (_: SecurityException) {
            stopForInvalidState(AutoStartResult.OVERLAY_PERMISSION_MISSING)
        }
    }

    private fun rawX(event: MotionEvent, pointerIndex: Int): Float =
        event.rawX + event.getX(pointerIndex) - event.x

    private fun rawY(event: MotionEvent, pointerIndex: Int): Float =
        event.rawY + event.getY(pointerIndex) - event.y

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

    private fun scheduleStateCheck() {
        stateCheckHandler.removeCallbacks(stateCheck)
        stateCheckHandler.postDelayed(stateCheck, STATE_CHECK_INTERVAL_MS)
    }

    private fun stopForInvalidState(result: AutoStartResult) {
        autoStartStateStore.setOverlayRequestedActive(false)
        autoStartStateStore.recordDiagnostic(AutoStartEvent.SERVICE_RECOVERY, result)
        removeOverlay()
        stopSelf()
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: RuntimeException) {
                // A View pode já ter sido removida pelo sistema.
            }
        }
        overlayView = null
        overlayButtons = emptyList()
        displayedSelections = emptyList()
    }

    private inner class OverlayImageButton(context: Context) : AppCompatImageButton(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val shouldClick = handleOverlayTouch(this, event)
            if (event.actionMasked == MotionEvent.ACTION_UP && shouldClick) {
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()
    }

    private sealed interface SelectedApplicationsResult {
        data class Valid(
            val applications: List<InstalledLauncherApp>,
        ) : SelectedApplicationsResult

        data class Invalid(
            val reason: AutoStartResult,
        ) : SelectedApplicationsResult
    }

    companion object {
        const val ACTION_SHOW = "me.diluir.floatswitch.action.SHOW_OVERLAY"
        const val ACTION_STOP = "me.diluir.floatswitch.action.STOP_OVERLAY"

        private const val NOTIFICATION_CHANNEL_ID = "floating_shortcuts"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN_APP = 1002
        private const val REQUEST_STOP = 1003
        private const val STATE_CHECK_INTERVAL_MS = 5_000L
        private const val DRAGGING_ALPHA = 0.85f
    }
}
