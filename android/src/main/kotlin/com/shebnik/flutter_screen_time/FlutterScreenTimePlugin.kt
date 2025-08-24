package com.shebnik.flutter_screen_time

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.const.PermissionType
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
import android.util.Log
import android.content.pm.PackageManager

/** FlutterScreenTimePlugin */
class FlutterScreenTimePlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    private var pendingResult: Result? = null
    private var pendingPermissionType: PermissionType? = null

    companion object {
        const val TAG = "FlutterScreenTimePlugin"
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_screen_time")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            MethodName.AUTHORIZATION_STATUS -> {
                val args = call.arguments as Map<*, *>
                val permissionType = args[Argument.PERMISSION_TYPE] as String

                val response = FlutterScreenTimeMethod.authorizationStatus(
                    context, PermissionType.valueOf(permissionType.toEnumFormat())
                )
                Log.i(
                    TAG,
                    "Got authorizationStatus for ${permissionType}: ${response.name.toCamelCase()}"
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
                val enumPermissionType = PermissionType.valueOf(permissionType.toEnumFormat())

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
                val bundleIds = args[Argument.BUNDLE_IDS] as List<*>?

                CoroutineScope(Dispatchers.IO).launch {
                    val installedApps =
                        FlutterScreenTimeMethod.installedApps(context, ignoreSystemApps, bundleIds)
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
                val notificationIcon = args[Argument.NOTIFICATION_ICON] as String?
                val notificationGroupIcon = args[Argument.NOTIFICATION_GROUP_ICON] as String?

                val response = FlutterScreenTimeMethod.blockApps(
                    context,
                    bundleIds?.filterIsInstance<String>() ?: mutableListOf(),
                    layoutName,
                    notificationTitle,
                    notificationBody,
                    notificationIcon,
                    notificationGroupIcon
                )

                result.success(response)
            }

            MethodName.DISABLE_APPS_BLOCKING -> {
                result.success(FlutterScreenTimeMethod.disableAppsBlocking(context))
            }

            MethodName.BLOCK_WEB_DOMAINS -> {
                val args = call.arguments as Map<*, *>
                val domains = args[Argument.BLOCKED_WEB_DOMAINS] as List<*>?
                val notificationTitle = args[Argument.NOTIFICATION_TITLE] as String?
                val notificationBody = args[Argument.NOTIFICATION_BODY] as String?
                val notificationIcon = args[Argument.NOTIFICATION_ICON] as String?
                val notificationGroupIcon = args[Argument.NOTIFICATION_GROUP_ICON] as String?
                val blockWebsitesOnlyInBrowsers =
                    args[Argument.BLOCK_WEBSITES_ONLY_IN_BROWSERS] as Boolean?

                val response = FlutterScreenTimeMethod.blockDomains(
                    context,
                    domains?.filterIsInstance<String>() ?: mutableListOf(),
                    notificationTitle,
                    notificationBody,
                    notificationIcon,
                    notificationGroupIcon,
                    blockWebsitesOnlyInBrowsers
                )

                result.success(response)
            }

            MethodName.DISABLE_WEB_DOMAINS_BLOCKING -> {
                result.success(FlutterScreenTimeMethod.stopBlockingDomains(context))
            }

            MethodName.DISABLE_ALL_BLOCKING -> {
                val disableAppsBlockingResult = FlutterScreenTimeMethod.disableAppsBlocking(context)
                val stopBlockingDomainsResult = FlutterScreenTimeMethod.stopBlockingDomains(context)
                result.success(disableAppsBlockingResult && stopBlockingDomainsResult)
            }

            else -> result.notImplemented()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val permissionType = when (requestCode) {
            PermissionRequestCode.REQUEST_CODE_APP_USAGE -> PermissionType.APP_USAGE
            PermissionRequestCode.REQUEST_CODE_DRAW_OVERLAY -> PermissionType.DRAW_OVERLAY
            PermissionRequestCode.REQUEST_CODE_ACCESSIBILITY_SETTINGS -> PermissionType.ACCESSIBILITY_SETTINGS
            else -> return false
        }

        // Handle the permission result using FlutterScreenTimeMethod
        val result = pendingResult
        if (result != null && pendingPermissionType == permissionType) {
            val isGranted = FlutterScreenTimeMethod.handlePermissionResult(context, permissionType)
            result.success(isGranted)
            Log.d(
                "FlutterScreenTimePlugin", "Permission result for $permissionType: $isGranted"
            )

            // Clear pending callbacks
            pendingResult = null
            pendingPermissionType = null
        }

        return true
    }

    // New method to handle runtime permission results
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ): Boolean {
        if (requestCode == PermissionRequestCode.REQUEST_CODE_NOTIFICATION) {
            val result = pendingResult
            if (result != null && pendingPermissionType == PermissionType.NOTIFICATION) {
                val isGranted =
                    grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

                result.success(isGranted)
                Log.d(TAG, "Notification permission result: $isGranted")

                // Clear pending callbacks
                pendingResult = null
                pendingPermissionType = null
            }
            return true
        }
        return false
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}