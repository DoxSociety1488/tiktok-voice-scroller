package com.tiktokscroller

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tiktokscroller.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUI()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateUI()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnAudioPermission.setOnClickListener {
            requestAudioPermission()
        }

        binding.btnAccessibilityPermission.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnNotificationPermission.setOnClickListener {
            requestNotificationPermission()
        }

        binding.btnToggleService.setOnClickListener {
            toggleService()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAudio = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasAccessibility = isAccessibilityServiceEnabled()
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        binding.btnOverlayPermission.isEnabled = !hasOverlay
        binding.statusOverlay.text = if (hasOverlay) "Granted" else "Not Granted"

        binding.btnAudioPermission.isEnabled = !hasAudio
        binding.statusAudio.text = if (hasAudio) "Granted" else "Not Granted"

        binding.btnAccessibilityPermission.isEnabled = !hasAccessibility
        binding.statusAccessibility.text = if (hasAccessibility) "Granted" else "Not Granted"

        binding.btnNotificationPermission.isEnabled = !hasNotification
        binding.statusNotification.text = if (hasNotification) "Granted" else "Not Granted"

        val allGranted = hasOverlay && hasAudio && hasAccessibility && hasNotification
        binding.btnToggleService.isEnabled = allGranted

        val isRunning = OverlayService.isRunning
        binding.btnToggleService.text = if (isRunning) "Stop Listening" else "Start Listening"
        binding.statusService.text = if (isRunning) "Running" else "Stopped"
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestAudioPermission() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            this,
            "Enable 'TikTok Voice Scroller' in Accessibility settings",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun toggleService() {
        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            val intent = Intent(this, OverlayService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
        binding.root.postDelayed({ updateUI() }, 500)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == ScrollAccessibilityService::class.java.name
        }
    }
}
