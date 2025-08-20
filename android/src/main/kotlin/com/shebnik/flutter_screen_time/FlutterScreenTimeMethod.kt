package com.shebnik.flutter_screen_time

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.shebnik.flutter_screen_time.const.FlutterScreenTimePermissionStatus
import com.shebnik.flutter_screen_time.const.FlutterScreenTimePermissionType
import com.shebnik.flutter_screen_time.const.PermissionRequestCode
import io.flutter.Log

object FlutterScreenTimeMethod {

    fun permissionStatus(
        context: Context,
        type: FlutterScreenTimePermissionType = FlutterScreenTimePermissionType.APP_USAGE
    ): FlutterScreenTimePermissionStatus {
        when (type) {
            FlutterScreenTimePermissionType.APP_USAGE -> {
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
                        FlutterScreenTimePermissionStatus.APPROVED
                    }

                    AppOpsManager.MODE_IGNORED -> {
                        FlutterScreenTimePermissionStatus.DENIED
                    }

                    else -> {
                        FlutterScreenTimePermissionStatus.NOT_DETERMINED
                    }
                }
            }

            FlutterScreenTimePermissionType.DRAW_OVERLAY -> {
                val result = Settings.canDrawOverlays(context)
                return if (result) {
                    FlutterScreenTimePermissionStatus.APPROVED
                } else {
                    FlutterScreenTimePermissionStatus.DENIED
                }
            }
        }
    }

    fun requestPermission(
        activity: Activity,
        type: FlutterScreenTimePermissionType = FlutterScreenTimePermissionType.APP_USAGE,
    ): Boolean {
        return when (type) {
            FlutterScreenTimePermissionType.APP_USAGE -> {
                try {
                    val intent = Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        "package:${activity.packageName}".toUri()
                    )
                    activity.startActivityForResult(
                        intent,
                        PermissionRequestCode.REQUEST_CODE_APP_USAGE
                    )
                    true
                } catch (exception: Exception) {
                    exception.localizedMessage?.let { Log.e("requestPermission appUsage", it) }
                    false
                }
            }

            FlutterScreenTimePermissionType.DRAW_OVERLAY -> {
                try {
                    if (!Settings.canDrawOverlays(activity)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${activity.packageName}".toUri()
                        )
                        activity.startActivityForResult(
                            intent,
                            PermissionRequestCode.REQUEST_CODE_DRAW_OVERLAY
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
        }
    }

    fun handlePermissionResult(
        context: Context,
        type: FlutterScreenTimePermissionType
    ): Boolean {
        val status = permissionStatus(context, type)
        return status == FlutterScreenTimePermissionStatus.APPROVED
    }
}