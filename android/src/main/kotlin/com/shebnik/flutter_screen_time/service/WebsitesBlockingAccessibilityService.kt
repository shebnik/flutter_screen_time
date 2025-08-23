package com.shebnik.flutter_screen_time.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.shebnik.flutter_screen_time.const.Argument
import java.net.URL

class WebsitesBlockingAccessibilityService : AccessibilityService() {

    private var blockedDomains: List<String> = emptyList()
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var isServiceActive = false
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var currentUrl: String? = null
    private var blockWebsitesOnlyInBrowsers = true
    private var lastBlockTime = 0L
    private val blockCooldownMs =
        2000L // 2 seconds cooldown between blocks to prevent rapid back presses

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
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "web_domain_blocking_service_channel"
        const val TAG = "WebsitesBlock"
        const val MONITORING_INTERVAL = 1000L // 1 second

        const val ACTION_STOP_BLOCKING = "com.shebnik.flutter_screen_time.STOP_BLOCKING"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        createNotificationChannel()

        // Register the receiver
        val filter = IntentFilter(ACTION_STOP_BLOCKING)
        registerReceiver(stopBlockingReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Accessibility service started")

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
        }

        isServiceActive = true
        startForeground(NOTIFICATION_ID, createNotification())

        startAppMonitoring()
        Log.d(
            TAG,
            "Domain blocking started for domains: $blockedDomains, browser-only: $blockWebsitesOnlyInBrowsers"
        )
        return START_STICKY
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!isServiceActive) return

        val packageName = event.packageName?.toString() ?: return

        // Check if we should monitor this app based on the flag
        val shouldMonitor = if (blockWebsitesOnlyInBrowsers) {
            // Only monitor browsers
            browserPackages.containsKey(packageName)
        } else {
            // Monitor both browsers and WebView apps
            browserPackages.containsKey(packageName) || webViewPackages.contains(packageName)
        }

        if (shouldMonitor) {
            // Handle different event types that might indicate URL changes
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_VIEW_FOCUSED, AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // Use a small delay to avoid too frequent checks
                    handler.removeCallbacks(urlCheckRunnable)
                    handler.postDelayed(urlCheckRunnable, 200)
                }
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
            Log.e(TAG, "Error in URL check runnable", e)
        }
    }

    private fun shouldMonitorPackage(packageName: String): Boolean {
        return if (blockWebsitesOnlyInBrowsers) {
            browserPackages.containsKey(packageName)
        } else {
            browserPackages.containsKey(packageName) || webViewPackages.contains(packageName)
        }
    }

    fun stopBlocking() {
        isServiceActive = false
        stopAppMonitoring()
        stopForeground(true)
        currentUrl = null
        Log.d(TAG, "Domain blocking deactivated")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopBlockingReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    private val stopBlockingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_BLOCKING) {
                stopBlocking()
            }
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
            Log.e(TAG, "Error checking foreground app", e)
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
                Log.d(TAG, "URL detected in $packageName: $url")

                if (isBlockedDomain(url)) {
                    performBackAction()
                    Log.d(TAG, "Blocked domain detected, back action performed: $url")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL", e)
        } finally {
            rootNode.recycle()
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
            Log.e(TAG, "Error checking current app URL", e)
        }
    }

    private fun performBackAction() {
        try {
            val currentTime = System.currentTimeMillis()

            // Check cooldown to prevent rapid back presses
            if (currentTime - lastBlockTime < blockCooldownMs) {
                Log.d(TAG, "Back action blocked due to cooldown")
                return
            }

            lastBlockTime = currentTime

            // Perform back action using global gesture
            val success = performGlobalAction(GLOBAL_ACTION_BACK)

            if (success) {
                Log.d(TAG, "Back action performed successfully")
            } else {
                Log.w(TAG, "Back action failed")
                // Fallback: try again after a short delay
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing back action", e)
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
                if (isValidUrl(desc)) return desc
            }

            // Check child nodes of WebView
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val url = extractUrlRecursively(child)
                    if (url != null && isValidUrl(url)) return url
                }
            }
        }
        return null
    }

    private fun findUrlInTextContent(rootNode: AccessibilityNodeInfo): String? {
        // Search through all text nodes for URLs
        return findAllNodes(rootNode).mapNotNull { it.text?.toString() }.firstOrNull { text ->
            // Look for URLs within text content
            val urlPattern = Regex("https?://[^\\s]+")
            urlPattern.find(text)?.value?.let { url ->
                if (isValidUrl(url)) return url
            }
            false
        }
    }

    private fun extractUrlRecursively(node: AccessibilityNodeInfo): String? {
        // Check current node
        node.text?.toString()?.let { text ->
            if (isValidUrl(text)) return text
        }

        node.contentDescription?.toString()?.let { desc ->
            if (isValidUrl(desc)) return desc
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
            ?: findUrlByClassName(rootNode, "android.widget.EditText")
            ?: findUrlByContentDescription(rootNode)
    }

    private fun extractFirefoxUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "org.mozilla.firefox:id/url_bar_title")
            ?: findUrlByResourceId(
                rootNode, "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
            ) ?: findUrlByClassName(rootNode, "android.widget.EditText")
    }

    private fun extractEdgeUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.microsoft.emmx:id/url_bar") ?: findUrlByClassName(
            rootNode, "android.widget.EditText"
        )
    }

    private fun extractSamsungUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.sec.android.app.sbrowser:id/location_bar")
            ?: findUrlByResourceId(rootNode, "com.samsung.android.sbrowser:id/location_bar")
            ?: findUrlByClassName(rootNode, "android.widget.EditText")
    }

    private fun extractBraveUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.brave.browser:id/url_bar") ?: findUrlByClassName(
            rootNode, "android.widget.EditText"
        )
    }

    private fun extractGenericUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByClassName(rootNode, "android.widget.EditText")
            ?: findUrlByContentDescription(rootNode)
    }

    private fun findUrlByResourceId(rootNode: AccessibilityNodeInfo, resourceId: String): String? {
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId)
        for (node in nodes) {
            val text = node.text?.toString()
            if (text != null && isValidUrl(text)) {
                return text
            }
        }
        return null
    }

    private fun findUrlByClassName(rootNode: AccessibilityNodeInfo, className: String): String? {
        return findNodesByClassName(rootNode, className).mapNotNull { it.text?.toString() }
            .firstOrNull { isValidUrl(it) }
    }

    private fun findUrlByContentDescription(rootNode: AccessibilityNodeInfo): String? {
        return findAllNodes(rootNode).mapNotNull { it.contentDescription?.toString() }
            .firstOrNull { isValidUrl(it) }
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

    private fun isValidUrl(text: String): Boolean {
        return try {
            val cleanUrl = if (!text.startsWith("http://") && !text.startsWith("https://")) {
                "https://$text"
            } else {
                text
            }

            URL(cleanUrl)
            text.contains(".") && !text.contains(" ") && text.length > 4
        } catch (e: Exception) {
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
            Log.e(TAG, "Error checking blocked domain for URL: $url", e)
            false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Web Domain Block Service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Service for blocking specified domains in browsers"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val modeText = if (blockWebsitesOnlyInBrowsers) "browsers only" else "browsers and apps"
        val bodyText = notificationBody ?: "Monitoring ${blockedDomains.size} domains in $modeText"

        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(notificationTitle)
            .setContentText(bodyText).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
    }
}