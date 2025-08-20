package com.shebnik.flutter_screen_time

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.const.FlutterScreenTimePermissionType
import com.shebnik.flutter_screen_time.const.MethodName
import com.shebnik.flutter_screen_time.const.PermissionRequestCode
import com.shebnik.flutter_screen_time.util.EnumExtension.toCamelCase
import com.shebnik.flutter_screen_time.util.EnumExtension.toEnumFormat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** FlutterScreenTimePlugin */
class FlutterScreenTimePlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    private var pendingResult: Result? = null
    private var pendingPermissionType: FlutterScreenTimePermissionType? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_screen_time")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            MethodName.PERMISSION_STATUS -> {
                val args = call.arguments as Map<*, *>
                val permissionType = args[Argument.PERMISSION_TYPE] as String

                val response = FlutterScreenTimeMethod.permissionStatus(
                    context,
                    FlutterScreenTimePermissionType.valueOf(permissionType.toEnumFormat())
                )

                result.success(response.name.toCamelCase())
            }

            MethodName.REQUEST_PERMISSION -> {
                val currentActivity = activity
                if (currentActivity == null) {
                    result.success(false)
                    return
                }

                val args = call.arguments as Map<*, *>
                val permissionType = args[Argument.PERMISSION_TYPE] as String
                val enumPermissionType =
                    FlutterScreenTimePermissionType.valueOf(permissionType.toEnumFormat())

                // Store pending result and permission type
                pendingResult = result
                pendingPermissionType = enumPermissionType

                val success = FlutterScreenTimeMethod.requestPermission(
                    currentActivity,
                    enumPermissionType,
                )

                if (!success) {
                    // If we couldn't launch the intent, return false immediately
                    pendingResult = null
                    pendingPermissionType = null
                    result.success(false)
                }
            }

            MethodName.INSTALLED_APPS -> {
                val args = call.arguments as Map<*, *>
                val ignoreSystemApps = args[Argument.IGNORE_SYSTEM_APPS] as Boolean? ?: true

                CoroutineScope(Dispatchers.IO).launch {
                    val installedApps =
                        FlutterScreenTimeMethod.installedApps(context, ignoreSystemApps)
                    withContext(Dispatchers.Main) {
                        result.success(installedApps)
                    }
                }
            }

            MethodName.BLOCK_APPS -> {
                val args = call.arguments as Map<*, *>
                val bundleIds = args[Argument.BUNDLE_IDS] as List<*>?
                val layoutName = args[Argument.BLOCK_OVERLAY_LAYOUT_NAME] as String?

                val notificationTitle = args[Argument.NOTIFICATION_TITLE] as String?
                val notificationBody = args[Argument.NOTIFICATION_BODY] as String?

                val response = FlutterScreenTimeMethod.blockApps(
                    context,
                    bundleIds?.filterIsInstance<String>() ?: mutableListOf(),
                    layoutName,
                    notificationTitle,
                    notificationBody
                )

                result.success(response)
            }

            MethodName.STOP_BLOCKING_APPS -> {
                result.success(FlutterScreenTimeMethod.stopBlockingApps(context))
            }

            else -> result.notImplemented()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val permissionType = when (requestCode) {
            PermissionRequestCode.REQUEST_CODE_APP_USAGE -> FlutterScreenTimePermissionType.APP_USAGE
            PermissionRequestCode.REQUEST_CODE_DRAW_OVERLAY -> FlutterScreenTimePermissionType.DRAW_OVERLAY
            else -> return false
        }

        // Handle the permission result using FlutterScreenTimeMethod
        val result = pendingResult
        if (result != null && pendingPermissionType == permissionType) {
            val isGranted = FlutterScreenTimeMethod.handlePermissionResult(context, permissionType)
            result.success(isGranted)

            // Clear pending callbacks
            pendingResult = null
            pendingPermissionType = null
        }

        return true
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}