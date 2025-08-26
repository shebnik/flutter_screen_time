package com.shebnik.flutter_screen_time.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
import com.shebnik.flutter_screen_time.util.NotificationUtil.stopForegroundWithCleanup
import java.net.URL

class BlockingService : AccessibilityService() {

    // App blocking properties
    private var blockedApps: List<String> = emptyList()

    // Website blocking properties
    private var blockedDomains: List<String> = emptyList()
    private var blockWebsitesOnlyInBrowsers = true
    private var currentUrl: String? = null

    // Common properties
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var customIconResId: Int? = null
    private var groupIconResId: Int? = null
    private var isServiceActive = false
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var lastBlockTime = 0L
    private val blockCooldownMs = 2000L

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

    private lateinit var stopBlockingReceiver: StopBlockingReceiver

    // Browser package names to monitor with their URL extraction methods
    private val browserPackages = mapOf(
        "com.android.chrome" to BrowserType.CHROME,
        "com.chrome.beta" to BrowserType.CHROME,
        "com.chrome.dev" to BrowserType.CHROME,
        "com.chrome.canary" to BrowserType.CHROME,
        "org.mozilla.firefox" to BrowserType.FIREFOX,
        "org.mozilla.firefox_beta" to BrowserType.FIREFOX,
        "com.microsoft.emmx" to BrowserType.EDGE,
        "com.opera.browser" to BrowserType.OPERA,
        "com.opera.browser.beta" to BrowserType.OPERA,
        "com.opera.mini.native" to BrowserType.OPERA,
        "com.sec.android.app.sbrowser" to BrowserType.SAMSUNG,
        "com.samsung.android.sbrowser" to BrowserType.SAMSUNG,
        "com.UCMobile.intl" to BrowserType.UC_BROWSER,
        "com.brave.browser" to BrowserType.BRAVE,
        "org.chromium.webview_shell" to BrowserType.CHROMIUM,
        "com.kiwibrowser.browser" to BrowserType.KIWI,
        "com.duckduckgo.mobile.android" to BrowserType.DUCKDUCKGO,
        "com.vivaldi.browser" to BrowserType.VIVALDI
    )

    // Apps that commonly use WebView for displaying web content
    private val webViewPackages = setOf(
        "com.facebook.katana", "com.instagram.android", "com.twitter.android",
        "com.linkedin.android", "com.pinterest", "com.reddit.frontpage",
        "com.tumblr", "com.medium.reader", "com.flipboard.app",
        "com.google.android.apps.news", "com.microsoft.office.outlook",
        "com.slack", "com.discord", "com.whatsapp", "com.telegram.messenger",
        "com.viber.voip", "com.skype.raider", "com.spotify.music",
        "com.netflix.mediaclient", "com.amazon.mShop.android.shopping",
        "com.ebay.mobile", "com.paypal.android.p2pmobile", "org.telegram.messenger"
    )

    private enum class BrowserType {
        CHROME, FIREFOX, EDGE, OPERA, SAMSUNG, UC_BROWSER, BRAVE, CHROMIUM, KIWI, DUCKDUCKGO, VIVALDI
    }

    companion object {
        const val TAG = "BlockingService"
        const val MONITORING_INTERVAL = 1000L // 1 second
        const val ACTION_STOP_BLOCKING = "com.shebnik.flutter_screen_time.STOP_BLOCKING"
        const val ACTION_STOP_BLOCKING_APPS = "com.shebnik.flutter_screen_time.STOP_BLOCKING_APPS"
        const val ACTION_STOP_BLOCKING_WEBSITES =
            "com.shebnik.flutter_screen_time.STOP_BLOCKING_WEBSITES"
        const val BACK_PRESS_COUNT = 10
        const val BACK_PRESS_INTERVAL = 100L // 100ms between back presses

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
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopBlockingReceiver, filter)
        }
    }

    fun saveServiceConfiguration(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("blocking_service_config", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        intent.getStringArrayListExtra("BUNDLE_IDS")?.let {
            editor.putStringSet("blocked_apps", it.toSet())
        }

        intent.getStringArrayListExtra("BLOCKED_WEB_DOMAINS")?.let {
            editor.putStringSet("blocked_domains", it.toSet())
        }

        intent.getStringExtra("BLOCK_OVERLAY_LAYOUT_PACKAGE")?.let {
            editor.putString("caller_package", it)
        }

        intent.getStringExtra("NOTIFICATION_TITLE")?.let {
            editor.putString("notification_title", it)
        }

        intent.getStringExtra("NOTIFICATION_BODY")?.let {
            editor.putString("notification_body", it)
        }

        editor.putBoolean("block_websites_only_in_browsers",
            intent.getBooleanExtra("BLOCK_WEBSITES_ONLY_IN_BROWSERS", true))

        editor.putBoolean("use_overlay_countdown",
            intent.getBooleanExtra("USE_OVERLAY_COUNTDOWN", false))

        editor.putInt("overlay_countdown_seconds",
            intent.getIntExtra("OVERLAY_COUNTDOWN_SECONDS", 5))

        intent.getStringExtra("BLOCK_OVERLAY_LAYOUT_NAME")?.let {
            editor.putString("layout_name", it)
        }

        editor.apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Unified blocking service started")

        intent?.let {
            saveServiceConfiguration(this, it)
            // Extract both app and website blocking data
            blockedApps = it.getStringArrayListExtra(Argument.BUNDLE_IDS) ?: emptyList()
            blockedDomains = it.getStringArrayListExtra(Argument.BLOCKED_WEB_DOMAINS) ?: emptyList()

            callerPackageName =
                it.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE) ?: packageName
            notificationTitle =
                it.getStringExtra(Argument.NOTIFICATION_TITLE) ?: getDefaultNotificationTitle()
            notificationBody =
                it.getStringExtra(Argument.NOTIFICATION_BODY) ?: getDefaultNotificationBody()

            blockWebsitesOnlyInBrowsers =
                it.getBooleanExtra(Argument.BLOCK_WEBSITES_ONLY_IN_BROWSERS, true)

            val customIconName = it.getStringExtra(Argument.NOTIFICATION_ICON)
            customIconResId = if (customIconName != null) {
                NotificationUtil.getIconResource(this, customIconName, callerPackageName)
            } else null

            val groupIconName = it.getStringExtra(Argument.NOTIFICATION_GROUP_ICON)
            groupIconResId = if (groupIconName != null) {
                NotificationUtil.getIconResource(this, groupIconName, callerPackageName)
            } else null

            useOverlayCountdown = it.getBooleanExtra(Argument.USE_OVERLAY_COUNTDOWN, false)
            overlayCountdownSeconds = it.getIntExtra(Argument.OVERLAY_COUNTDOWN_SECONDS, 5)
            layoutName = it.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME)
                ?: if (useOverlayCountdown) DEFAULT_COUNT_LAYOUT_NAME else DEFAULT_LAYOUT_NAME
        }

        isServiceActive = true

        val notification = NotificationUtil.createBlockingNotification(
            context = this,
            title = notificationTitle,
            body = notificationBody,
            customIconResId = customIconResId,
            blockedAppsCount = blockedApps.size,
            blockedDomainsCount = blockedDomains.size,
            groupIconResId = groupIconResId
        )
        startForeground(NotificationUtil.BLOCKING_NOTIFICATION_ID, notification)

        startAppMonitoring()
        Log.d(
            TAG,
            "Unified blocking started - Apps: ${blockedApps.size}, Domains: ${blockedDomains.size}"
        )
        return START_STICKY
    }

    override fun onInterrupt() {
        Log.d(TAG, "Unified blocking service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!isServiceActive || blockedDomains.isEmpty()) return

        val packageName = event.packageName?.toString() ?: return

        val shouldMonitor = if (blockWebsitesOnlyInBrowsers) {
            browserPackages.containsKey(packageName) || webViewPackages.contains(packageName)
        } else {
            true
        }

        if (shouldMonitor) {
            if (intArrayOf(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_VIEW_FOCUSED,
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                ).contains(event.eventType)
            ) {
                handler.removeCallbacks(urlCheckRunnable)
                handler.postDelayed(urlCheckRunnable, 200)
            }
        }
    }

    private val urlCheckRunnable = Runnable {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val packageName = rootNode.packageName?.toString()
                if (packageName != null && shouldMonitorPackageForWebsite(packageName)) {
                    checkUrlAndPerformAction(packageName, rootNode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in URL check runnable", e)
        }
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

                // Check for website monitoring
                if (blockedDomains.isNotEmpty()) {
                    if (!shouldMonitorPackageForWebsite(packageName)) {
                        currentUrl = null
                        // Don't hide overlay if countdown is active
                        if (!useOverlayCountdown || !isOverlayShowing || countdownRunnable == null) {
                            hideOverlay()
                        }
                    } else {
                        handler.postDelayed({
                            checkCurrentAppUrl(packageName)
                        }, 500)
                    }
                } else {
                    // Don't hide overlay if countdown is active
                    if (!useOverlayCountdown || !isOverlayShowing || countdownRunnable == null) {
                        hideOverlay()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun shouldMonitorPackageForWebsite(packageName: String): Boolean {
        return if (blockWebsitesOnlyInBrowsers) {
            browserPackages.containsKey(packageName)
        } else {
            browserPackages.containsKey(packageName) || webViewPackages.contains(packageName)
        }
    }

    private fun checkCurrentAppUrl(packageName: String) {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                checkUrlAndPerformAction(packageName, rootNode)
            } else {
                handler.postDelayed({
                    checkCurrentAppUrl(packageName)
                }, 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking current app URL", e)
        }
    }

    private fun checkUrlAndPerformAction(packageName: String, rootNode: AccessibilityNodeInfo?) {
        rootNode ?: return

        try {
            val url = if (browserPackages.containsKey(packageName)) {
                val browserType = browserPackages[packageName] ?: return
                extractUrlFromBrowser(rootNode, browserType)
            } else {
                extractUrlFromWebView(rootNode)
            }

            if (url != null) {
                currentUrl = url
                Log.d(TAG, "URL detected in $packageName: $url")

                if (isBlockedDomain(url)) {
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastBlockTime < blockCooldownMs) {
                        Log.d(TAG, "Blocking action skipped due to cooldown")
                        return
                    }

                    lastBlockTime = currentTime

                    if (useOverlayCountdown) {
                        showOverlay()
                        startBackButtonSequence()
                        Log.d(TAG, "Blocked domain detected, blocking actions initiated: $url")
                    } else {
                        performBackAction()
                        Log.d(TAG, "Blocked domain detected, back action performed: $url")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL", e)
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
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
        val countdownText = getResource("countdown_text", "id")
            .takeIf { it != 0 }
            ?.let { overlayView?.findViewById<TextView>(it) }

        currentCountdown = overlayCountdownSeconds
        countdownText?.text = getResource("closing_in_seconds", type = "string")
            .takeIf { it != 0 }
            ?.let { getString(it, currentCountdown) }
            ?: "Closing in ${currentCountdown}s"

        val launchButton = getResource("launch_app_button", "id")
            .takeIf { it != 0 }
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
                    val countdownText = getResource("countdown_text", "id")
                        .takeIf { it != 0 }
                        ?.let { overlayView?.findViewById<TextView>(it) }

                    countdownText?.text = getResource("closing_in_seconds", type = "string")
                        .takeIf { it != 0 }
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

    private fun performBackAction() {
        try {
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastBlockTime < blockCooldownMs) {
                Log.d(TAG, "Back action blocked due to cooldown")
                return
            }

            lastBlockTime = currentTime

            val success = performGlobalAction(GLOBAL_ACTION_BACK)

            if (success) {
                Log.d(TAG, "Back action performed successfully")
            } else {
                Log.w(TAG, "Back action failed")
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing back action", e)
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
        NotificationUtil.cleanupGroupSummary(this)
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
            blockedApps.isNotEmpty() && blockedDomains.isNotEmpty() ->
                "Monitoring ${blockedApps.size} apps and ${blockedDomains.size} websites"

            blockedApps.isNotEmpty() -> "Monitoring ${blockedApps.size} apps"
            blockedDomains.isNotEmpty() -> "Monitoring ${blockedDomains.size} websites"
            else -> "Service is running"
        }
    }

    private fun getResource(resourceName: String, type: String): Int {
        return try {
            val resources = if (callerPackageName != packageName) {
                packageManager.getResourcesForApplication(callerPackageName)
            } else {
                resources
            }

            @SuppressLint("DiscouragedApi")
            resources.getIdentifier(resourceName, type, callerPackageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout resource", e)
            0
        }
    }

    // Website blocking URL extraction methods
    private fun extractUrlFromBrowser(
        rootNode: AccessibilityNodeInfo,
        browserType: BrowserType
    ): String? {
        return when (browserType) {
            BrowserType.CHROME -> extractChromeUrl(rootNode)
            BrowserType.FIREFOX -> extractFirefoxUrl(rootNode)
            BrowserType.EDGE -> extractEdgeUrl(rootNode)
            BrowserType.SAMSUNG -> extractSamsungUrl(rootNode)
            BrowserType.BRAVE -> extractBraveUrl(rootNode)
            else -> extractGenericUrl(rootNode)
        }
    }

    private fun extractUrlFromWebView(rootNode: AccessibilityNodeInfo): String? {
        return findUrlInWebView(rootNode) ?: findUrlByContentDescription(rootNode)
        ?: findUrlInTextContent(rootNode)
    }

    private fun findUrlInWebView(rootNode: AccessibilityNodeInfo): String? {
        val webViewNodes = findNodesByClassName(rootNode, "android.webkit.WebView")
        for (node in webViewNodes) {
            node.contentDescription?.toString()?.let { desc ->
                extractUrlFromText(desc)?.let { return it }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    extractUrlRecursively(child)?.let { url -> return url }
                }
            }
        }
        return null
    }

    private fun extractChromeUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.android.chrome:id/url_bar")
            ?: findUrlByResourceId(rootNode, "com.android.chrome:id/location_bar_status")
            ?: findUrlInEditText(rootNode)
            ?: findUrlByContentDescription(rootNode)
    }

    private fun extractFirefoxUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "org.mozilla.firefox:id/url_bar_title")
            ?: findUrlByResourceId(
                rootNode,
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
            )
            ?: findUrlInEditText(rootNode)
    }

    private fun extractEdgeUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.microsoft.emmx:id/url_bar")
            ?: findUrlInEditText(rootNode)
    }

    private fun extractSamsungUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.sec.android.app.sbrowser:id/location_bar")
            ?: findUrlByResourceId(rootNode, "com.samsung.android.sbrowser:id/location_bar")
            ?: findUrlInEditText(rootNode)
    }

    private fun extractBraveUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.brave.browser:id/url_bar")
            ?: findUrlInEditText(rootNode)
    }

    private fun extractGenericUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlInEditText(rootNode) ?: findUrlByContentDescription(rootNode)
    }

    private fun findUrlByResourceId(rootNode: AccessibilityNodeInfo, resourceId: String): String? {
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId)
        for (node in nodes) {
            val text = node.text?.toString()
            if (text != null) {
                extractUrlFromText(text)?.let { return it }
            }
        }
        return null
    }

    private fun findUrlInEditText(rootNode: AccessibilityNodeInfo): String? {
        return findNodesByClassName(rootNode, "android.widget.EditText")
            .mapNotNull { it.text?.toString() }
            .firstNotNullOfOrNull { extractUrlFromText(it) }
    }

    private fun findUrlByContentDescription(rootNode: AccessibilityNodeInfo): String? {
        return findAllNodes(rootNode)
            .mapNotNull { it.contentDescription?.toString() }
            .firstNotNullOfOrNull { extractUrlFromText(it) }
    }

    private fun findUrlInTextContent(rootNode: AccessibilityNodeInfo): String? {
        return findAllNodes(rootNode)
            .mapNotNull { it.text?.toString() }
            .firstNotNullOfOrNull { extractUrlFromText(it) }
    }

    private fun findNodesByClassName(
        rootNode: AccessibilityNodeInfo,
        className: String
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.className?.toString() == className) {
                nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child -> traverse(child) }
            }
        }

        traverse(rootNode)
        return nodes
    }

    private fun findAllNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            nodes.add(node)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child -> traverse(child) }
            }
        }

        traverse(rootNode)
        return nodes
    }

    private fun extractUrlRecursively(node: AccessibilityNodeInfo): String? {
        node.text?.toString()?.let { text ->
            extractUrlFromText(text)?.let { return it }
        }

        node.contentDescription?.toString()?.let { desc ->
            extractUrlFromText(desc)?.let { return it }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractUrlRecursively(child)?.let { url -> return url }
            }
        }

        return null
    }

    private fun extractUrlFromText(text: String): String? {
        val urlPattern = Regex("https?://\\S+")
        val match = urlPattern.find(text)

        return match?.value?.let { url ->
            val cleanedUrl = url.trimEnd('.', ',', '!', '?', ')', '}', ']')
            if (isValidUrlFormat(cleanedUrl)) cleanedUrl else null
        } ?: run {
            if (isPossibleDomain(text)) {
                val urlWithProtocol = "https://$text"
                if (isValidUrlFormat(urlWithProtocol)) urlWithProtocol else null
            } else null
        }
    }

    private fun isPossibleDomain(text: String): Boolean {
        return text.contains(".") &&
                !text.contains(" ") &&
                text.length in 4..253 &&
                !text.contains("\n") &&
                !text.contains("\t") &&
                text.matches(Regex("^[a-zA-Z0-9.-]+$"))
    }

    private fun isValidUrlFormat(url: String): Boolean {
        return try {
            val urlObj = URL(url)
            // Additional validation
            urlObj.host != null &&
                    urlObj.host.isNotEmpty() &&
                    urlObj.host.contains(".")
        } catch (_: Exception) {
            false
        }
    }

    private fun isBlockedDomain(url: String): Boolean {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            val urlObj = URL(cleanUrl)
            val domain = urlObj.host?.lowercase()

            domain?.let { hostDomain ->
                blockedDomains.any { blockedDomain ->
                    val cleanBlockedDomain = blockedDomain.lowercase().removePrefix("www.")
                    val cleanHostDomain = hostDomain.removePrefix("www.")

                    // Exact match or subdomain match
                    cleanHostDomain == cleanBlockedDomain || cleanHostDomain.endsWith(".$cleanBlockedDomain")
                }
            } ?: false
        } catch (e: Exception) {
            Log.e(
                WebsitesBlockingAccessibilityService.Companion.TAG,
                "Error checking blocked domain for URL: $url",
                e
            )
            false
        }
    }
}