import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/model/android/android_permission_type.dart';
import 'package:flutter_screen_time/src/model/android/app_category.dart';
import 'package:flutter_screen_time/src/model/android/installed_app.dart';
import 'package:flutter_screen_time/src/model/authorization_status.dart';
import 'package:flutter_screen_time/src/model/ios/family_activity_selection.dart';
import 'package:flutter_screen_time/src/model/ios/plugin_configuration.dart';

export 'package:flutter_screen_time/src/model/android/android_permission_type.dart';
export 'package:flutter_screen_time/src/model/android/app_category.dart';
export 'package:flutter_screen_time/src/model/android/installed_app.dart';
export 'package:flutter_screen_time/src/model/authorization_status.dart';

class FlutterScreenTime {
  // Static configuration state
  static PluginConfiguration? _globalConfiguration;
  static bool _isConfigured = false;

  /// Configure the app's internal settings needed for Screen Time APIs
  ///
  /// This is a static method that configures the plugin globally.
  /// All instances of FlutterScreenTime will use this configuration.
  ///
  /// [logFilePath] specifies where to store plugin logs (optional)
  ///
  /// Returns a [PluginConfiguration] object indicating success or failure
  /// with relevant configuration details
  static Future<PluginConfiguration> configure({
    String? logFilePath,
  }) async {
    _globalConfiguration = await FlutterScreenTimePlatform.instance.configure(
      logFilePath: logFilePath,
    );
    _isConfigured = true;
    return _globalConfiguration!;
  }

  /// Get the current global configuration if available
  static PluginConfiguration? get globalConfiguration => _globalConfiguration;

  /// Check if the plugin has been configured globally
  static bool get isConfigured => _isConfigured;

  /// On iOS requests permission for Screen Time API.
  ///
  /// On Android, it requests permission for [AndroidPermissionType].
  ///
  /// Returns true if permission is granted, false otherwise.
  Future<bool> requestPermission({
    AndroidPermissionType? permissionType,
  }) {
    return FlutterScreenTimePlatform.instance.requestPermission(
      permissionType: permissionType,
    );
  }

  /// On iOS, it checks the authorization status for Screen Time API.
  ///
  /// On Android, it checks the authorization status for 
  /// [AndroidPermissionType].
  ///
  /// Returns the current [AuthorizationStatus].
  Future<AuthorizationStatus> authorizationStatus({
    AndroidPermissionType? permissionType,
  }) {
    return FlutterScreenTimePlatform.instance.authorizationStatus(
      permissionType: permissionType,
    );
  }

  /// Block specific apps indefinitely
  ///
  /// On iOS, [iOSSelection] is family activity selection, could be retrieved by
  /// calling method `getFamilyActivitySelection`.
  ///
  /// On Android, [androidBundleIds] specifies the list of app bundle IDs to 
  /// block.
  /// [androidLayoutName] Custom layout for android overlay.
  /// [androidNotificationTitle] Custom title for the android notification.
  /// [androidNotificationBody] Custom body for the android notification.
  Future<bool> blockApps({
    FamilyActivitySelection? iOSSelection,
    List<String>? androidBundleIds,
    String? androidLayoutName,
    String? androidNotificationTitle,
    String? androidNotificationBody,
  }) {
    return FlutterScreenTimePlatform.instance.blockApps(
      androidBundleIds: androidBundleIds,
      iOSSelection: iOSSelection,
      androidLayoutName: androidLayoutName,
      androidNotificationTitle: androidNotificationTitle,
      androidNotificationBody: androidNotificationBody,
    );
  }

  Future<List<InstalledApp>> installedApps({
    bool ignoreSystemApps = true,
  }) {
    return FlutterScreenTimePlatform.instance.getAndroidInstalledApps(
      ignoreSystemApps: ignoreSystemApps,
    );
  }

  Map<AppCategory, List<InstalledApp>> categorizeApps(List<InstalledApp> apps) {
    final categorized = <AppCategory, List<InstalledApp>>{};
    for (final app in apps) {
      categorized.putIfAbsent(app.category, () => []).add(app);
    }
    return categorized;
  }

  Future<bool> disableAppsBlocking() {
    return FlutterScreenTimePlatform.instance.stopBlockingAndroidApps();
  }

  /// Blocks the specified web domains.
  ///
  /// [webDomains] The list of web domains to block.
  ///
  /// Android specific parameters:
  /// [layoutName] Custom layout for android overlay.
  /// [notificationTitle] Custom title for the android notification.
  /// [notificationBody] Custom body for the android notification.
  Future<bool> blockWebDomains({
    required List<String> webDomains,
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) {
    return FlutterScreenTimePlatform.instance.blockWebDomains(
      webDomains: webDomains,
      layoutName: layoutName,
      notificationTitle: notificationTitle,
      notificationBody: notificationBody,
    );
  }

  Future<bool> disableWebDomainsBlocking() {
    return FlutterScreenTimePlatform.instance.disableWebDomainsBlocking();
  }

  Future<bool> updateBlockedWebDomains(List<String> webDomains) {
    return FlutterScreenTimePlatform.instance.updateBlockedWebDomains(
      webDomains,
    );
  }
}
