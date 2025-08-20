package com.shebnik.flutter_screen_time.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.shebnik.flutter_screen_time.const.Argument

class BlockAppsService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var blockedApps: List<String> = emptyList()
    private var layoutName: String = DEFAULT_LAYOUT_NAME
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null

    companion object {
        const val DEFAULT_LAYOUT_NAME = "block_overlay"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "block_apps_service_channel"
        const val MONITORING_INTERVAL = 1000L // 1 second
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.d("BlockAppsService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BlockAppsService", "Service started")

        // Extract data from intent
        intent?.let {
            blockedApps = it.getStringArrayListExtra(Argument.BUNDLE_IDS) ?: emptyList()
            layoutName =
                it.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME) ?: DEFAULT_LAYOUT_NAME
            callerPackageName =
                it.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE) ?: packageName
            notificationTitle =
                it.getStringExtra(Argument.NOTIFICATION_TITLE) ?: "App Blocking Active"
            notificationBody = it.getStringExtra(Argument.NOTIFICATION_BODY)
                ?: "Monitoring ${blockedApps.size} apps"
        }

        Log.d("BlockAppsService", "Blocked apps: $blockedApps")

        // Start foreground with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start monitoring apps
        startAppMonitoring()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAppMonitoring()
        hideOverlay()
        Log.d("BlockAppsService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "App Block Service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Service for blocking specified applications"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(notificationTitle)
            .setContentText(notificationBody).setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
    }

    private fun startAppMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        monitoringRunnable = object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    checkForegroundApp()
                    handler.postDelayed(this, MONITORING_INTERVAL)
                }
            }
        }
        handler.post(monitoringRunnable!!)
    }

    private fun stopAppMonitoring() {
        isMonitoring = false
        monitoringRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun checkForegroundApp() {
        try {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTime = System.currentTimeMillis()
            val events =
                usageStatsManager.queryEvents(currentTime - MONITORING_INTERVAL * 2, currentTime)

            var foregroundApp: String? = null
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    foregroundApp = event.packageName
                }
            }

            foregroundApp?.let { packageName ->
                if (blockedApps.contains(packageName)) {
                    Log.d("BlockAppsService", "Blocked app detected: $packageName")
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        } catch (e: Exception) {
            Log.e("BlockAppsService", "Error checking foreground app", e)
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        try {
            // Create overlay layout
            val layoutInflater = LayoutInflater.from(this)
            val layoutResId = getLayoutResource(layoutName)

            overlayView = if (layoutResId != 0) {
                layoutInflater.inflate(layoutResId, null)
            } else {
                // Fallback to default layout if specified layout is not found
                layoutInflater.inflate(getLayoutResource(DEFAULT_LAYOUT_NAME), null)
            }

            // Set up window parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            windowManager?.addView(overlayView, params)
            Log.d("BlockAppsService", "Overlay shown")

        } catch (e: Exception) {
            Log.e("BlockAppsService", "Error showing overlay", e)
        }
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                overlayView = null
                Log.d("BlockAppsService", "Overlay hidden")
            } catch (e: Exception) {
                Log.e("BlockAppsService", "Error hiding overlay", e)
            }
        }
    }

    private fun getLayoutResource(layoutName: String): Int {
        return try {
            val resources = if (callerPackageName != packageName) {
                packageManager.getResourcesForApplication(callerPackageName)
            } else {
                resources
            }

            resources.getIdentifier(layoutName, "layout", callerPackageName)
        } catch (e: Exception) {
            Log.e("BlockAppsService", "Error getting layout resource", e)
            0
        }
    }
}