import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/model/android/android_permission_type.dart';
import 'package:flutter_screen_time/src/model/android/app_category.dart';
import 'package:flutter_screen_time/src/model/android/installed_app.dart';
import 'package:flutter_screen_time/src/model/authorization_status.dart';
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

  Future<AuthorizationStatus> authorizationStatus({
    AndroidPermissionType permissionType = AndroidPermissionType.appUsage,
  }) {
    return FlutterScreenTimePlatform.instance.authorizationStatus(
      permissionType: permissionType,
    );
  }

  Future<bool> requestPermission({
    AndroidPermissionType permissionType = AndroidPermissionType.appUsage,
  }) {
    return FlutterScreenTimePlatform.instance.requestPermission(
      permissionType: permissionType,
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

  Future<bool> blockApps({
    List<String> bundleIds = const <String>[],
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) {
    return FlutterScreenTimePlatform.instance.blockAndroidApps(
      bundleIds: bundleIds,
      layoutName: layoutName,
      notificationTitle: notificationTitle,
      notificationBody: notificationBody,
    );
  }

  Future<bool> stopBlockingApps() {
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

  Future<bool> stopBlockingWebDomains() {
    return FlutterScreenTimePlatform.instance.stopBlockingWebDomains();
  }

  Future<bool> updateBlockedWebDomains(List<String> webDomains) {
    return FlutterScreenTimePlatform.instance.updateBlockedWebDomains(
      webDomains,
    );
  }
}
