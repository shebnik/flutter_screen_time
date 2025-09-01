package com.shebnik.flutter_screen_time.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.receiver.StopBlockingReceiver
import com.shebnik.flutter_screen_time.util.NotificationUtil
import com.shebnik.flutter_screen_time.util.NotificationUtil.startForegroundWithGroupedNotification
import com.shebnik.flutter_screen_time.util.NotificationUtil.stopForegroundWithCleanup

class BlockingService : AccessibilityService() {

    // App blocking properties
    private var blockedApps: List<String> = emptyList()

    // Website blocking properties
    private var blockedDomains: List<String> = emptyList()
    private var currentUrl: String? = null
    private var lastCheckedUrl: String? = null

    // Common properties
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var customIconResId: Int? = null
    private var isServiceActive = false
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var blockUninstalling: Boolean = false
    private var appName: String? = null

    // Overlay properties
    private var layoutName: String = DEFAULT_LAYOUT_NAME
    private var useOverlayCountdown: Boolean = false
    private var overlayCountdownSeconds: Int = 5
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var countdownRunnable: Runnable? = null
    private var backPressRunnable: Runnable? = null
    private var currentCountdown = 0
    private var backPressCount = 0

    // DNS blocking
    private var useDNSWebsiteBlocking: Boolean = false

    private lateinit var stopBlockingReceiver: StopBlockingReceiver

    companion object {
        const val TAG = "BlockingService"
        const val PREFS_NAME = "blocking_service_config"
        const val MONITORING_INTERVAL = 1000L
        const val ACTION_STOP_BLOCKING = "com.shebnik.flutter_screen_time.STOP_BLOCKING"
        const val ACTION_STOP_BLOCKING_APPS = "com.shebnik.flutter_screen_time.STOP_BLOCKING_APPS"
        const val ACTION_STOP_BLOCKING_WEBSITES =
            "com.shebnik.flutter_screen_time.STOP_BLOCKING_WEBSITES"
        const val BACK_PRESS_COUNT = 10
        const val BACK_PRESS_INTERVAL = 100L

        const val DEFAULT_LAYOUT_NAME = "block_overlay"
        const val DEFAULT_COUNT_LAYOUT_NAME = "block_overlay_count"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Unified blocking accessibility service connected")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        NotificationUtil.createNotificationChannel(this)

        stopBlockingReceiver = StopBlockingReceiver(this)

        val filter = IntentFilter(ACTION_STOP_BLOCKING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopBlockingReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag") registerReceiver(
                stopBlockingReceiver,
                filter
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Unified blocking service started")

        val prefs = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Load configuration - prioritize prefs over intent if null
        loadConfiguration(intent, prefs)

        isServiceActive = true

        val notification = NotificationUtil.createBlockingNotification(
            context = this,
            title = notificationTitle,
            body = notificationBody,
            customIconResId = customIconResId,
            blockedAppsCount = blockedApps.size,
            blockedDomainsCount = blockedDomains.size,
        )
        startForegroundWithGroupedNotification(
            NotificationUtil.BLOCKING_NOTIFICATION_ID,
            notification
        )

        startAppMonitoring()
        Log.d(
            TAG,
            "Unified blocking started - Apps: ${blockedApps.size}, Domains: ${blockedDomains.size}"
        )
        return START_STICKY
    }

    private fun loadConfiguration(intent: Intent?, prefs: android.content.SharedPreferences) {
        val editor = prefs.edit()

        // Load blocked apps - prefer intent, fallback to prefs
        val intentBlockedApps = intent?.getStringArrayListExtra(Argument.BUNDLE_IDS)
        blockedApps = intentBlockedApps ?: prefs.getStringSet(
            Argument.BUNDLE_IDS, null
        )?.toList() ?: emptyList()
        intentBlockedApps?.let { editor.putStringSet(Argument.BUNDLE_IDS, it.toSet()) }

        // Load blocked domains - prefer intent, fallback to prefs
        val intentBlockedDomains = intent?.getStringArrayListExtra(Argument.BLOCKED_WEB_DOMAINS)
        blockedDomains = intentBlockedDomains ?: prefs.getStringSet(
            Argument.BLOCKED_WEB_DOMAINS, null
        )?.toList() ?: emptyList()
        intentBlockedDomains?.let { editor.putStringSet(Argument.BLOCKED_WEB_DOMAINS, it.toSet()) }

        // Load caller package name - prefer intent, fallback to prefs
        val intentCallerPackage = intent?.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE)
        callerPackageName = intentCallerPackage ?: prefs.getString(
            Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, null
        ) ?: packageName
        intentCallerPackage?.let { editor.putString(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, it) }

        // Load notification title - prefer intent, fallback to prefs, then default
        val intentNotificationTitle = intent?.getStringExtra(Argument.NOTIFICATION_TITLE)
        notificationTitle = intentNotificationTitle ?: prefs.getString(
            Argument.NOTIFICATION_TITLE, null
        ) ?: getDefaultNotificationTitle()
        intentNotificationTitle?.let { editor.putString(Argument.NOTIFICATION_TITLE, it) }

        // Load notification body - prefer intent, fallback to prefs, then default
        notificationBody = intent?.getStringExtra(Argument.NOTIFICATION_BODY) ?: prefs.getString(
            Argument.NOTIFICATION_BODY, null
        ) ?: getDefaultNotificationBody()
        intent?.getStringExtra(Argument.NOTIFICATION_BODY)?.let { intentValue ->
            editor.putString(Argument.NOTIFICATION_BODY, intentValue)
        }

        // Load custom icon - prefer intent, fallback to prefs
        val customIconName = intent?.getStringExtra(Argument.NOTIFICATION_ICON) ?: prefs.getString(
            Argument.NOTIFICATION_ICON, null
        )
        intent?.getStringExtra(Argument.NOTIFICATION_ICON)?.let { intentValue ->
            editor.putString(Argument.NOTIFICATION_ICON, intentValue)
        }
        customIconResId = if (customIconName != null) {
            NotificationUtil.getIconResource(this, customIconName, callerPackageName)
        } else null

        // Load overlay countdown settings - prefer intent, fallback to prefs
        useOverlayCountdown = when {
            intent?.hasExtra(Argument.USE_OVERLAY_COUNTDOWN) == true -> intent.getBooleanExtra(
                Argument.USE_OVERLAY_COUNTDOWN,
                true
            )

            prefs.contains(Argument.USE_OVERLAY_COUNTDOWN) -> prefs.getBoolean(
                Argument.USE_OVERLAY_COUNTDOWN,
                true
            )

            else -> true
        }

        if (intent?.hasExtra(Argument.USE_OVERLAY_COUNTDOWN) == true) {
            editor.putBoolean(Argument.USE_OVERLAY_COUNTDOWN, useOverlayCountdown)
        }

        overlayCountdownSeconds = when {
            intent?.hasExtra(Argument.OVERLAY_COUNTDOWN_SECONDS) == true -> intent.getIntExtra(
                Argument.OVERLAY_COUNTDOWN_SECONDS,
                10
            )

            prefs.contains(Argument.OVERLAY_COUNTDOWN_SECONDS) -> prefs.getInt(
                Argument.OVERLAY_COUNTDOWN_SECONDS,
                10
            )

            else -> 10
        }

        if (intent?.hasExtra(Argument.OVERLAY_COUNTDOWN_SECONDS) == true) {
            editor.putInt(Argument.OVERLAY_COUNTDOWN_SECONDS, overlayCountdownSeconds)
        }

        // Load layout name - prefer intent, fallback to prefs, then default
        layoutName = intent?.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME) ?: prefs.getString(
            Argument.BLOCK_OVERLAY_LAYOUT_NAME, null
        ) ?: if (useOverlayCountdown) DEFAULT_COUNT_LAYOUT_NAME else DEFAULT_LAYOUT_NAME
        intent?.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME)?.let { intentValue ->
            editor.putString(Argument.BLOCK_OVERLAY_LAYOUT_NAME, intentValue)
        }

        blockUninstalling = intent?.getBooleanExtra(Argument.BLOCK_UNINSTALLING, false)
            ?: prefs.getBoolean(Argument.BLOCK_UNINSTALLING, false)
        editor.putBoolean(Argument.BLOCK_UNINSTALLING, blockUninstalling)

        appName =
            intent?.getStringExtra(Argument.APP_NAME) ?: prefs.getString(Argument.APP_NAME, null)
        intent?.getStringExtra(Argument.APP_NAME)?.let { intentValue ->
            editor.putString(Argument.APP_NAME, intentValue)
        }

        useDNSWebsiteBlocking = intent?.getBooleanExtra(Argument.USE_DNS_WEBSITE_BLOCKING, false)
            ?: prefs.getBoolean(Argument.USE_DNS_WEBSITE_BLOCKING, false)
        editor.putBoolean(Argument.USE_DNS_WEBSITE_BLOCKING, useDNSWebsiteBlocking)

        if (useDNSWebsiteBlocking) {
            val primaryDNS = intent?.getStringExtra(Argument.PRIMARY_DNS)
                ?: prefs.getString(Argument.PRIMARY_DNS, null)
            editor.putString(Argument.PRIMARY_DNS, primaryDNS)
            val secondaryDNS = intent?.getStringExtra(Argument.SECONDARY_DNS) ?: prefs.getString(
                Argument.SECONDARY_DNS,
                null
            )
            editor.putString(Argument.SECONDARY_DNS, secondaryDNS)
        }

        editor.apply()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Unified blocking service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!isServiceActive) return
        if (blockUninstalling && appName != null) {
            val eventPackageName = event.packageName?.toString() ?: return
            if (eventPackageName.contains("packageinstaller") || eventPackageName.contains("permissioncontroller")) {
                val textNodes = rootInActiveWindow.findAccessibilityNodeInfosByText(appName!!)
                if (textNodes.isNotEmpty()) {
                    showOverlay()
                    if (useOverlayCountdown) {
                        startBackButtonSequence()
                    } else {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }
        }

        if (blockedDomains.isEmpty() || useDNSWebsiteBlocking) return
        if (event.packageName?.toString() == callerPackageName) {
            Log.d(TAG, "Skipping website blocking for caller package: ${event.packageName}")
            return
        }

        val blockResult = checkContentChangeBlocking(event)
        val shouldBlock = blockResult.first
        val blockedDomain = blockResult.second

        if (shouldBlock) {
            Log.d(TAG, "Blocked access to $blockedDomain")
            showOverlay()
            handler.postDelayed({
                if (useOverlayCountdown) {
                    startBackButtonSequence()
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, 500)
        }
    }

    private fun checkContentChangeBlocking(event: AccessibilityEvent): Pair<Boolean, String> {
        event.source?.let { source ->
            // Rule 1: Block if any child node class is android.widget.EditText and text contains domain with suffix
            if (event.className == "android.widget.EditText") {
                val text = source.text?.toString() ?: ""
                for (domain in blockedDomains) {
                    if (text.contains(domain, ignoreCase = true)) {
                        Log.d(TAG, "BLOCKING: EditText contains blocked domain: $domain")
                        return Pair(true, domain)
                    }
                }
            }

            // Check child nodes for EditText
            val childEditTextResult = checkChildNodesForEditText(source)
            if (childEditTextResult.first) {
                return childEditTextResult
            }

            // Rule 2: Block if some child node class is android.webkit.WebView and text contains domain name without suffix
            if (event.className == "android.webkit.WebView") {
                val text = source.getChild(0)?.toString()?.lowercase() ?: ""
                for (domain in blockedDomains) {
                    val domainWithoutSuffix = domain.substringBefore(".")
                    if (text.contains(domainWithoutSuffix, ignoreCase = true)) {
                        Log.d(
                            TAG,
                            "BLOCKING: WebView contains blocked domain name: $domainWithoutSuffix"
                        )
                        return Pair(true, domain)
                    }
                }
            }
        }

        return Pair(false, "")
    }

    private fun checkChildNodesForEditText(node: AccessibilityNodeInfo): Pair<Boolean, String> {
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (child.className == "android.widget.EditText") {
                    val text = child.text?.toString() ?: ""
                    for (domain in blockedDomains) {
                        if (text.contains(domain, ignoreCase = true)) {
                            Log.d(TAG, "BLOCKING: Child EditText contains blocked domain: $domain")
                            return Pair(true, domain)
                        }
                    }
                }
                val childResult = checkChildNodesForEditText(child)
                if (childResult.first) {
                    return childResult
                }
            }
        }
        return Pair(false, "")
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
                // Skip blocking the caller package (screen time/parental control app)
                if (packageName == callerPackageName) {
                    Log.d(TAG, "Skipping caller package: $packageName")
                    hideOverlay()
                    return
                }

                // Check for blocked apps first
                if (blockedApps.contains(packageName)) {
                    Log.d(TAG, "Blocked app detected: $packageName")
                    showOverlay()
                    if (useOverlayCountdown) {
                        startBackButtonSequence()
                    } else {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    return
                }

                // For website blocking, we only check on navigation events
                // Don't hide overlay if countdown is active
                if (!useOverlayCountdown || !isOverlayShowing || countdownRunnable == null) {
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
            val layoutInflater = LayoutInflater.from(this)
            val layoutResId = getResource(layoutName, "layout")

            overlayView = if (layoutResId != 0) {
                layoutInflater.inflate(layoutResId, null)
            } else {
                layoutInflater.inflate(getResource(DEFAULT_LAYOUT_NAME, "layout"), null)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            if (useOverlayCountdown) {
                setupCountdownOverlay()
            }

            windowManager?.addView(overlayView, params)
            isOverlayShowing = true

            if (useOverlayCountdown) {
                startCountdown()
            }

            Log.d(TAG, "Overlay shown")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    private fun setupCountdownOverlay() {
        val countdownText = getResource("countdown_text", "id").takeIf { it != 0 }
            ?.let { overlayView?.findViewById<TextView>(it) }

        currentCountdown = overlayCountdownSeconds
        countdownText?.text = getResource("closing_in_seconds", type = "string").takeIf { it != 0 }
            ?.let { getString(it, currentCountdown) } ?: "Closing in ${currentCountdown}s"

        val launchButton = getResource("launch_app_button", "id").takeIf { it != 0 }
            ?.let { overlayView?.findViewById<Button>(it) }

        launchButton?.setOnClickListener {
            stopBackButtonSequence()
            launchCallerApp()
        }
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                overlayView = null
                isOverlayShowing = false

                countdownRunnable?.let { handler.removeCallbacks(it) }
                countdownRunnable = null

                Log.d(TAG, "Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay", e)
            }
        }
    }

    private fun startCountdown() {
        countdownRunnable = object : Runnable {
            override fun run() {
                if (currentCountdown > 0) {
                    val countdownText = getResource("countdown_text", "id").takeIf { it != 0 }
                        ?.let { overlayView?.findViewById<TextView>(it) }

                    countdownText?.text =
                        getResource("closing_in_seconds", type = "string").takeIf { it != 0 }
                            ?.let { getString(it, currentCountdown) }
                            ?: "Closing in ${currentCountdown}s"

                    currentCountdown--
                    handler.postDelayed(this, 1000)
                } else {
                    hideOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Log.d(TAG, "Countdown finished, navigated to home")
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun startBackButtonSequence() {
        backPressCount = 0

        backPressRunnable = object : Runnable {
            override fun run() {
                if (backPressCount < BACK_PRESS_COUNT) {
                    val success = performGlobalAction(GLOBAL_ACTION_BACK)
                    if (success) {
                        backPressCount++
                        Log.d(TAG, "Back press $backPressCount/$BACK_PRESS_COUNT performed")
                        handler.postDelayed(this, BACK_PRESS_INTERVAL)
                    } else {
                        Log.w(TAG, "Back press $backPressCount failed, retrying...")
                        handler.postDelayed(this, BACK_PRESS_INTERVAL)
                    }
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Log.d(TAG, "All back presses completed, navigated to home")
                }
            }
        }

        handler.post(backPressRunnable!!)
    }

    private fun stopBackButtonSequence() {
        backPressRunnable?.let { handler.removeCallbacks(it) }
        backPressRunnable = null
        backPressCount = 0
    }

    private fun launchCallerApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(callerPackageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                hideOverlay()
                Log.d(TAG, "Launched caller app: $callerPackageName")
            } else {
                Log.e(TAG, "Cannot launch caller app: $callerPackageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching caller app", e)
        }
    }

    fun stopBlocking() {
        isServiceActive = false
        stopAppMonitoring()
        hideOverlay()

        backPressRunnable?.let { handler.removeCallbacks(it) }
        backPressRunnable = null

        stopForegroundWithCleanup()
        currentUrl = null
        lastCheckedUrl = null
        Log.d(TAG, "Unified blocking deactivated")
    }

    fun stopBlockingApps() {
        blockedApps = emptyList()
        if (blockedDomains.isEmpty()) {
            stopBlocking()
        }
        Log.d(TAG, "App blocking deactivated")
    }

    fun stopBlockingWebsites() {
        blockedDomains = emptyList()
        if (blockedApps.isEmpty()) {
            stopBlocking()
        }
        Log.d(TAG, "Website blocking deactivated")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopBlockingReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        hideOverlay()
        Log.d(TAG, "Unified blocking service destroyed")
        super.onDestroy()
    }

    private fun getDefaultNotificationTitle(): String {
        return when {
            blockedApps.isNotEmpty() && blockedDomains.isNotEmpty() -> "App & Website Blocking Active"
            blockedApps.isNotEmpty() -> "App Blocking Active"
            blockedDomains.isNotEmpty() -> "Website Blocking Active"
            else -> "Blocking Service Active"
        }
    }

    private fun getDefaultNotificationBody(): String {
        return when {
            blockedApps.isNotEmpty() && blockedDomains.isNotEmpty() -> "Monitoring ${blockedApps.size} apps and ${blockedDomains.size} websites"

            blockedApps.isNotEmpty() -> "Monitoring ${blockedApps.size} apps"
            blockedDomains.isNotEmpty() -> "Monitoring ${blockedDomains.size} websites"
            else -> "Blocker is not running"
        }
    }

    private fun getResource(resourceName: String, type: String): Int {
        return try {
            val resources = if (callerPackageName != packageName) {
                packageManager.getResourcesForApplication(callerPackageName)
            } else {
                resources
            }

            @SuppressLint("DiscouragedApi") resources.getIdentifier(
                resourceName,
                type,
                callerPackageName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout resource", e)
            0
        }
    }
}