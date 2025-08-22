import 'package:flutter_screen_time/src/flutter_screen_time_android.dart';
import 'package:flutter_screen_time/src/flutter_screen_time_ios.dart';
import 'package:flutter_screen_time/src/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/model/android/android_permission_type.dart';
import 'package:flutter_screen_time/src/model/android/app_category.dart';
import 'package:flutter_screen_time/src/model/android/installed_app.dart';
import 'package:flutter_screen_time/src/model/authorization_status.dart';
import 'package:flutter_screen_time/src/model/ios/family_activity_selection.dart';
import 'package:flutter_screen_time/src/model/ios/family_picker_configuration.dart';
import 'package:flutter_screen_time/src/model/ios/plugin_configuration.dart';
import 'package:flutter_screen_time/src/model/ios/web_content_blocking_configuration.dart';

export 'package:flutter_screen_time/src/model/model.dart';
export 'package:flutter_screen_time/src/widgets/widgets.dart';

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
    AndroidPermissionType? androidPermissionType,
  }) {
    return FlutterScreenTimePlatform.instance.authorizationStatus(
      androidPermissionType: androidPermissionType,
    );
  }

  /// Block specific apps indefinitely.
  ///
  /// On iOS [iOSSelection] is family activity selection containing encoded
  /// tokens, which could be retrieved after calling [showFamilyActivityPicker].
  /// [iOSSelection] contains applicationTokens, categoryTokens, webDomainTokens
  ///
  /// On Android [androidBundleIds] specifies the list of app bundle IDs to
  /// block.
  ///
  /// [androidLayoutName] Custom layout for android blocking overlay.
  ///
  /// [androidNotificationTitle] Custom title for the android notification.
  ///
  /// [androidNotificationBody] Custom body for the android notification.
  Future<bool> blockApps({
    FamilyActivitySelection? iOSSelection,
    List<String>? androidBundleIds,
    String? androidLayoutName,
    String? androidNotificationTitle,
    String? androidNotificationBody,
  }) {
    return FlutterScreenTimePlatform.instance.blockApps(
      iOSSelection: iOSSelection,
      androidBundleIds: androidBundleIds,
      androidLayoutName: androidLayoutName,
      androidNotificationTitle: androidNotificationTitle,
      androidNotificationBody: androidNotificationBody,
    );
  }

  /// Blocks the specified web domains indefinitely.
  ///
  /// [webDomains] The list of web domains to block, 50 is the maximum for iOS.
  ///
  /// On iOS [isAdultWebsitesBlocked] specifies whether to block adult websites.
  ///
  /// Android specific parameters:
  ///
  /// [layoutName] Custom layout for android blocking overlay.
  ///
  /// [notificationTitle] Custom title for the android notification.
  ///
  /// [notificationBody] Custom body for the android notification.
  Future<bool> blockWebDomains({
    required List<String> webDomains,
    bool isAdultWebsitesBlocked = false,
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

  /// Disables all app blocking completely.
  /// Returns true if successful, false otherwise.
  Future<bool> disableAppsBlocking() {
    return FlutterScreenTimePlatform.instance.disableAppsBlocking();
  }

  /// Disables all web domain blocking completely.
  /// Returns true if successful, false otherwise.
  Future<bool> disableWebDomainsBlocking() {
    return FlutterScreenTimePlatform.instance.disableWebDomainsBlocking();
  }

  /// Disables both app and web domain blocking completely.
  /// Returns true if successful, false otherwise.
  Future<bool> disableAllBlocking() async {
    return FlutterScreenTimePlatform.instance.disableAllBlocking();
  }

  /// Android only
  ///
  /// Retrieves a list of installed apps on the Android device.
  ///
  /// [ignoreSystemApps] determines whether to include system apps in the
  /// result.
  ///
  /// [bundleIds] is an optional list of bundle IDs to filter the results.
  ///
  /// Returns a list of [InstalledApp].
  Future<List<InstalledApp>> getAndroidInstalledApps({
    bool ignoreSystemApps = true,
    List<String>? bundleIds,
  }) {
    return FlutterScreenTimeAndroid().getAndroidInstalledApps(
      ignoreSystemApps: ignoreSystemApps,
      bundleIds: bundleIds,
    );
  }

  /// Categorizes a list of Android [InstalledApp] by their [AppCategory].
  Map<AppCategory, List<InstalledApp>> categorizeAndroidApps(
    List<InstalledApp> apps,
  ) {
    final categorized = <AppCategory, List<InstalledApp>>{};
    for (final app in apps) {
      categorized.putIfAbsent(app.category, () => []).add(app);
    }
    return categorized;
  }

  /// iOS only
  ///
  /// Show the native iOS family activity picker to let users select
  /// apps/categories to restrict.
  ///
  /// [familyPickerConfiguration] can be used to customize the appearance of
  /// the picker
  /// [selection] allows showing the picker with pre-selected apps/categories
  ///
  /// Returns the selected apps/categories if user saves, null if user cancels
  /// or dismisses.
  /// If user taps reset, it only clears the current selection without closing
  /// the picker.
  ///
  /// Example:
  /// ```dart
  /// final selection = await flutterScreenTime.showFamilyActivityPicker();
  /// if (selection != null) {
  ///   // User saved a selection
  ///   await flutterScreenTime.blockApps(iOSSelection: selection);
  /// } else {
  ///   // User cancelled or dismissed
  /// }
  /// ```
  Future<FamilyActivitySelection?> showFamilyActivityPicker({
    FamilyPickerConfiguration? familyPickerConfiguration,
    FamilyActivitySelection? selection,
  }) {
    return FlutterScreenTimeIos().showFamilyActivityPicker(
      familyPickerConfiguration: familyPickerConfiguration,
      selection: selection,
    );
  }

  /// iOS only
  ///
  /// Remove restrictions for specific apps/categories
  /// [selection] contains the apps/categories to remove restrictions from
  ///
  /// This allows you to selectively remove restrictions without affecting other
  /// blocked apps. Use this when you want to unblock specific apps while
  /// keeping restrictions on others.
  ///
  /// Example:
  /// ```dart
  /// // Remove restrictions only for selected apps
  /// await flutterScreenTime.unblockApps(selectedApps);
  /// ```
  Future<bool> unblockApps(FamilyActivitySelection selection) {
    return FlutterScreenTimeIos().unblockApps(selection);
  }

  /// iOS only
  ///
  /// Get the list of currently blocked apps/categories
  /// Returns a FamilyActivitySelection with separated token types
  Future<FamilyActivitySelection> getBlockedApps() async {
    return FlutterScreenTimeIos().getBlockedApps();
  }

  /// iOS only
  ///
  /// Set the adult websites blocking
  ///
  /// Set [isEnabled] true to enable blocking, false to disable blocking
  Future<bool> setAdultWebsitesBlocking({required bool isEnabled}) {
    return FlutterScreenTimeIos().setAdultWebsitesBlocking(
      isEnabled: isEnabled,
    );
  }

  /// iOS only
  ///
  /// Get the current adult websites blocking status
  ///
  /// Returns true if adult websites are blocked, false otherwise.
  Future<bool> isAdultWebsitesBlocked() {
    return FlutterScreenTimeIos().isAdultWebsitesBlocked();
  }

  /// iOS only
  ///
  /// Returns a current [WebContentBlockingConfiguration] containing:
  /// - isAdultWebsitesBlocked: bool
  /// - blockedWebDomains: List[String]
  Future<WebContentBlockingConfiguration?> getWebContentBlocking() {
    return FlutterScreenTimeIos().getWebContentBlocking();
  }
}
