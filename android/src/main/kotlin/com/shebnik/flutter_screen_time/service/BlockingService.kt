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
import com.shebnik.flutter_screen_time.util.NotificationUtil.stopForegroundWithCleanup
import java.net.URL
import androidx.core.content.edit

class BlockingService : AccessibilityService() {

    // App blocking properties
    private var blockedApps: List<String> = emptyList()

    // Website blocking properties
    private var blockedDomains: List<String> = emptyList()
    private var currentUrl: String? = null

    // Common properties
    private var callerPackageName: String = ""
    private var notificationTitle: String? = null
    private var notificationBody: String? = null
    private var customIconResId: Int? = null
    private var isServiceActive = false
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var lastBlockTime = 0L
    private val blockCooldownMs = 1500L // Reduced cooldown for better responsiveness

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

    companion object {
        const val TAG = "BlockingService"
        const val PREFS_NAME = "blocking_service_config"
        const val MONITORING_INTERVAL = 800L // Faster monitoring for better detection
        const val URL_CHECK_DELAY = 150L // Faster URL checking
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
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopBlockingReceiver, filter)
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
            blockedDomainsCount = blockedDomains.size
        )
        startForeground(NotificationUtil.BLOCKING_NOTIFICATION_ID, notification)

        startAppMonitoring()
        Log.d(
            TAG,
            "Unified blocking started - Apps: ${blockedApps.size}, Domains: ${blockedDomains.size}"
        )
        return START_STICKY
    }

    private fun loadConfiguration(intent: Intent?, prefs: android.content.SharedPreferences) {
        prefs.edit {

            // Load blocked apps - prefer intent, fallback to prefs
            val intentBlockedApps = intent?.getStringArrayListExtra(Argument.BUNDLE_IDS)
            blockedApps = intentBlockedApps ?: prefs.getStringSet(
                Argument.BUNDLE_IDS,
                null
            )?.toList()
                    ?: emptyList()
            intentBlockedApps?.let { putStringSet(Argument.BUNDLE_IDS, it.toSet()) }

            // Load blocked domains - prefer intent, fallback to prefs
            val intentBlockedDomains = intent?.getStringArrayListExtra(Argument.BLOCKED_WEB_DOMAINS)
            blockedDomains = intentBlockedDomains ?: prefs.getStringSet(
                Argument.BLOCKED_WEB_DOMAINS,
                null
            )?.toList()
                    ?: emptyList()
            intentBlockedDomains?.let { putStringSet(Argument.BLOCKED_WEB_DOMAINS, it.toSet()) }

            // Load caller package name - prefer intent, fallback to prefs
            val intentCallerPackage = intent?.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE)
            callerPackageName = intentCallerPackage ?: prefs.getString(
                Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE,
                null
            ) ?: packageName
            intentCallerPackage?.let { putString(Argument.BLOCK_OVERLAY_LAYOUT_PACKAGE, it) }

            // Load notification title - prefer intent, fallback to prefs, then default
            val intentNotificationTitle = intent?.getStringExtra(Argument.NOTIFICATION_TITLE)
            notificationTitle = intentNotificationTitle ?: prefs.getString(
                Argument.NOTIFICATION_TITLE,
                null
            ) ?: getDefaultNotificationTitle()
            intentNotificationTitle?.let { putString(Argument.NOTIFICATION_TITLE, it) }

            // Load notification body - prefer intent, fallback to prefs, then default
            notificationBody =
                intent?.getStringExtra(Argument.NOTIFICATION_BODY) ?: prefs.getString(
                    Argument.NOTIFICATION_BODY,
                    null
                )
                        ?: getDefaultNotificationBody()
            intent?.getStringExtra(Argument.NOTIFICATION_BODY)?.let { intentValue ->
                putString(Argument.NOTIFICATION_BODY, intentValue)
            }

            // Load custom icon - prefer intent, fallback to prefs
            val customIconName =
                intent?.getStringExtra(Argument.NOTIFICATION_ICON) ?: prefs.getString(
                    Argument.NOTIFICATION_ICON,
                    null
                )
            intent?.getStringExtra(Argument.NOTIFICATION_ICON)?.let { intentValue ->
                putString(Argument.NOTIFICATION_ICON, intentValue)
            }
            customIconResId = if (customIconName != null) {
                NotificationUtil.getIconResource(this, customIconName, callerPackageName)
            } else null

            // Load overlay countdown settings - prefer intent, fallback to prefs
            useOverlayCountdown = when {
                intent?.hasExtra(Argument.USE_OVERLAY_COUNTDOWN) == true ->
                    intent.getBooleanExtra(Argument.USE_OVERLAY_COUNTDOWN, true)

                prefs.contains(Argument.USE_OVERLAY_COUNTDOWN) ->
                    prefs.getBoolean(Argument.USE_OVERLAY_COUNTDOWN, true)

                else -> true
            }

            if (intent?.hasExtra(Argument.USE_OVERLAY_COUNTDOWN) == true) {
                putBoolean(Argument.USE_OVERLAY_COUNTDOWN, useOverlayCountdown)
            }

            overlayCountdownSeconds = when {
                intent?.hasExtra(Argument.OVERLAY_COUNTDOWN_SECONDS) == true ->
                    intent.getIntExtra(Argument.OVERLAY_COUNTDOWN_SECONDS, 10)

                prefs.contains(Argument.OVERLAY_COUNTDOWN_SECONDS) ->
                    prefs.getInt(Argument.OVERLAY_COUNTDOWN_SECONDS, 10)

                else -> 10
            }

            if (intent?.hasExtra(Argument.OVERLAY_COUNTDOWN_SECONDS) == true) {
                putInt(Argument.OVERLAY_COUNTDOWN_SECONDS, overlayCountdownSeconds)
            }

            // Load layout name - prefer intent, fallback to prefs, then default
            layoutName =
                intent?.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME) ?: prefs.getString(
                    Argument.BLOCK_OVERLAY_LAYOUT_NAME,
                    null
                ) ?: if (useOverlayCountdown) DEFAULT_COUNT_LAYOUT_NAME else DEFAULT_LAYOUT_NAME
            intent?.getStringExtra(Argument.BLOCK_OVERLAY_LAYOUT_NAME)?.let { intentValue ->
                putString(Argument.BLOCK_OVERLAY_LAYOUT_NAME, intentValue)
            }

        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Unified blocking service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!isServiceActive || blockedDomains.isEmpty()) return

        // Monitor ALL apps for website content - no restrictions
        if (intArrayOf(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SELECTED
            ).contains(event.eventType)
        ) {
            handler.removeCallbacks(urlCheckRunnable)
            handler.postDelayed(urlCheckRunnable, URL_CHECK_DELAY)
        }
    }

    private val urlCheckRunnable = Runnable {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val packageName = rootNode.packageName?.toString()
                if (packageName != null) {
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

                // Check for website content in ALL apps if domains are blocked
                if (blockedDomains.isNotEmpty()) {
                    handler.postDelayed({
                        checkCurrentAppUrl(packageName)
                    }, 300)
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

        // Skip caller package
        if (packageName == callerPackageName) {
            return
        }

        try {
            // Just scan the entire screen - no special cases
            val url = scanEntireScreenForUrls(rootNode)

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

            @SuppressLint("DiscouragedApi")
            resources.getIdentifier(resourceName, type, callerPackageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout resource", e)
            0
        }
    }

    private fun scanEntireScreenForUrls(rootNode: AccessibilityNodeInfo): String? {
        val allTextContent = mutableSetOf<String>() // Use Set to avoid duplicates

        // Collect ALL text content from the screen
        collectAllScreenContent(rootNode, allTextContent)

        // Process all collected text
        for (text in allTextContent) {
            val foundUrl = extractUrlFromText(text)
            if (foundUrl != null) {
                return foundUrl
            }
        }

        return null
    }

    private fun collectAllScreenContent(
        node: AccessibilityNodeInfo,
        textSet: MutableSet<String>
    ) {
        try {
            // Get text content
            node.text?.toString()?.let { text ->
                if (text.isNotBlank() && text.length < 1000) {
                    textSet.add(text.trim())
                }
            }

            // Get content description
            node.contentDescription?.toString()?.let { desc ->
                if (desc.isNotBlank() && desc.length < 1000) {
                    textSet.add(desc.trim())
                }
            }

            // Get tooltip text (Android P+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.tooltipText?.toString()?.let { tooltip ->
                    if (tooltip.isNotBlank() && tooltip.length < 500) {
                        textSet.add(tooltip.trim())
                    }
                }
            }

            // Get view ID resource name (sometimes contains URLs)
            node.viewIdResourceName?.let { viewId ->
                if (viewId.isNotBlank() && viewId.length < 200) {
                    textSet.add(viewId.trim())
                }
            }

            // Recurse through all children
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        collectAllScreenContent(child, textSet)
                    }
                } catch (e: Exception) {
                    // Continue if one child fails
                    Log.d(TAG, "Error scanning child node: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.d(TAG, "Error scanning node: ${e.message}")
        }
    }

    private fun extractUrlFromText(text: String): String? {
        if (text.isBlank() || text.length > 1000) return null

        val cleanText = text.trim()

        // Method 1: Find complete URLs with protocol
        findCompleteUrl(cleanText)?.let { return it }

        // Method 2: Find domains in the text (most common case)
        findDomainInText(cleanText)?.let { return it }

        // Method 3: Check if entire text is a domain
        if (isValidDomainFormat(cleanText)) {
            val normalizedDomain = normalizeDomain(cleanText)
            if (isMatchingBlockedDomain(normalizedDomain)) {
                return "https://$normalizedDomain"
            }
        }

        return null
    }

    private fun findCompleteUrl(text: String): String? {
        // Pattern for complete URLs
        val urlPattern = Regex(
            """https?://[^\s<>"'\[\]{}|\\^`]+""",
            RegexOption.IGNORE_CASE
        )

        return urlPattern.findAll(text)
            .map { it.value.trimEnd('.', ',', '!', '?', ')', '}', ']', ':', ';', '"', '\'') }
            .firstOrNull { url ->
                try {
                    val urlObj = URL(url)
                    val domain = urlObj.host?.removePrefix("www.")?.lowercase()
                    domain != null && isMatchingBlockedDomain(domain)
                } catch (_: Exception) {
                    false
                }
            }
    }

    private fun findDomainInText(text: String): String? {
        // Multiple patterns to catch different domain formats
        val patterns = listOf(
            // Standard domain pattern
            Regex("""(?:^|\s|[^\w.-])((?:www\.)?[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*\.[a-zA-Z]{2,})(?:[^\w.-]|$)"""),
            // Domain with common separators
            Regex("""(?:^|[\s,;:()\[\]{}"'])((?:www\.)?[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})(?:[\s,;:()\[\]{}"']|$)"""),
            // Simple word boundary pattern
            Regex("""\b((?:www\.)?[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})\b""")
        )

        for (pattern in patterns) {
            pattern.findAll(text).forEach { match ->
                val domain = if (match.groupValues.size > 1) {
                    match.groupValues[1]
                } else {
                    match.value
                }.trim()

                if (isValidDomainFormat(domain)) {
                    val normalizedDomain = normalizeDomain(domain)
                    if (isMatchingBlockedDomain(normalizedDomain)) {
                        return "https://$normalizedDomain"
                    }
                }
            }
        }

        // Fallback: Split text and check each word
        val words = text.split(Regex("""[\s,;:()\[\]{}"'<>|\\^`]+"""))
        for (word in words) {
            val cleanWord = word.trim()
            if (isValidDomainFormat(cleanWord)) {
                val normalizedDomain = normalizeDomain(cleanWord)
                if (isMatchingBlockedDomain(normalizedDomain)) {
                    return "https://$normalizedDomain"
                }
            }
        }

        return null
    }

    private fun isValidDomainFormat(domain: String): Boolean {
        if (domain.isBlank() || domain.length < 4 || domain.length > 253) return false

        val cleanDomain = domain.trim()

        // Basic format checks
        if (!cleanDomain.contains(".") ||
            cleanDomain.startsWith(".") ||
            cleanDomain.endsWith(".") ||
            cleanDomain.startsWith("-") ||
            cleanDomain.endsWith("-") ||
            cleanDomain.contains("..") ||
            cleanDomain.contains(" ")
        ) {
            return false
        }

        // Count dots (reasonable subdomain levels)
        val dotCount = cleanDomain.count { it == '.' }
        if (dotCount < 1 || dotCount > 10) return false

        // Check for valid characters
        val validDomainPattern = Regex("""^[a-zA-Z0-9.-]+$""")
        if (!cleanDomain.matches(validDomainPattern)) return false

        // Check TLD (top-level domain)
        val parts = cleanDomain.split(".")
        val tld = parts.lastOrNull()
        if (tld.isNullOrEmpty() || tld.length < 2 || !tld.matches(Regex("""^[a-zA-Z]{2,}$"""))) {
            return false
        }

        // Check each part
        return parts.all { part ->
            part.isNotEmpty() &&
                    part.length <= 63 &&
                    !part.startsWith("-") &&
                    !part.endsWith("-") &&
                    part.matches(Regex("""^[a-zA-Z0-9-]+$"""))
        }
    }

    private fun normalizeDomain(domain: String): String {
        return domain.lowercase()
            .removePrefix("www.")
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .trim()
    }

    private fun isMatchingBlockedDomain(domain: String): Boolean {
        val normalizedDomain = normalizeDomain(domain)

        return blockedDomains.any { blockedDomain ->
            val normalizedBlocked = normalizeDomain(blockedDomain)

            when {
                // Exact match
                normalizedDomain == normalizedBlocked -> true

                // Subdomain match (e.g., "mail.google.com" matches "google.com")
                normalizedDomain.endsWith(".$normalizedBlocked") -> true

                // Reverse match (e.g., "google.com" matches "mail.google.com" if that's what's blocked)
                normalizedBlocked.endsWith(".$normalizedDomain") -> true

                // Partial match for very similar domains (be careful with this)
                normalizedDomain.contains(normalizedBlocked) &&
                        normalizedBlocked.length >= 5 && // Only for reasonably long domains
                        normalizedDomain.length - normalizedBlocked.length <= 10 -> true

                else -> false
            }
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
                val normalizedDomain = normalizeDomain(hostDomain)
                isMatchingBlockedDomain(normalizedDomain)
            } ?: false

        } catch (_: Exception) {
            // If URL parsing fails, try direct domain matching
            val normalizedUrl = normalizeDomain(url)
            if (isValidDomainFormat(normalizedUrl)) {
                isMatchingBlockedDomain(normalizedUrl)
            } else {
                false
            }
        }
    }
}