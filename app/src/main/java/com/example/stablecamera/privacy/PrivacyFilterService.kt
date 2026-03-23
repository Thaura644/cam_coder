package com.example.stablecamera.privacy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import com.example.stablecamera.privacy.PrivacyOverlayView
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

class PrivacyFilterService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_SHOW) {
            showOverlay()
        } else if (action == ACTION_HIDE) {
            hideOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayView = FrameLayout(this).apply {
            addView(PrivacyOverlayView(context))
            // "Narrow Viewing Angle" effect: a vignette-like darkening
            // For simplicity, we use a dark semi-transparent background with a center "hole"
            // In a more advanced version, we'd use a custom view with a radial gradient
            setBackgroundColor(Color.parseColor("#CC000000"))

            // "Rainbow Lights" aesthetic indicator
            val rainbowView = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    20,
                    Gravity.TOP
                )
                // In a real app, we'd use an AnimatedVectorDrawable or a custom shader
                setBackgroundColor(Color.MAGENTA)
            }
            addView(rainbowView)
        }

        windowManager?.addView(overlayView, params)
    }

    private fun hideOverlay() {
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Privacy Filter Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Privacy Filter Active")
            .setContentText("Protecting your screen content.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "PrivacyFilterChannel"
        const val ACTION_SHOW = "com.example.stablecamera.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.example.stablecamera.HIDE_OVERLAY"
    }
}
