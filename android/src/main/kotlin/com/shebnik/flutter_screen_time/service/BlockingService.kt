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
import android.os.HandlerThread
import android.os.Looper
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
import com.shebnik.flutter_screen_time.util.logDebug
import com.shebnik.flutter_screen_time.util.logError
import com.shebnik.flutter_screen_time.util.logWarning

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
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var monitoringRunnable: Runnable? = null
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

    // Uninstall prevention
    private var uninstallPreventionKeywords: Set<String>? = null

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
        logDebug(TAG, "Unified blocking accessibility service connected")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        NotificationUtil.createNotificationChannel(this)

        // Initialize background thread for heavy operations
        backgroundThread = HandlerThread("BlockingServiceBackground").apply {
            start()
        }
        backgroundHandler = Handler(backgroundThread!!.looper)

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
        logDebug(TAG, "Unified blocking service started")

        val prefs = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Load configuration from SharedPreferences
        loadConfiguration(prefs)

        isServiceActive = true

        val notification = NotificationUtil.createBlockingNotification(
            context = this,
            title = notificationTitle,
            body = notificationBody,
            customIconResId = customIconResId,
            blockedAppsCount = blockedApps.size,
            blockedDomainsCount = blockedDomains.size,
            appName = appName
        )
        startForegroundWithGroupedNotification(
            NotificationUtil.BLOCKING_NOTIFICATION_ID,
            notification
        )

        startAppMonitoring()
        logDebug(
            TAG,
            "Unified blocking started - Apps: ${blockedApps.size}, Domains: ${blockedDomains.size}, Uninstall Prevention: $uninstallPreventionKeywords"
        )
        return START_STICKY
    }

    private fun loadConfiguration(prefs: android.content.SharedPreferences) {
        // Load all configuration from SharedPreferences
        blockedApps = prefs.getStringSet(Argument.BUNDLE_IDS, null)?.toList() ?: emptyList()

        val useDnsWebsiteBlocking =
            prefs.getBoolean(Argument.USE_DNS_WEBSITE_BLOCKING, true)

        if (!useDnsWebsiteBlocking) {
            blockedDomains = prefs.getStringSet(Argument.BLOCKED_WEB_DOMAINS, null)?.toList() ?: emptyList()
        }

        uninstallPreventionKeywords = prefs.getStringSet(Argument.UNINSTALL_PREVENTION_KEYWORDS, null)

        callerPackageName = prefs.getString(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, null) ?: packageName

        notificationTitle = prefs.getString(Argument.NOTIFICATION_TITLE, null) ?: getDefaultNotificationTitle()

        notificationBody = prefs.getString(Argument.NOTIFICATION_BODY, null) ?: getDefaultNotificationBody()

        val customIconName = prefs.getString(Argument.NOTIFICATION_ICON, null)
        customIconResId = if (customIconName != null) {
            NotificationUtil.getIconResource(this, customIconName, callerPackageName)
        } else null

        useOverlayCountdown = prefs.getBoolean(Argument.USE_OVERLAY_COUNTDOWN, true)

        overlayCountdownSeconds = prefs.getInt(Argument.OVERLAY_COUNTDOWN_SECONDS, 10)

        layoutName = prefs.getString(Argument.BLOCK_OVERLAY_LAYOUT_NAME, null)
            ?: if (useOverlayCountdown) DEFAULT_COUNT_LAYOUT_NAME else DEFAULT_LAYOUT_NAME

        appName = prefs.getString(Argument.APP_NAME, null)
    }

    override fun onInterrupt() {
        logDebug(TAG, "Unified blocking service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!isServiceActive) return

        if (event.packageName?.toString() == callerPackageName) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && !uninstallPreventionKeywords.isNullOrEmpty()) {
            logDebug(TAG, "Clicked view class: ${event.className}, text: ${event.text}")
            for (keyword in uninstallPreventionKeywords!!) {
                if (event.text.any { it.toString().contains(keyword, ignoreCase = true) }) {
                    logDebug(TAG, "Uninstall prevention triggered by keyword: $keyword")
                    showOverlay()
                    if (useOverlayCountdown) {
                        startBackButtonSequence()
                    } else {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    return
                }
            }
        }

        if (blockedDomains.isEmpty()) return

        val blockResult = checkContentChangeBlocking(event)
        val shouldBlock = blockResult.first
        val blockedDomain = blockResult.second

        if (shouldBlock) {
            logDebug(TAG, "Blocked access to $blockedDomain")
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
                        logDebug(TAG, "BLOCKING: EditText contains blocked domain: $domain")
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
                        logDebug(
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
                            logDebug(
                                TAG,
                                "BLOCKING: Child EditText contains blocked domain: $domain"
                            )
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
                    // Move the heavy UsageStatsManager operation to background thread
                    backgroundHandler?.post {
                        checkForegroundApp()
                    }
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

            // Post UI operations back to main thread
            handler.post {
                handleForegroundAppResult(foregroundApp)
            }
        } catch (e: Exception) {
            logError(TAG, "Error checking foreground app", e)
        }
    }

    private fun handleForegroundAppResult(foregroundApp: String?) {
        foregroundApp?.let { packageName ->
            // Skip blocking the caller package (screen time/parental control app)
            if (packageName == callerPackageName) {
                logDebug(TAG, "Skipping caller package: $packageName")
                hideOverlay()
                return
            }

            // Check for blocked apps first
            if (blockedApps.contains(packageName)) {
                logDebug(TAG, "Blocked app detected: $packageName")
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

            logDebug(TAG, "Overlay shown")

        } catch (e: Exception) {
            logError(TAG, "Error showing overlay", e)
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

                logDebug(TAG, "Overlay hidden")
            } catch (e: Exception) {
                logError(TAG, "Error hiding overlay", e)
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
                    logDebug(TAG, "Countdown finished, navigated to home")
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
                        logDebug(TAG, "Back press $backPressCount/$BACK_PRESS_COUNT performed")
                        handler.postDelayed(this, BACK_PRESS_INTERVAL)
                    } else {
                        logWarning(TAG, "Back press $backPressCount failed, retrying...")
                        handler.postDelayed(this, BACK_PRESS_INTERVAL)
                    }
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    logDebug(TAG, "All back presses completed, navigated to home")
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
                logDebug(TAG, "Launched caller app: $callerPackageName")
            } else {
                logError(TAG, "Cannot launch caller app: $callerPackageName")
            }
        } catch (e: Exception) {
            logError(TAG, "Error launching caller app", e)
        }
    }

    fun stopBlocking() {
        isServiceActive = false
        stopAppMonitoring()
        hideOverlay()

        // Note: VPN service is now managed separately from FlutterScreenTimeMethod
        // No need to stop VPN from AccessibilityService

        backPressRunnable?.let { handler.removeCallbacks(it) }
        backPressRunnable = null

        stopForegroundWithCleanup()
        currentUrl = null
        lastCheckedUrl = null
        logDebug(TAG, "Unified blocking deactivated")
    }

    fun stopBlockingApps() {
        blockedApps = emptyList()
        if (blockedDomains.isEmpty() && uninstallPreventionKeywords.isNullOrEmpty()) {
            stopBlocking()
        }
        logDebug(TAG, "App blocking deactivated")
    }

    fun stopBlockingWebsites() {
        blockedDomains = emptyList()
        if (blockedApps.isEmpty() && uninstallPreventionKeywords.isNullOrEmpty()) {
            stopBlocking()
        }
        logDebug(TAG, "Website blocking deactivated")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopBlockingReceiver)
        } catch (e: Exception) {
            logError(TAG, "Error unregistering receiver", e)
        }

        // Clean up background thread
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null

        hideOverlay()
        logDebug(TAG, "Unified blocking service destroyed")
        super.onDestroy()
    }

    private fun getDefaultNotificationTitle(): String {
        return when {
            blockedApps.isNotEmpty() && blockedDomains.isNotEmpty() -> "App & Website Blocking Active"
            blockedApps.isNotEmpty() -> "App Blocking Active"
            blockedDomains.isNotEmpty() -> "Website Blocking Active"
            !uninstallPreventionKeywords.isNullOrEmpty() -> "Uninstall Prevention Active"
            else -> "Interrupted by OS."
        }
    }

    private fun getDefaultNotificationBody(): String {
        return when {
            blockedApps.isNotEmpty() && blockedDomains.isNotEmpty() -> "Monitoring ${blockedApps.size} apps and ${blockedDomains.size} websites"
            blockedApps.isNotEmpty() -> "[${blockedApps.size} ${if (blockedApps.size > 1) "apps" else "app"}] Swipe left to hide notification."
            blockedDomains.isNotEmpty() -> "[${blockedDomains.size} ${if (blockedDomains.size > 1) "websites" else "website"}] Swipe left to hide notification."
            !uninstallPreventionKeywords.isNullOrEmpty() -> "Swipe left to hide notification."
            else -> "Check permissions in ${appName ?: "the"} app, or contact support."
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
            logError(TAG, "Error getting layout resource", e)
            0
        }
    }
}