package com.shebnik.flutter_screen_time

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
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
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.shebnik.flutter_screen_time.*
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.const.Field
import com.shebnik.flutter_screen_time.const.PermissionRequestCode
import com.shebnik.flutter_screen_time.const.PermissionStatus
import com.shebnik.flutter_screen_time.const.PermissionType
import com.shebnik.flutter_screen_time.service.BlockAppsService
import com.shebnik.flutter_screen_time.service.DomainBlockingAccessibilityService
import com.shebnik.flutter_screen_time.util.ApplicationInfoUtil
import java.io.ByteArrayOutputStream


object FlutterScreenTimeMethod {

    fun permissionStatus(
        context: Context, type: PermissionType = PermissionType.APP_USAGE
    ): PermissionStatus {
        when (type) {
            PermissionType.APP_USAGE -> {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                } else {
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                }

                return when (mode) {
                    AppOpsManager.MODE_ALLOWED -> {
                        PermissionStatus.APPROVED
                    }

                    AppOpsManager.MODE_IGNORED -> {
                        PermissionStatus.DENIED
                    }

                    else -> {
                        PermissionStatus.NOT_DETERMINED
                    }
                }
            }

            PermissionType.DRAW_OVERLAY -> {
                val result = Settings.canDrawOverlays(context)
                return if (result) {
                    PermissionStatus.APPROVED
                } else {
                    PermissionStatus.DENIED
                }
            }


            PermissionType.NOTIFICATION -> {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    )
                    when (permission) {
                        PackageManager.PERMISSION_GRANTED -> {
                            PermissionStatus.APPROVED
                        }

                        PackageManager.PERMISSION_DENIED -> {
                            PermissionStatus.DENIED
                        }

                        else -> {
                            PermissionStatus.NOT_DETERMINED
                        }
                    }
                } else {
                    PermissionStatus.APPROVED
                }
            }

            PermissionType.ACCESSIBILITY_SETTINGS -> {
                // Check if the accessibility service is enabled
                val am =
                    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val enabledServices =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)

                for (enabledService in enabledServices) {
                    val enabledServiceInfo: ServiceInfo =
                        enabledService.resolveInfo.serviceInfo
                    if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(
                            DomainBlockingAccessibilityService::class.java.name
                        )
                    ) return PermissionStatus.APPROVED
                }
                return PermissionStatus.DENIED
            }
        }
    }

    fun requestPermission(
        activity: Activity,
        type: PermissionType = PermissionType.APP_USAGE,
    ): Boolean {
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
                    exception.localizedMessage?.let { Log.e("requestPermission appUsage", it) }
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
                        Log.e("requestPermission manageOverlayPermission", it)
                    }
                    false
                }
            }

            PermissionType.NOTIFICATION -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val intent = Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS, packageUri
                        )
                        activity.startActivityForResult(
                            intent, PermissionRequestCode.REQUEST_CODE_NOTIFICATION
                        )
                        true
                    } else {
                        false // Notification permission not needed for Android < 13
                    }
                } catch (exception: Exception) {
                    exception.localizedMessage?.let { Log.e("requestPermission NOTIFICATION", it) }
                    false
                }
            }

            PermissionType.ACCESSIBILITY_SETTINGS -> {
                try {
                    val intent = Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                    )
                    activity.startActivityForResult(
                        intent, PermissionRequestCode.REQUEST_CODE_ACCESSIBILITY_SETTINGS
                    )
                    true
                } catch (e: Exception) {
                    e.localizedMessage?.let {
                        Log.e(
                            "requestPermission ACCESSIBILITY_SETTINGS", it
                        )
                    }
                    false
                }
            }
        }
    }

    fun handlePermissionResult(
        context: Context, type: PermissionType
    ): Boolean {
        val status = permissionStatus(context, type)
        return status == PermissionStatus.APPROVED
    }

    fun installedApps(context: Context, ignoreSystemApps: Boolean = true): Map<String, Any> {
        try {
            val packageManager = context.packageManager
            val apps = ArrayList<ApplicationInfo>()

            val installedApplications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(
                        PackageManager.GET_META_DATA.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION") packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            }

            if (ignoreSystemApps) {
                val filtered =
                    installedApplications.filter { app -> (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && app.packageName != context.packageName }
                apps.addAll(filtered)
            } else {
                apps.addAll(installedApplications)
            }

            val appMap = ArrayList<MutableMap<String, Any?>>()

            for (app in apps) {
                val appCategory = ApplicationInfoUtil.category(app.category)
                val packageInfo = packageManager.getPackageInfo(app.packageName, 0)
                val appIcon = appIconAsBase64(
                    packageManager, app.packageName
                )

                val data = mutableMapOf(
                    Field.appName to app.loadLabel(packageManager),
                    Field.packageName to app.packageName,
                    Field.enabled to app.enabled,
                    Field.category to appCategory,
                    Field.versionName to packageInfo.versionName,
                    Field.versionCode to packageInfo.versionCode,
                )

                if (appIcon != null) {
                    data[Field.appIcon] = appIcon
                }

                appMap.add(data)
            }

            return mutableMapOf(
                Field.status to true,
                Field.data to appMap,
            )
        } catch (exception: Exception) {
            exception.localizedMessage?.let { Log.e("installedApps", it) }

            return mutableMapOf(
                Field.status to false,
                Field.data to ArrayList<MutableMap<String, Any?>>(),
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
        notificationBody: String? = null
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
            }

            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (e is ForegroundServiceStartNotAllowedException) {
                        Log.e("FlutterScreenTimeMethod", "Foreground service start not allowed", e)
                    } else {
                        Log.e("FlutterScreenTimeMethod", "Foreground service start not allowed", e)
                    }
                } else {
                    Log.e("FlutterScreenTimeMethod", "Foreground service start not allowed", e)
                }

                return false
            }

            return true
        } catch (e: Exception) {
            Log.e("FlutterScreenTimeMethod", "Error starting block", e)
            return false
        }
    }

    fun stopBlockingApps(context: Context): Boolean {
        return try {
            val intent = Intent(context, BlockAppsService::class.java)
            context.stopService(intent)
            Log.d("FlutterScreenTimeMethod", "BlockAppService stopped successfully")
            true
        } catch (e: Exception) {
            Log.e("FlutterScreenTimeMethod", "Error stopping block service", e)
            false
        }
    }

    fun blockDomains(
        context: Context,
        domains: List<String>,
        layoutName: String? = null,
        notificationTitle: String? = null,
        notificationBody: String? = null
    ): Boolean {
        if (domains.isEmpty()) return false

        try {
            val intent = Intent(context, DomainBlockingAccessibilityService::class.java).apply {
                action = DomainBlockingAccessibilityService.ACTION_START_BLOCKING
                putStringArrayListExtra(Argument.BUNDLE_IDS, ArrayList(domains))

                val callerPackageName = context.packageName
                putExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, callerPackageName)
                putExtra(
                    Argument.BLOCK_OVERLAY_LAYOUT_NAME,
                    layoutName ?: DomainBlockingAccessibilityService.DEFAULT_LAYOUT_NAME
                )

                putExtra(Argument.NOTIFICATION_TITLE, notificationTitle)
                putExtra(Argument.NOTIFICATION_BODY, notificationBody)
            }

            context.startService(intent)
            Log.d("FlutterScreenTimeMethod", "Domain blocking started successfully")
            return true

        } catch (e: Exception) {
            Log.e("FlutterScreenTimeMethod", "Error starting domain blocking", e)
            return false
        }
    }

    fun stopBlockingDomains(context: Context): Boolean {
        return try {
            val intent = Intent(context, DomainBlockingAccessibilityService::class.java).apply {
                action = DomainBlockingAccessibilityService.ACTION_STOP_BLOCKING
            }
            context.startService(intent)
            Log.d("FlutterScreenTimeMethod", "Domain blocking stopped successfully")
            true
        } catch (e: Exception) {
            Log.e("FlutterScreenTimeMethod", "Error stopping domain blocking", e)
            false
        }
    }

    fun updateBlockedDomains(context: Context, domains: List<String>): Boolean {
        return try {
            val intent = Intent(context, DomainBlockingAccessibilityService::class.java).apply {
                action = DomainBlockingAccessibilityService.ACTION_UPDATE_WEB_DOMAINS
                putStringArrayListExtra(Argument.BUNDLE_IDS, ArrayList(domains))
            }
            context.startService(intent)
            Log.d("FlutterScreenTimeMethod", "Blocked domains updated successfully")
            true
        } catch (e: Exception) {
            Log.e("FlutterScreenTimeMethod", "Error updating blocked domains", e)
            false
        }
    }
}