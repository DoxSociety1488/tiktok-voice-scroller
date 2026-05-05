package com.tiktokscroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class OverlayService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "voice_scroller_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var voiceManager: VoiceRecognitionManager

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
        startVoiceRecognition()
    }

    override fun onDestroy() {
        isRunning = false
        voiceManager.stop()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Scroller",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Listening for voice commands to scroll TikTok"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TikTok Voice Scroller")
            .setContentText("Listening for \"skip\" command...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        setupDragBehavior(params)
        windowManager.addView(overlayView, params)
    }

    private fun setupDragBehavior(params: WindowManager.LayoutParams) {
        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun startVoiceRecognition() {
        val indicator = overlayView.findViewById<ImageView>(R.id.micIndicator)

        voiceManager = VoiceRecognitionManager(
            context = this,
            onSkipDetected = {
                indicator.post {
                    indicator.setColorFilter(0xFF00FF00.toInt())
                    ScrollAccessibilityService.performScroll()
                    indicator.postDelayed({
                        indicator.setColorFilter(0xFFFFFFFF.toInt())
                    }, 500)
                }
            },
            onListeningStateChanged = { listening ->
                indicator.post {
                    indicator.alpha = if (listening) 1.0f else 0.5f
                }
            }
        )
        voiceManager.start()
    }
}
