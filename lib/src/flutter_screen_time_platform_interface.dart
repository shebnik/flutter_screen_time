import 'package:flutter_screen_time/flutter_screen_time.dart';
import 'package:flutter_screen_time/src/flutter_screen_time_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

abstract class FlutterScreenTimePlatform extends PlatformInterface {
  FlutterScreenTimePlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterScreenTimePlatform _instance = MethodChannelFlutterScreenTime();

  static FlutterScreenTimePlatform get instance => _instance;

  static set instance(FlutterScreenTimePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<PluginConfiguration> configure({
    String? logFilePath,
  }) {
    throw UnimplementedError('configure() has not been implemented.');
  }

  Future<bool> requestPermission({
    AndroidPermissionType? permissionType,
    bool? isOnlyWebsitesBlocking,
  }) =>
      throw UnimplementedError('requestPermission() has not been implemented.');

  Future<AuthorizationStatus> authorizationStatus({
    AndroidPermissionType? androidPermissionType,
    bool? isOnlyWebsitesBlocking,
  }) => throw UnimplementedError(
    'authorizationStatus() has not been implemented.',
  );

  Future<bool> hasAutoStartPermission() => throw UnimplementedError(
    'hasAutoStartPermission() has not been implemented.',
  );

  Future<bool> blockApps({
    FamilyActivitySelection? iOSSelection,
    List<String>? androidBundleIds,
    String? androidLayoutName,
    String? androidNotificationTitle,
    String? androidNotificationBody,
    String? androidNotificationIcon,
    String? androidNotificationGroupIcon,
    bool? androidUseOverlayCountdown,
    int? androidOverlayCountdownSeconds,
  }) => throw UnimplementedError('blockApps() has not been implemented.');

  Future<bool> blockWebDomains({
    required List<String> webDomains,
    bool isAdultWebsitesBlocked = false,
    String? androidNotificationTitle,
    String? androidNotificationBody,
    String? androidNotificationIcon,
    String? androidNotificationGroupIcon,
    bool blockWebsitesOnlyInBrowsers = true,
    String? androidLayoutName,
    bool? androidUseOverlayCountdown,
    int? androidOverlayCountdownSeconds,
  }) => throw UnimplementedError('blockWebDomains() has not been implemented.');

  Future<bool> disableAppsBlocking() => throw UnimplementedError(
    'disableAppsBlocking() has not been implemented.',
  );

  Future<bool> disableWebDomainsBlocking() => throw UnimplementedError(
    'disableWebDomainsBlocking() has not been implemented.',
  );

  Future<bool> disableAllBlocking() => throw UnimplementedError(
    'disableAllBlocking() has not been implemented.',
  );

  Future<List<InstalledApp>> getAndroidInstalledApps({
    bool ignoreSystemApps = true,
    List<String>? bundleIds,
  }) => throw UnimplementedError('installedApps() has not been implemented.');

  Future<FamilyActivitySelection?> showFamilyActivityPicker({
    FamilyPickerConfiguration? familyPickerConfiguration,
    FamilyActivitySelection? selection,
  }) => throw UnimplementedError(
    'showFamilyActivityPicker() has not been implemented.',
  );

  Future<bool> unblockApps(FamilyActivitySelection selection) =>
      throw UnimplementedError(
        'unblockApps() has not been implemented.',
      );

  Future<FamilyActivitySelection> getBlockedApps() => throw UnimplementedError(
    'getBlockedApps() has not been implemented.',
  );

  Future<bool> setAdultWebsitesBlocking({required bool isEnabled}) =>
      throw UnimplementedError(
        'setAdultWebsitesBlocking() has not been implemented.',
      );

  Future<bool> isAdultWebsitesBlocked() => throw UnimplementedError(
    'isAdultWebsitesBlocked() has not been implemented.',
  );

  Future<WebContentBlockingConfiguration?> getWebContentBlocking() =>
      throw UnimplementedError(
        'getWebContentBlocking() has not been implemented.',
      );

  Future<bool> blockAppsAndWebDomains({
    FamilyActivitySelection? iOSSelection,
    List<String>? androidBundleIds,
    List<String>? webDomains,
    bool isAdultWebsitesBlocked = false,
    String? androidNotificationTitle,
    String? androidNotificationBody,
    String? androidNotificationIcon,
    String? androidLayoutName,
    bool? androidUseOverlayCountdown,
    int? androidOverlayCountdownSeconds,
    bool? androidUseDnsWebsiteBlocking,
    String? androidForwardDnsServer,
    List<String>? androidUninstallPreventionKeywords,
    String? appName,
  }) => throw UnimplementedError(
    'blockAppsAndWebDomains() has not been implemented.',
  );

  Future<bool> disableAppsAndWebDomainsBlocking() => throw UnimplementedError(
    'disableAppsAndWebDomainsBlocking() has not been implemented.',
  );
}
