package com.shebnik.flutter_screen_time.service

import android.app.Notification
import android.content.Intent
import android.util.Log
import com.shebnik.flutter_screen_time.const.Argument
import android.net.VpnService
import com.shebnik.flutter_screen_time.util.NotificationUtil
import com.shebnik.flutter_screen_time.util.NotificationUtil.startForegroundWithGroupedNotification
import com.shebnik.flutter_screen_time.util.NotificationUtil.stopForegroundWithCleanup
import java.util.concurrent.atomic.AtomicBoolean

class BlockingVpnService : VpnService() {
    companion object {
        private const val TAG = "BlockingService"

        private val isRunning = AtomicBoolean(false)

        fun isRunning(): Boolean = isRunning.get()
    }

    private var primaryDns: String? = null
    private var secondaryDns: String? = null

    override fun onCreate() {
        super.onCreate()
        NotificationUtil.createNotificationChannel(this)
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(Argument.ACTION) ?: Argument.START_ACTION

        when (action) {
            Argument.START_ACTION -> {
                primaryDns = intent?.getStringExtra(Argument.PRIMARY_DNS)
                secondaryDns = intent?.getStringExtra(Argument.SECONDARY_DNS)
                val iconName = intent?.getStringExtra(Argument.NOTIFICATION_ICON)
                val customIconResId = iconName?.let {
                    NotificationUtil.getIconResource(this, it, packageName)
                }
                val notification = createVpnNotification(customIconResId)
                startForegroundWithGroupedNotification(
                    NotificationUtil.VPN_NOTIFICATION_ID,
                    notification
                )
                startVpn()
            }

            Argument.STOP_ACTION -> {
                stopVpn()
            }
        }

        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) {
            Log.w(TAG, "VPN is already running")
            return
        }
        if (primaryDns == null || secondaryDns == null) {
            Log.e(TAG, "DNS servers not set")
            return
        }

        try {
            isRunning.set(true)
            Log.i(TAG, "VPN started successfully with DNS servers: $primaryDns, $secondaryDns")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN")
        isRunning.set(false)

        // Stop foreground service and cleanup group summary
        stopForegroundWithCleanup()
        stopSelf()

        Log.i(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        Log.d(TAG, "VPN Service destroyed")
    }

    private fun createVpnNotification(
        customIconResId: Int?,
    ): Notification {
        val notificationTitle = "Website Blocking Active"
        val notificationBody = "DNS: $primaryDns, $secondaryDns"
        return NotificationUtil.createVpnNotification(
            this,
            notificationTitle,
            notificationBody,
            customIconResId,
        )
    }
}