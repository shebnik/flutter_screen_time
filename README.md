# flutter_screen_time


Add to your manifest ``<application>`` tag whichever you'll use:
```xml
<!-- Websites Blocking Accessibility Service -->
<service
    android:name="com.shebnik.flutter_screen_time.service.WebsitesBlockingAccessibilityService"
    android:label="Website Domain Blocking Service"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```
Or
```xml
<!-- Apps and Websites Blocking Accessibility Service -->
<service
    android:name="com.shebnik.flutter_screen_time.service.BlockingService"
    android:label="Apps and Website Domain Blocking Service"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>

<!-- If using VPN blocking -->
<service
    android:name="com.shebnik.flutter_screen_time.service.BlockingVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

BlockerVpnService scheduleRestart sends ``BLOCKER_VPN_STOPPED`` event, own app VpnMonitorReceiver implementation
```xml
<!-- VPN Monitor Receiver -->
<receiver
    android:name=".receiver.VpnMonitorReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.PACKAGE_ADDED" />
        <action android:name="android.intent.action.PACKAGE_REMOVED" />
        <action android:name="android.intent.action.PACKAGE_REPLACED" />
        <data android:scheme="package" />
    </intent-filter>
    <intent-filter>
        <action android:name="BLOCKER_VPN_STOPPED" />
    </intent-filter>
</receiver>
```