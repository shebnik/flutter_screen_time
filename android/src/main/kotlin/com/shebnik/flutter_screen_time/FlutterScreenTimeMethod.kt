package com.shebnik.flutter_screen_time

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.const.Field
import com.shebnik.flutter_screen_time.const.PermissionRequestCode
import com.shebnik.flutter_screen_time.const.AuthorizationStatus
import com.shebnik.flutter_screen_time.const.PermissionType
import com.shebnik.flutter_screen_time.service.BlockAppsService
import com.shebnik.flutter_screen_time.service.BlockingService
import com.shebnik.flutter_screen_time.service.WebsitesBlockingAccessibilityService
import com.shebnik.flutter_screen_time.util.ApplicationInfoUtil
import com.shebnik.flutter_screen_time.util.Logger
import com.shebnik.flutter_screen_time.util.logDebug
import com.shebnik.flutter_screen_time.util.logError
import com.shebnik.flutter_screen_time.util.logInfo
import com.shebnik.flutter_screen_time.util.logSuccess
import com.shebnik.flutter_screen_time.util.logWarning
import java.io.ByteArrayOutputStream
import android.net.VpnService
import com.shebnik.flutter_screen_time.service.BlockingVpnService


object FlutterScreenTimeMethod {

    const val TAG = "FlutterScreenTimeMethod"

    /**
     * Configure the plugin with various settings
     * @param logFilePath Optional path to configure file logging
     * @return Map containing configuration results
     */
    fun configure(logFilePath: String?): Map<String, Any> {
        logInfo(TAG, "🔧 Configuring Flutter Screen Time plugin")

        val configuredItems = mutableListOf<String>()

        // Configure logging if logFilePath is provided
        logFilePath?.let { path ->
            Logger.getInstance().configureLogFile(path)
            configuredItems.add("logging")
            logSuccess(TAG, "Logging configured: $path")
        }

        return mapOf(
            "configured" to configuredItems
        )
    }

    fun authorizationStatus(
        context: Context,
        type: PermissionType = PermissionType.APP_USAGE,
        isOnlyWebsitesBlocking: Boolean
    ): AuthorizationStatus {
        logInfo(TAG, "Requesting authorizationStatus for PermissionType ${type.name}")
        when (type) {
            PermissionType.APP_USAGE -> {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )

                return when (mode) {
                    AppOpsManager.MODE_ALLOWED -> {
                        AuthorizationStatus.APPROVED
                    }

                    AppOpsManager.MODE_IGNORED -> {
                        AuthorizationStatus.DENIED
                    }

                    else -> {
                        AuthorizationStatus.NOT_DETERMINED
                    }
                }
            }

            PermissionType.DRAW_OVERLAY -> {
                val result = Settings.canDrawOverlays(context)
                return if (result) {
                    AuthorizationStatus.APPROVED
                } else {
                    AuthorizationStatus.DENIED
                }
            }


            PermissionType.NOTIFICATION -> {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    )
                    when (permission) {
                        PackageManager.PERMISSION_GRANTED -> {
                            AuthorizationStatus.APPROVED
                        }

                        PackageManager.PERMISSION_DENIED -> {
                            AuthorizationStatus.DENIED
                        }

                        else -> {
                            AuthorizationStatus.NOT_DETERMINED
                        }
                    }
                } else {
                    AuthorizationStatus.APPROVED
                }
            }

            PermissionType.ACCESSIBILITY_SETTINGS -> {
                // Check if the blocking accessibility service is enabled
                val am =
                    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val enabledServices =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)

                for (enabledService in enabledServices) {
                    val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
                    if (enabledServiceInfo.packageName.equals(context.packageName) && enabledServiceInfo.name.equals(
                            if (isOnlyWebsitesBlocking) WebsitesBlockingAccessibilityService::class.java.name
                            else BlockingService::class.java.name
                        )
                    ) return AuthorizationStatus.APPROVED
                }
                return AuthorizationStatus.DENIED
            }

            PermissionType.VPN -> {
                val intent = VpnService.prepare(context)
                return if (intent == null) {
                    AuthorizationStatus.APPROVED
                } else {
                    AuthorizationStatus.NOT_DETERMINED // Permission not yet granted
                }
            }

            PermissionType.AUTOSTART -> {
                // For autostart, we can't reliably check if it's enabled
                // Return NOT_DETERMINED to prompt user to check manually
                return AuthorizationStatus.APPROVED
            }
        }
    }

    fun requestPermission(
        activity: Activity,
        type: PermissionType = PermissionType.APP_USAGE,
        isOnlyWebsitesBlocking: Boolean
    ): Boolean {
        logInfo(TAG, "Requesting permission for ${type.name}")
        val packageUri = "package:${activity.packageName}".toUri()
        return when (type) {
            PermissionType.APP_USAGE -> {
                try {
                    val intent = Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri
                    )
                    activity.startActivityForResult(
                        intent, PermissionRequestCode.REQUEST_CODE_APP_USAGE
                    )
                    true
                } catch (exception: Exception) {
                    logError(
                        "requestPermission",
                        "Failed to request app usage permission: ${exception.localizedMessage}",
                        exception
                    )
                    false
                }
            }

            PermissionType.DRAW_OVERLAY -> {
                try {
                    if (!Settings.canDrawOverlays(activity)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri
                        )
                        activity.startActivityForResult(
                            intent, PermissionRequestCode.REQUEST_CODE_DRAW_OVERLAY
                        )
                        true
                    } else {
                        // Permission already granted, no need to request
                        true
                    }
                } catch (exception: Exception) {
                    exception.localizedMessage?.let {
                        logError("requestPermission manageOverlayPermission", it)
                    }
                    false
                }
            }

            PermissionType.NOTIFICATION -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Request runtime permission using ActivityCompat
                        androidx.core.app.ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            PermissionRequestCode.REQUEST_CODE_NOTIFICATION
                        )
                        true
                    } else {
                        true // Notification permission not needed for Android < 13
                    }
                } catch (exception: Exception) {
                    exception.localizedMessage?.let {
                        logError(
                            "requestPermission NOTIFICATION",
                            it
                        )
                    }
                    false
                }
            }

            PermissionType.ACCESSIBILITY_SETTINGS -> {
                try {
                    val intent = Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                    )
                    activity.startActivityForResult(
                        intent,
                        if (isOnlyWebsitesBlocking) PermissionRequestCode.REQUEST_CODE_ACCESSIBILITY_WEBSITES_ONLY else PermissionRequestCode.REQUEST_CODE_ACCESSIBILITY_APPS_AND_WEBSITES
                    )
                    true
                } catch (e: Exception) {
                    e.localizedMessage?.let {
                        logError(
                            "requestPermission ACCESSIBILITY_SETTINGS", it
                        )
                    }
                    false
                }
            }

            PermissionType.VPN -> {
                try {
                    val intent = VpnService.prepare(activity)
                        ?: return true // Already has permission
                    activity.startActivityForResult(
                        intent,
                        PermissionRequestCode.REQUEST_CODE_VPN
                    )
                    true
                } catch (e: Exception) {
                    e.localizedMessage?.let {
                        logError(
                            "requestPermission VPN", it
                        )
                    }
                    false
                }
            }

            PermissionType.AUTOSTART -> {
                try {
                    requestAutoStartPermission(activity)
                    true
                } catch (e: Exception) {
                    e.localizedMessage?.let {
                        logError("requestPermission AUTOSTART", it)
                    }
                    true
                }
            }
        }
    }

    fun handlePermissionResult(
        context: Context, type: PermissionType, isOnlyWebsitesBlocking: Boolean
    ): Boolean {
        val status = authorizationStatus(context, type, isOnlyWebsitesBlocking)
        return when (type) {
            PermissionType.AUTOSTART -> {
                // For autostart, we can't determine the actual status
                // Return true to indicate the user was shown the settings
                true
            }
            else -> status == AuthorizationStatus.APPROVED
        }
    }

    private fun requestAutoStartPermission(activity: Activity): Boolean {
        try {
            val intent = Intent()
            val manufacturer = Build.MANUFACTURER.lowercase()
            
            when (manufacturer) {
                "xiaomi", "poco", "redmi" -> {
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                "letv" -> {
                    intent.component = ComponentName(
                        "com.letv.android.letvsafe",
                        "com.letv.android.letvsafe.AutobootManageActivity"
                    )
                }
                "oppo" -> {
                    intent.component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                "vivo" -> {
                    intent.component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                "honor" -> {
                    intent.component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
                "huawei" -> {
                    intent.component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                "samsung" -> {
                    intent.component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                }
                "oneplus" -> {
                    intent.component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
                "nokia" -> {
                    intent.component = ComponentName(
                        "com.evenwell.powersaving.g3",
                        "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"
                    )
                }
                "asus" -> {
                    intent.component = ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutoStartActivity"
                    )
                }
                "realme" -> {
                    intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
                else -> {
                    // Fallback to battery optimization settings
                    intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
            }

            val packageManager = activity.packageManager
            val resolveInfos = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            
            if (resolveInfos.isNotEmpty()) {
                activity.startActivityForResult(intent, PermissionRequestCode.REQUEST_CODE_AUTOSTART)
                logInfo(TAG, "Auto-start permission intent launched for $manufacturer")
                return true
            } else {
                logWarning(TAG, "No auto-start settings found for $manufacturer")
                return true
            }
        } catch (e: Exception) {
            logError(TAG, "Error requesting auto-start permission", e)
            return true
        }
    }

    fun hasAutoStartPermission(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when (manufacturer) {
            "xiaomi", "poco", "redmi", "letv", "oppo", "vivo", 
            "honor", "huawei", "samsung", "oneplus", "nokia", 
            "asus", "realme" -> true
            else -> false
        }
    }

    fun installedApps(
        context: Context, ignoreSystemApps: Boolean = true, bundleIds: List<*>? = null
    ): Map<String, Any> {
        try {
            val packageManager = context.packageManager
            val apps = ArrayList<ApplicationInfo>()

            if (bundleIds != null && bundleIds.isNotEmpty()) {
                // Directly query specific packages
                for (bundleId in bundleIds) {
                    val packageName = bundleId.toString()
                    try {
                        val appInfo = packageManager.getApplicationInfo(
                            packageName, PackageManager.GET_META_DATA
                        )

                        // Apply filtering logic
                        val shouldInclude = if (ignoreSystemApps) {
                            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && appInfo.packageName != context.packageName
                        } else {
                            true
                        }

                        if (shouldInclude) {
                            apps.add(appInfo)
                        }
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Package not found, skip it
                        logWarning("installedApps error: $e", "Package not found: $packageName")
                    }
                }
            } else {
                // Get all installed applications
                val installedApplications =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getInstalledApplications(
                            PackageManager.ApplicationInfoFlags.of(
                                PackageManager.GET_META_DATA.toLong()
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION") packageManager.getInstalledApplications(
                            PackageManager.GET_META_DATA
                        )
                    }

                if (ignoreSystemApps) {
                    val filtered = installedApplications.filter { app ->
                        (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && app.packageName != context.packageName
                    }
                    apps.addAll(filtered)
                } else {
                    apps.addAll(installedApplications)
                }
            }

            val appMap = ArrayList<MutableMap<String, Any?>>()

            for (app in apps) {
                val appCategory = ApplicationInfoUtil.category(app.category)
                val packageInfo = packageManager.getPackageInfo(app.packageName, 0)
                val appIcon = appIconAsBase64(packageManager, app.packageName)

                val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
                }
                val data = mutableMapOf(
                    Field.APP_NAME to app.loadLabel(packageManager),
                    Field.PACKAGE_NAME to app.packageName,
                    Field.ENABLED to app.enabled,
                    Field.CATEGORY to appCategory,
                    Field.VERSION_NAME to packageInfo.versionName,
                    Field.VERSION_CODE to versionCode,
                )

                if (appIcon != null) {
                    data[Field.APP_ICON] = appIcon
                }

                appMap.add(data)
            }

            return mutableMapOf(
                Field.STATUS to true,
                Field.DATA to appMap,
            )
        } catch (exception: Exception) {
            exception.localizedMessage?.let { logError("installedApps", it) }

            return mutableMapOf(
                Field.STATUS to false,
                Field.DATA to ArrayList<MutableMap<String, Any?>>(),
            )
        }
    }

    fun appIconAsBase64(
        packageManager: PackageManager,
        packageName: String,
    ): String? {
        return try {
            val drawable: Drawable = packageManager.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)  // Convert to Base64
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun blockApps(
        context: Context,
        bundleIds: List<String>,
        layoutName: String? = null,
        notificationTitle: String? = null,
        notificationBody: String? = null,
        notificationIcon: String? = null,
        notificationGroupIcon: String? = null,
    ): Boolean {
        if (bundleIds.isEmpty()) return false

        try {
            // Start BlockAppService
            val intent = Intent(context, BlockAppsService::class.java).apply {
                putStringArrayListExtra(Argument.BUNDLE_IDS, ArrayList(bundleIds))

                val callerPackageName = context.packageName
                putExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, callerPackageName)
                putExtra(
                    Argument.BLOCK_OVERLAY_LAYOUT_NAME,
                    layoutName ?: BlockAppsService.DEFAULT_LAYOUT_NAME
                )

                putExtra(Argument.NOTIFICATION_TITLE, notificationTitle)
                putExtra(Argument.NOTIFICATION_BODY, notificationBody)
                putExtra(Argument.NOTIFICATION_ICON, notificationIcon)
                putExtra(Argument.NOTIFICATION_GROUP_ICON, notificationGroupIcon)
            }

            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (e is ForegroundServiceStartNotAllowedException) {
                        logError(TAG, "Foreground service start not allowed", e)
                    } else {
                        logError(TAG, "Foreground service start not allowed", e)
                    }
                } else {
                    logError(TAG, "Foreground service start not allowed", e)
                }

                return false
            }

            return true
        } catch (e: Exception) {
            logError(TAG, "Error starting block", e)
            return false
        }
    }

    fun disableAppsBlocking(context: Context): Boolean {
        return try {
            val intent = Intent(context, BlockAppsService::class.java)
            context.stopService(intent)
            logDebug(TAG, "BlockAppService stopped successfully")
            true
        } catch (e: Exception) {
            logError(TAG, "Error stopping block service", e)
            false
        }
    }

    fun blockDomains(
        context: Context,
        domains: List<String>,
        notificationTitle: String? = null,
        notificationBody: String? = null,
        notificationIcon: String? = null,
        notificationGroupIcon: String? = null,
        blockWebsitesOnlyInBrowsers: Boolean,
        layoutName: String?,
        useOverlayCountdown: Boolean,
        overlayCountdownSeconds: Int,
    ): Boolean {
        if (domains.isEmpty()) return false

        val intent = Intent(context, WebsitesBlockingAccessibilityService::class.java).apply {
            putStringArrayListExtra(Argument.BLOCKED_WEB_DOMAINS, ArrayList(domains))

            val callerPackageName = context.packageName
            putExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, callerPackageName)

            putExtra(Argument.NOTIFICATION_TITLE, notificationTitle)
            putExtra(Argument.NOTIFICATION_BODY, notificationBody)
            putExtra(Argument.NOTIFICATION_ICON, notificationIcon)
            putExtra(Argument.NOTIFICATION_GROUP_ICON, notificationGroupIcon)

            putExtra(Argument.BLOCK_WEBSITES_ONLY_IN_BROWSERS, blockWebsitesOnlyInBrowsers)

            putExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME, layoutName)
            putExtra(Argument.USE_OVERLAY_COUNTDOWN, useOverlayCountdown)
            putExtra(Argument.OVERLAY_COUNTDOWN_SECONDS, overlayCountdownSeconds)
        }

        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            logError(TAG, "Error starting domain blocking", e)
            return false
        }
        logSuccess(TAG, "Domain blocking started successfully")
        return true
    }

    fun stopBlockingDomains(context: Context): Boolean {
        return try {
            val intent = Intent(WebsitesBlockingAccessibilityService.ACTION_STOP_BLOCKING)
            context.sendBroadcast(intent)
            logDebug(TAG, "Domain blocking stopped successfully")
            true
        } catch (e: Exception) {
            logError(TAG, "Error stopping domain blocking", e)
            false
        }
    }

    fun blockAppsAndWebDomains(
        context: Context,
        bundleIds: List<String>,
        domains: List<String>,
        notificationTitle: String? = null,
        notificationBody: String? = null,
        notificationIcon: String? = null,
        layoutName: String?,
        useOverlayCountdown: Boolean,
        overlayCountdownSeconds: Int,
        useDNSWebsiteBlocking: Boolean,
        forwardDnsServer: String?,
        forwardDnsServerName: String?,
        uninstallPreventionKeywords: List<*>?,
        appName: String? = null
    ): Boolean {
        // Save configuration to SharedPreferences first
        val prefs = context.getSharedPreferences("blocking_service_config", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save all configuration parameters - remove key if empty/null
        if (bundleIds.isNotEmpty()) {
            editor.putStringSet(Argument.BUNDLE_IDS, bundleIds.toSet())
        } else {
            editor.remove(Argument.BUNDLE_IDS)
        }
        
        if (domains.isNotEmpty()) {
            editor.putStringSet(Argument.BLOCKED_WEB_DOMAINS, domains.toSet())
        } else {
            editor.remove(Argument.BLOCKED_WEB_DOMAINS)
        }
        
        if (notificationTitle != null) {
            editor.putString(Argument.NOTIFICATION_TITLE, notificationTitle)
        } else {
            editor.remove(Argument.NOTIFICATION_TITLE)
        }
        
        if (notificationBody != null) {
            editor.putString(Argument.NOTIFICATION_BODY, notificationBody)
        } else {
            editor.remove(Argument.NOTIFICATION_BODY)
        }
        
        if (notificationIcon != null) {
            editor.putString(Argument.NOTIFICATION_ICON, notificationIcon)
        } else {
            editor.remove(Argument.NOTIFICATION_ICON)
        }
        
        if (layoutName != null) {
            editor.putString(Argument.BLOCK_OVERLAY_LAYOUT_NAME, layoutName)
        } else {
            editor.remove(Argument.BLOCK_OVERLAY_LAYOUT_NAME)
        }
        
        editor.putBoolean(Argument.USE_OVERLAY_COUNTDOWN, useOverlayCountdown)
        editor.putInt(Argument.OVERLAY_COUNTDOWN_SECONDS, overlayCountdownSeconds)
        editor.putBoolean(Argument.USE_DNS_WEBSITE_BLOCKING, useDNSWebsiteBlocking)
        
        if (forwardDnsServer != null) {
            editor.putString(Argument.FORWARD_DNS_SERVER, forwardDnsServer)
        } else {
            editor.remove(Argument.FORWARD_DNS_SERVER)
        }
        
        if (forwardDnsServerName != null) {
            editor.putString(Argument.FORWARD_DNS_SERVER_NAME, forwardDnsServerName)
        } else {
            editor.remove(Argument.FORWARD_DNS_SERVER_NAME)
        }
        
        if (appName != null) {
            editor.putString(Argument.APP_NAME, appName)
        } else {
            editor.remove(Argument.APP_NAME)
        }
        
        editor.putString(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, context.packageName)
        
        if (uninstallPreventionKeywords != null && uninstallPreventionKeywords.isNotEmpty()) {
            editor.putStringSet(Argument.UNINSTALL_PREVENTION_KEYWORDS, uninstallPreventionKeywords.map { it.toString() }.toSet())
        } else {
            editor.remove(Argument.UNINSTALL_PREVENTION_KEYWORDS)
        }
        
        editor.apply()
        logDebug(TAG, "Configuration saved to SharedPreferences")
        
        var success = true
        
        // Start VPN service only if DNS website blocking is enabled AND there are domains to block
        if (useDNSWebsiteBlocking && domains.isNotEmpty()) {
            logDebug(TAG, "Starting VPN service for DNS website blocking")
            val vpnIntent = Intent(context, BlockingVpnService::class.java)
            
            try {
                context.startForegroundService(vpnIntent)
                logDebug(TAG, "VPN service started for DNS blocking")
            } catch (e: Exception) {
                logError(TAG, "Error starting VPN service", e)
                success = false
            }
        } else if (useDNSWebsiteBlocking && domains.isEmpty()) {
            logWarning(TAG, "DNS website blocking enabled but no domains provided - VPN service not started")
            stopBlockingVpnService(context)
        }
        
        // Start AccessibilityService only if there's work to do
        val hasAppsToBlock = bundleIds.isNotEmpty()
        val hasWebsitesToBlock = domains.isNotEmpty() && !useDNSWebsiteBlocking
        val hasUninstallPrevention = uninstallPreventionKeywords?.isNotEmpty() == true
        
        if (hasAppsToBlock || hasWebsitesToBlock || hasUninstallPrevention) {
            logDebug(TAG, "Starting AccessibilityService for app/website blocking")
            val intent = Intent(context, BlockingService::class.java)
            try {
                context.startForegroundService(intent)
                logDebug(TAG, "AccessibilityService started")
            } catch (e: Exception) {
                logError(TAG, "Error starting AccessibilityService", e)
                success = false
            }
        } else {
            logDebug(TAG, "AccessibilityService not needed - no apps, websites, or uninstall prevention")
            stopBlockingAppsAndWebDomains(context)
        }
        
        // Check if any services were started
        val vpnNeeded = useDNSWebsiteBlocking && domains.isNotEmpty()
        val accessibilityNeeded = hasAppsToBlock || hasWebsitesToBlock || hasUninstallPrevention
        
        if (!vpnNeeded && !accessibilityNeeded) {
            logWarning(TAG, "No blocking services needed - nothing to start")
            return false
        }
        
        return success
    }


    fun stopBlockingAppsAndWebDomains(context: Context): Boolean {
        return try {
            var intent = Intent(BlockingService.ACTION_STOP_BLOCKING)
            context.sendBroadcast(intent)
            logDebug(TAG, "Domain blocking stopped successfully")
            true
        } catch (e: Exception) {
            logError(TAG, "Error stopping domain blocking", e)
            false
        }
    }

    fun stopBlockingVpnService(context: Context): Boolean {
        return try {
            val intent = Intent(BlockingVpnService.ACTION_STOP_VPN)
            context.sendBroadcast(intent)
            logDebug(TAG, "VPN blocking stopped successfully")
            true
        } catch (e: Exception) {
            logError(TAG, "Error stopping VPN blocking", e)
            false
        }
    }
}