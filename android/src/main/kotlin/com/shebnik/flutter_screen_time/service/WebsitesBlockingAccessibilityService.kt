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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.receiver.StopWebsitesBlockingReceiver
import com.shebnik.flutter_screen_time.util.NotificationUtil
import com.shebnik.flutter_screen_time.util.NotificationUtil.stopForegroundWithCleanup
import com.shebnik.flutter_screen_time.util.logDebug
import com.shebnik.flutter_screen_time.util.logError
import com.shebnik.flutter_screen_time.util.logWarning
import java.net.URL

class WebsitesBlockingAccessibilityService : AccessibilityService() {

    private var blockedDomains: List<String> = emptyList()
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var customIconResId: Int? = null
    private var groupIconResId: Int? = null
    private var isServiceActive = false
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var currentUrl: String? = null
    private var blockWebsitesOnlyInBrowsers = true
    private var lastBlockTime = 0L
    private val blockCooldownMs = 2000L

    private var layoutName: String = DEFAULT_LAYOUT_NAME
    private var useOverlayCountdown: Boolean = false
    private var overlayCountdownSeconds: Int = 5

    // Overlay related variables
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var countdownRunnable: Runnable? = null
    private var backPressRunnable: Runnable? = null
    private var currentCountdown = 0
    private var backPressCount = 0

    private lateinit var stopBlockingReceiver: StopWebsitesBlockingReceiver

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
        "com.facebook.katana", // Facebook
        "com.instagram.android", // Instagram
        "com.twitter.android", // Twitter/X
        "com.linkedin.android", // LinkedIn
        "com.pinterest", // Pinterest
        "com.reddit.frontpage", // Reddit
        "com.tumblr", // Tumblr
        "com.medium.reader", // Medium
        "com.flipboard.app", // Flipboard
        "com.google.android.apps.news", // Google News
        "com.microsoft.office.outlook", // Outlook (email links)
        "com.slack", // Slack
        "com.discord", // Discord
        "com.whatsapp", // WhatsApp (link previews)
        "com.telegram.messenger", // Telegram
        "com.viber.voip", // Viber
        "com.skype.raider", // Skype
        "com.spotify.music", // Spotify (web player)
        "com.netflix.mediaclient", // Netflix
        "com.amazon.mShop.android.shopping", // Amazon
        "com.ebay.mobile", // eBay
        "com.paypal.android.p2pmobile", // PayPal
        "org.telegram.messenger"
    )

    private enum class BrowserType {
        CHROME, FIREFOX, EDGE, OPERA, SAMSUNG, UC_BROWSER, BRAVE, CHROMIUM, KIWI, DUCKDUCKGO, VIVALDI
    }

    companion object {
        const val TAG = "WebsitesBlock"
        const val MONITORING_INTERVAL = 1000L // 1 second
        const val ACTION_STOP_BLOCKING = "com.shebnik.flutter_screen_time.STOP_BLOCKING"
        const val BACK_PRESS_COUNT = 10
        const val BACK_PRESS_INTERVAL = 100L // 100ms between back presses

        const val DEFAULT_LAYOUT_NAME = "block_overlay"
        const val DEFAULT_COUNT_LAYOUT_NAME = "block_overlay_count"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        logDebug(TAG, "Accessibility service connected")

        // Initialize WindowManager for overlay
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Use centralized notification channel creation
        NotificationUtil.createNotificationChannel(this)

        // Initialize the receiver with reference to this service
        stopBlockingReceiver = StopWebsitesBlockingReceiver(this)

        // Register the receiver
        val filter = IntentFilter(ACTION_STOP_BLOCKING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopBlockingReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag") registerReceiver(
                stopBlockingReceiver, filter
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logDebug(TAG, "Accessibility service started")

        intent?.let {
            blockedDomains =
                intent.getStringArrayListExtra(Argument.BLOCKED_WEB_DOMAINS) ?: emptyList()
            callerPackageName =
                intent.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE) ?: packageName
            notificationTitle =
                intent.getStringExtra(Argument.NOTIFICATION_TITLE) ?: "Domain Blocking Active"
            notificationBody = intent.getStringExtra(Argument.NOTIFICATION_BODY)
                ?: "Monitoring ${blockedDomains.size} domains"
            blockWebsitesOnlyInBrowsers =
                intent.getBooleanExtra(Argument.BLOCK_WEBSITES_ONLY_IN_BROWSERS, true)

            val customIconName = intent.getStringExtra(Argument.NOTIFICATION_ICON)
            customIconResId = if (customIconName != null) {
                NotificationUtil.getIconResource(this, customIconName, callerPackageName)
            } else {
                null
            }

            val groupIconName = intent.getStringExtra(Argument.NOTIFICATION_GROUP_ICON)
            groupIconResId = if (groupIconName != null) {
                NotificationUtil.getIconResource(this, groupIconName, callerPackageName)
            } else {
                null
            }

            useOverlayCountdown = it.getBooleanExtra(Argument.USE_OVERLAY_COUNTDOWN, false)
            overlayCountdownSeconds = it.getIntExtra(Argument.OVERLAY_COUNTDOWN_SECONDS, 5)
            layoutName =
                it.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME)
                    ?: if (useOverlayCountdown) DEFAULT_COUNT_LAYOUT_NAME else DEFAULT_LAYOUT_NAME
        }

        isServiceActive = true

        // Create notification using centralized utility
        val notification = NotificationUtil.createWebsiteBlockingNotification(
            context = this,
            title = notificationTitle,
            body = notificationBody,
            customIconResId = customIconResId,
            blockedDomainsCount = blockedDomains.size,
            blockWebsitesOnlyInBrowsers = blockWebsitesOnlyInBrowsers,
            groupIconResId = groupIconResId
        )
        startForeground(NotificationUtil.WEBSITES_BLOCKING_NOTIFICATION_ID, notification)

        startAppMonitoring()
        logDebug(
            TAG,
            "Domain blocking started for domains: $blockedDomains, browser-only: $blockWebsitesOnlyInBrowsers"
        )
        return START_STICKY
    }

    override fun onInterrupt() {
        logDebug(TAG, "Accessibility service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!isServiceActive) return

        val packageName = event.packageName?.toString() ?: return

        // Check if we should monitor this app based on the flag
        val shouldMonitor = if (blockWebsitesOnlyInBrowsers) {
            // Only monitor browsers
            browserPackages.containsKey(packageName) || webViewPackages.contains(packageName)
        } else {
            // Monitor all apps
            true
        }

        if (shouldMonitor) {
            // Handle different event types that might indicate URL changes
            if (intArrayOf(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_VIEW_FOCUSED,
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                ).contains(
                    event.eventType
                )
            ) {
                // Use a small delay to avoid too frequent checks
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
                if (packageName != null && shouldMonitorPackage(packageName)) {
                    checkUrlAndPerformAction(packageName, rootNode)
                }
            }
        } catch (e: Exception) {
            logError(TAG, "Error in URL check runnable", e)
        }
    }

    private fun shouldMonitorPackage(packageName: String): Boolean {
        return if (blockWebsitesOnlyInBrowsers) {
            browserPackages.containsKey(packageName)
        } else {
            browserPackages.containsKey(packageName) || webViewPackages.contains(packageName)
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
            logError(BlockAppsService.Companion.TAG, "Error getting layout resource", e)
            0
        }
    }

    private fun showBlockingOverlay() {
        if (isOverlayShowing || !useOverlayCountdown) return

        try {
            // Create overlay layout
            val layoutInflater = LayoutInflater.from(this)
            val layoutResId = getResource(layoutName, "layout")

            overlayView = if (layoutResId != 0) {
                layoutInflater.inflate(layoutResId, null)
            } else {
                // Fallback to default layout if specified layout is not found
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
                // Update countdown text if available
                val countdownText = getResource("countdown_text", "id")
                    .takeIf { it != 0 }
                    ?.let { overlayView?.findViewById<TextView>(it) }
                currentCountdown = overlayCountdownSeconds
                countdownText?.text = getResource("closing_in_seconds", type = "string")
                    .takeIf { it != 0 }
                    ?.let { getString(it, currentCountdown) }
                    ?: "Closing in ${currentCountdown}s"

                // Set up launch app button
                val launchButton = getResource("launch_app_button", "id")
                    .takeIf { it != 0 }
                    ?.let {
                        overlayView?.findViewById<Button>(it)
                    }
                launchButton?.setOnClickListener {
                    stopBackButtonSequence()
                    // Launch the calling app
                    launchCallerApp()
                }
            }

            windowManager?.addView(overlayView, params)
            isOverlayShowing = true

            startCountdown()
            logDebug(TAG, "Blocking overlay shown")

        } catch (e: Exception) {
            logError(TAG, "Error showing blocking overlay", e)
        }
    }

    private fun hideBlockingOverlay() {
        try {
            if (isOverlayShowing && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false

                // Cancel countdown
                countdownRunnable?.let { handler.removeCallbacks(it) }
                countdownRunnable = null

                logDebug(TAG, "Blocking overlay hidden")
            }
        } catch (e: Exception) {
            logError(TAG, "Error hiding blocking overlay", e)
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
                    // Countdown finished, hide overlay and perform home action
                    hideBlockingOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    logDebug(TAG, "Countdown finished, navigated to home")
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun launchCallerApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(callerPackageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                hideBlockingOverlay()
                logDebug(TAG, "Launched caller app: $callerPackageName")
            } else {
                logError(TAG, "Cannot launch caller app: $callerPackageName")
            }
        } catch (e: Exception) {
            logError(TAG, "Error launching caller app", e)
        }
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

                        // Continue with next back press
                        handler.postDelayed(this, BACK_PRESS_INTERVAL)
                    } else {
                        logWarning(TAG, "Back press $backPressCount failed, retrying...")
                        handler.postDelayed(this, BACK_PRESS_INTERVAL)
                    }
                } else {
                    // All back presses completed, perform home action
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

    fun stopBlocking() {
        isServiceActive = false
        stopAppMonitoring()
        hideBlockingOverlay()

        // Cancel any pending back press actions
        backPressRunnable?.let { handler.removeCallbacks(it) }
        backPressRunnable = null

        stopForegroundWithCleanup()
        currentUrl = null
        logDebug(TAG, "Domain blocking deactivated")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopBlockingReceiver)
        } catch (e: Exception) {
            logError(TAG, "Error unregistering receiver", e)
        }

        hideBlockingOverlay()
        NotificationUtil.cleanupGroupSummary(this)
        logDebug(TAG, "Accessibility service destroyed")
        super.onDestroy()
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
                if (!shouldMonitorPackage(packageName)) {
                    // If current app is not monitored, reset URL
                    currentUrl = null
                } else {
                    // Monitored app is now in foreground, check URL immediately
                    handler.postDelayed({
                        checkCurrentAppUrl(packageName)
                    }, 500) // Short delay to allow app UI to load
                }
            }
        } catch (e: Exception) {
            logError(TAG, "Error checking foreground app", e)
        }
    }

    private fun checkUrlAndPerformAction(packageName: String, rootNode: AccessibilityNodeInfo?) {
        rootNode ?: return

        try {
            val url = if (browserPackages.containsKey(packageName)) {
                // Extract URL from browser
                val browserType = browserPackages[packageName] ?: return
                extractUrlFromBrowser(rootNode, browserType)
            } else {
                // Extract URL from WebView (for non-browser apps)
                extractUrlFromWebView(rootNode)
            }

            if (url != null) {
                // Always update and check, don't compare with currentUrl to avoid missing cases
                currentUrl = url
                logDebug(TAG, "URL detected in $packageName: $url")

                if (isBlockedDomain(url)) {
                    if (useOverlayCountdown) {
                        val currentTime = System.currentTimeMillis()

                        // Check cooldown to prevent rapid blocking
                        if (currentTime - lastBlockTime < blockCooldownMs) {
                            logDebug(TAG, "Blocking action skipped due to cooldown")
                            return
                        }

                        lastBlockTime = currentTime
                        showBlockingOverlay()
                        startBackButtonSequence()
                        logDebug(TAG, "Blocked domain detected, blocking actions initiated: $url")
                    } else {
                        performBackAction()
                        logDebug(TAG, "Blocked domain detected, back action performed: $url")
                    }
                }
            }
        } catch (e: Exception) {
            logError(TAG, "Error checking URL", e)
        }
    }

    private fun checkCurrentAppUrl(packageName: String) {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                checkUrlAndPerformAction(packageName, rootNode)
            } else {
                // If we can't get root node, schedule another check
                handler.postDelayed({
                    checkCurrentAppUrl(packageName)
                }, 1000)
            }
        } catch (e: Exception) {
            logError(TAG, "Error checking current app URL", e)
        }
    }

    private fun performBackAction() {
        try {
            val currentTime = System.currentTimeMillis()

            // Check cooldown to prevent rapid back presses
            if (currentTime - lastBlockTime < blockCooldownMs) {
                logDebug(TAG, "Back action blocked due to cooldown")
                return
            }

            lastBlockTime = currentTime

            // Perform back action using global gesture
            val success = performGlobalAction(GLOBAL_ACTION_BACK)

            if (success) {
                logDebug(TAG, "Back action performed successfully")
            } else {
                logWarning(TAG, "Back action failed")
                // Fallback: try again after a short delay
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 500)
            }
        } catch (e: Exception) {
            logError(TAG, "Error performing back action", e)
        }
    }

    private fun extractUrlFromBrowser(
        rootNode: AccessibilityNodeInfo, browserType: BrowserType
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
        // For WebView apps, try to find URLs in text content, content descriptions, or WebView nodes
        return findUrlInWebView(rootNode) ?: findUrlByContentDescription(rootNode)
        ?: findUrlInTextContent(rootNode)
    }

    private fun findUrlInWebView(rootNode: AccessibilityNodeInfo): String? {
        // Look for WebView nodes
        val webViewNodes = findNodesByClassName(rootNode, "android.webkit.WebView")
        for (node in webViewNodes) {
            // Check if WebView has URL information in its properties
            node.contentDescription?.toString()?.let { desc ->
                extractUrlFromText(desc)?.let { return it }
            }

            // Check child nodes of WebView
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    extractUrlRecursively(child)?.let { url ->
                        return url
                    }
                }
            }
        }
        return null
    }

    private fun findUrlInTextContent(rootNode: AccessibilityNodeInfo): String? {
        return findAllNodes(rootNode).mapNotNull { it.text?.toString() }
            .firstNotNullOfOrNull { text ->
                extractUrlFromText(text)
            }
    }

    private fun extractUrlRecursively(node: AccessibilityNodeInfo): String? {
        // Check current node
        node.text?.toString()?.let { text ->
            extractUrlFromText(text)?.let { return it }
        }

        node.contentDescription?.toString()?.let { desc ->
            extractUrlFromText(desc)?.let { return it }
        }

        // Check child nodes
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractUrlRecursively(child)?.let { url ->
                    return url
                }
            }
        }

        return null
    }

    private fun extractChromeUrl(rootNode: AccessibilityNodeInfo): String? {
        // Try multiple approaches for Chrome
        return findUrlByResourceId(rootNode, "com.android.chrome:id/url_bar")
            ?: findUrlByResourceId(rootNode, "com.android.chrome:id/location_bar_status")
            ?: findUrlInEditText(rootNode) ?: findUrlByContentDescription(rootNode)
    }

    private fun extractFirefoxUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "org.mozilla.firefox:id/url_bar_title")
            ?: findUrlByResourceId(
                rootNode, "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
            ) ?: findUrlInEditText(rootNode)
    }

    private fun extractEdgeUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.microsoft.emmx:id/url_bar") ?: findUrlInEditText(
            rootNode
        )
    }

    private fun extractSamsungUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.sec.android.app.sbrowser:id/location_bar")
            ?: findUrlByResourceId(rootNode, "com.samsung.android.sbrowser:id/location_bar")
            ?: findUrlInEditText(rootNode)
    }

    private fun extractBraveUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.brave.browser:id/url_bar") ?: findUrlInEditText(
            rootNode
        )
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
        val className = "android.widget.EditText"
        return findNodesByClassName(rootNode, className).mapNotNull { it.text?.toString() }
            .firstNotNullOfOrNull { extractUrlFromText(it) }
    }

    private fun findUrlByContentDescription(rootNode: AccessibilityNodeInfo): String? {
        return findAllNodes(rootNode).mapNotNull { it.contentDescription?.toString() }
            .firstNotNullOfOrNull { extractUrlFromText(it) }
    }

    private fun findNodesByClassName(
        rootNode: AccessibilityNodeInfo, className: String
    ): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.className?.toString() == className) {
                nodes.add(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    traverse(child)
                }
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
                node.getChild(i)?.let { child ->
                    traverse(child)
                }
            }
        }

        traverse(rootNode)
        return nodes
    }

    private fun extractUrlFromText(text: String): String? {
        // First try to extract URL from mixed text content
        val urlPattern = Regex("https?://\\S+")
        val match = urlPattern.find(text)

        return match?.value?.let { url ->
            // Clean up the extracted URL (remove trailing punctuation)
            val cleanedUrl = url.trimEnd('.', ',', '!', '?', ')', '}', ']')
            if (isValidUrlFormat(cleanedUrl)) cleanedUrl else null
        } ?: run {
            // If no http/https URL found, check if the entire text is a domain
            if (isPossibleDomain(text)) {
                val urlWithProtocol = "https://$text"
                if (isValidUrlFormat(urlWithProtocol)) urlWithProtocol else null
            } else null
        }
    }

    private fun isPossibleDomain(text: String): Boolean {
        // Check if text looks like a domain (contains dot, no spaces, reasonable length)
        return text.contains(".") &&
                !text.contains(" ") &&
                text.length in 4..253 && // Valid domain length range
                !text.contains("\n") &&
                !text.contains("\t") &&
                // Simple check for valid domain characters
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
            logError(TAG, "Error checking blocked domain for URL: $url", e)
            false
        }
    }
}