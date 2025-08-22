package com.shebnik.flutter_screen_time.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.shebnik.flutter_screen_time.const.Argument
import java.net.URL

class DomainBlockingAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var blockedDomains: List<String> = emptyList()
    private var layoutName: String = DEFAULT_LAYOUT_NAME
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var isServiceActive = false
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var currentUrl: String? = null
    private var isOverlayVisible = false

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

    private enum class BrowserType {
        CHROME, FIREFOX, EDGE, OPERA, SAMSUNG, UC_BROWSER, BRAVE, CHROMIUM, KIWI, DUCKDUCKGO, VIVALDI
    }

    companion object {
        const val DEFAULT_LAYOUT_NAME = "block_overlay"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "domain_blocking_service_channel"
        const val TAG = "BrowserBlock"
        const val MONITORING_INTERVAL = 1000L // 1 second

        const val ACTION_START_BLOCKING = "com.shebnik.flutter_screen_time.START_DOMAIN_BLOCKING"
        const val ACTION_STOP_BLOCKING = "com.shebnik.flutter_screen_time.STOP_DOMAIN_BLOCKING"

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (DomainBlockingAccessibilityService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Accessibility service started")

        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!isServiceActive) return

        // Check if the event is from a browser
        val packageName = event.packageName?.toString()
        if (packageName != null && browserPackages.containsKey(packageName)) {
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
                if (packageName != null && browserPackages.containsKey(packageName)) {
                    checkUrlAndShowOverlay(packageName, rootNode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in URL check runnable", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAppMonitoring()
        hideOverlay()
        Log.d(TAG, "Accessibility service destroyed")
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_START_BLOCKING -> startDomainBlocking(intent)
            ACTION_STOP_BLOCKING -> stopDomainBlocking()
        }
    }

    private fun startDomainBlocking(intent: Intent) {
        blockedDomains = intent.getStringArrayListExtra(Argument.BLOCKED_WEB_DOMAINS) ?: emptyList()
        layoutName =
            intent.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME) ?: DEFAULT_LAYOUT_NAME
        callerPackageName =
            intent.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE) ?: packageName
        notificationTitle =
            intent.getStringExtra(Argument.NOTIFICATION_TITLE) ?: "Domain Blocking Active"
        notificationBody = intent.getStringExtra(Argument.NOTIFICATION_BODY)
            ?: "Monitoring ${blockedDomains.size} domains"

        isServiceActive = true
        startForeground(NOTIFICATION_ID, createNotification())

        startAppMonitoring()

        Log.d(TAG, "Domain blocking started for domains: $blockedDomains")
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
                if (!browserPackages.containsKey(packageName)) {
                    // If current app is not a browser, hide overlay and reset URL
                    currentUrl = null
                    hideOverlay()
                } else {
                    // Browser is now in foreground, check URL immediately
                    handler.postDelayed({
                        checkCurrentBrowserUrl(packageName)
                    }, 500) // Short delay to allow browser UI to load
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun checkUrlAndShowOverlay(packageName: String, rootNode: AccessibilityNodeInfo?) {
        rootNode ?: return

        try {
            val browserType = browserPackages[packageName] ?: return
            val url = extractUrlFromBrowser(rootNode, browserType)

            if (url != null) {
                // Always update and check, don't compare with currentUrl to avoid missing cases
                currentUrl = url
                Log.d(TAG, "URL detected: $url")

                if (isBlockedDomain(url)) {
                    showOverlay()
                    Log.d(TAG, "Blocked domain detected: $url")
                } else {
                    hideOverlay()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun checkCurrentBrowserUrl(packageName: String) {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                checkUrlAndShowOverlay(packageName, rootNode)
            } else {
                // If we can't get root node, schedule another check
                handler.postDelayed({
                    checkCurrentBrowserUrl(packageName)
                }, 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking current browser URL", e)
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
            rootNode,
            "android.widget.EditText"
        )
    }

    private fun extractSamsungUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.sec.android.app.sbrowser:id/location_bar")
            ?: findUrlByResourceId(rootNode, "com.samsung.android.sbrowser:id/location_bar")
            ?: findUrlByClassName(rootNode, "android.widget.EditText")
    }

    private fun extractBraveUrl(rootNode: AccessibilityNodeInfo): String? {
        return findUrlByResourceId(rootNode, "com.brave.browser:id/url_bar") ?: findUrlByClassName(
            rootNode,
            "android.widget.EditText"
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

    private fun stopDomainBlocking() {
        isServiceActive = false
        hideOverlay()
        stopForeground(true)
        currentUrl = null
        Log.d(TAG, "Domain blocking stopped")
    }

    private fun showOverlay() {
        if (isOverlayVisible) return

        try {
            // Create overlay layout
            val layoutInflater = LayoutInflater.from(this)
            val layoutResId = getLayoutResource(layoutName)

            overlayView = if (layoutResId != 0) {
                layoutInflater.inflate(layoutResId, null)
            } else {
                // Fallback to default layout if specified layout is not found
                layoutInflater.inflate(
                    getLayoutResource(DEFAULT_LAYOUT_NAME), null
                )
            }

            // Set up window parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            windowManager?.addView(overlayView, params)
            isOverlayVisible = true
            Log.d(TAG, "Overlay shown for blocked domain: $currentUrl")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                overlayView = null
                isOverlayVisible = false
                Log.d(TAG, "Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay", e)
            }
        }
    }

    private fun getLayoutResource(layoutName: String): Int {
        return try {
            val resources = if (callerPackageName != packageName) {
                packageManager.getResourcesForApplication(callerPackageName)
            } else {
                resources
            }

            resources.getIdentifier(layoutName, "layout", callerPackageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout resource", e)
            0
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
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(notificationTitle)
            .setContentText(notificationBody).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
    }

    private fun hideNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}