package com.shebnik.flutter_screen_time.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat

object NotificationUtil {

    // Notification constants
    const val CHANNEL_ID = "screen_time_blocking_service_channel"
    const val GROUP_KEY = "com.shebnik.flutter_screen_time.BLOCKING_GROUP"
    const val GROUP_SUMMARY_ID = 1000
    const val BLOCK_APPS_NOTIFICATION_ID = 1001
    const val WEBSITES_BLOCKING_NOTIFICATION_ID = 1002

    private const val TAG = "NotificationUtils"

    /**
     * Creates the notification channel for all blocking services
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Time Blocking Services",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for app and website blocking services"
            setShowBadge(false)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates a notification for the BlockAppsService
     */
    fun createBlockAppsNotification(
        context: Context,
        title: String?,
        body: String?,
        customIconResId: Int?,
        blockedAppsCount: Int,
        groupIconResId: Int? = null
    ): Notification {
        val notificationTitle = title ?: "App Blocking Active"
        val notificationBody = body ?: "Monitoring $blockedAppsCount apps"
        val iconResId = customIconResId ?: android.R.drawable.ic_lock_idle_lock

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setSmallIcon(iconResId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setGroup(GROUP_KEY)
            .build()

        updateGroupSummary(context, groupIconResId)
        return notification
    }

    /**
     * Creates a notification for the WebsitesBlockingAccessibilityService
     */
    fun createWebsiteBlockingNotification(
        context: Context,
        title: String?,
        body: String?,
        customIconResId: Int?,
        blockedDomainsCount: Int,
        blockWebsitesOnlyInBrowsers: Boolean,
        groupIconResId: Int? = null
    ): Notification {
        val notificationTitle = title ?: "Website Blocking Active"
        val modeText = if (blockWebsitesOnlyInBrowsers) "browsers only" else "browsers and apps"
        val notificationBody = body ?: "Monitoring $blockedDomainsCount domains in $modeText"
        val iconResId = customIconResId ?: android.R.drawable.ic_dialog_alert

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setSmallIcon(iconResId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setGroup(GROUP_KEY)
            .build()

        updateGroupSummary(context, groupIconResId)
        return notification
    }

    /**
     * Updates or creates the group summary notification
     */
    private fun updateGroupSummary(context: Context, groupIconResId: Int? = null) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check active notifications in our group
        val activeNotifications = notificationManager.activeNotifications
        val groupNotifications = activeNotifications.filter {
            it.groupKey?.contains(GROUP_KEY) == true && it.id != GROUP_SUMMARY_ID
        }

        // Create summary notification
        val summaryText = when (groupNotifications.size) {
            0 -> "Screen Time Control active"
            1 -> "Screen Time Control active"
            else -> "${groupNotifications.size} blocking services active"
        }

        // Use group icon if provided, otherwise use default
        val summaryIconResId = groupIconResId ?: android.R.drawable.ic_menu_info_details

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Screen Time Controls")
            .setContentText(summaryText)
            .setSmallIcon(summaryIconResId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(GROUP_SUMMARY_ID, summaryNotification)
    }

    /**
     * Removes the group summary if no other notifications are active
     */
    fun cleanupGroupSummary(context: Context, groupIconResId: Int? = null) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Small delay to ensure notification cancellation is processed
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val activeNotifications = notificationManager.activeNotifications
            val groupNotifications = activeNotifications.filter {
                it.groupKey?.contains(GROUP_KEY) == true && it.id != GROUP_SUMMARY_ID
            }

            // If no other notifications in group, remove summary
            if (groupNotifications.isEmpty()) {
                notificationManager.cancel(GROUP_SUMMARY_ID)
                Log.d(TAG, "Group summary notification removed")
            } else {
                // Update summary with current count
                updateGroupSummary(context, groupIconResId)
            }
        }, 100)
    }

    /**
     * Gets custom icon resource from caller package
     */
    fun getIconResource(context: Context, iconName: String, callerPackageName: String): Int? {
        return try {
            val resources = if (callerPackageName != context.packageName) {
                context.packageManager.getResourcesForApplication(callerPackageName)
            } else {
                context.resources
            }

            @SuppressLint("DiscouragedApi")
            val resId = resources.getIdentifier(iconName, "drawable", callerPackageName)
            if (resId != 0) resId else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting icon resource: $iconName", e)
            null
        }
    }

    /**
     * Extension function to start foreground with proper notification management
     */
    fun Service.startForegroundWithGroupedNotification(
        notificationId: Int,
        notification: Notification
    ) {
        startForeground(notificationId, notification)
    }

    /**
     * Extension function to stop foreground and cleanup group summary
     */
    fun Service.stopForegroundWithCleanup(groupIconResId: Int? = null) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        cleanupGroupSummary(this, groupIconResId)
    }
}