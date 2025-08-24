package com.shebnik.flutter_screen_time.service

import android.annotation.SuppressLint
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
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.util.NotificationUtil
import com.shebnik.flutter_screen_time.util.NotificationUtil.startForegroundWithGroupedNotification
import com.shebnik.flutter_screen_time.util.NotificationUtil.stopForegroundWithCleanup

class BlockAppsService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var blockedApps: List<String> = emptyList()
    private var layoutName: String = DEFAULT_LAYOUT_NAME
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var customIconResId: Int? = null
    private var groupIconResId: Int? = null
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null

    companion object {
        const val DEFAULT_LAYOUT_NAME = "block_overlay"
        const val MONITORING_INTERVAL = 1000L // 1 second
        const val TAG = "BlockAppsService"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        NotificationUtil.createNotificationChannel(this)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Extract data from intent
        intent?.let {
            blockedApps = it.getStringArrayListExtra(Argument.BUNDLE_IDS) ?: emptyList()
            layoutName =
                it.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME) ?: DEFAULT_LAYOUT_NAME
            callerPackageName =
                it.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE) ?: packageName
            notificationTitle = it.getStringExtra(Argument.NOTIFICATION_TITLE)
            notificationBody = it.getStringExtra(Argument.NOTIFICATION_BODY)

            val customIconName = it.getStringExtra(Argument.NOTIFICATION_ICON)
            customIconResId = if (customIconName != null) {
                NotificationUtil.getIconResource(this, customIconName, callerPackageName)
            } else {
                null
            }

            val groupIconName = it.getStringExtra(Argument.NOTIFICATION_GROUP_ICON)
            groupIconResId = if (groupIconName != null) {
                NotificationUtil.getIconResource(this, groupIconName, callerPackageName)
            } else {
                null
            }
        }

        Log.d(TAG, "Blocked apps: $blockedApps")

        // Create and start foreground with notification
        val notification = NotificationUtil.createBlockAppsNotification(
            context = this,
            title = notificationTitle,
            body = notificationBody,
            customIconResId = customIconResId,
            blockedAppsCount = blockedApps.size,
            groupIconResId = groupIconResId
        )

        startForegroundWithGroupedNotification(
            NotificationUtil.BLOCK_APPS_NOTIFICATION_ID,
            notification
        )

        // Start monitoring apps
        startAppMonitoring()

        return START_STICKY
    }

    override fun onDestroy() {
        stopAppMonitoring()
        hideOverlay()
        stopForegroundWithCleanup()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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
                    Log.d(TAG, "Blocked app detected: $packageName")
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
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
            Log.d(TAG, "Overlay shown")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                overlayView = null
                Log.d(TAG, "Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay", e)
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

            @SuppressLint("DiscouragedApi")
            resources.getIdentifier(layoutName, "layout", callerPackageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout resource", e)
            0
        }
    }
}