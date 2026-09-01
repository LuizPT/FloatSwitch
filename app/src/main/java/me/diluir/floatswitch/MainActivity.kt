package me.diluir.floatswitch

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var permissionStateText: TextView
    private lateinit var feedbackText: TextView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        permissionStateText = findViewById(R.id.overlayPermissionState)
        feedbackText = findViewById(R.id.feedbackText)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.grantOverlayPermissionButton).setOnClickListener {
            openOverlayPermissionSettings()
        }
        findViewById<Button>(R.id.showShortcutsButton).setOnClickListener {
            showShortcuts()
        }
        findViewById<Button>(R.id.hideShortcutsButton).setOnClickListener {
            hideShortcuts()
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayPermissionState()
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

    private fun showShortcuts() {
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
            feedbackText.setText(R.string.shortcuts_start_requested)
        } catch (_: RuntimeException) {
            feedbackText.setText(R.string.shortcuts_start_failed)
        }
    }

    private fun hideShortcuts() {
        stopService(Intent(this, OverlayService::class.java))
        feedbackText.setText(R.string.shortcuts_hidden)
    }
}
