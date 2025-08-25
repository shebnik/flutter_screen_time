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
```