import 'package:flutter_screen_time/flutter_screen_time.dart';
import 'package:flutter_screen_time/flutter_screen_time_method_channel.dart';
import 'package:flutter_screen_time/src/model/ios/family_activity_selection.dart';
import 'package:flutter_screen_time/src/model/ios/plugin_configuration.dart';
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
  }) =>
      throw UnimplementedError('requestPermission() has not been implemented.');

  Future<AuthorizationStatus> authorizationStatus({
    AndroidPermissionType? permissionType,
  }) => throw UnimplementedError(
    'authorizationStatus() has not been implemented.',
  );

  Future<bool> blockApps({
    List<String>? androidBundleIds,
    FamilyActivitySelection? iOSSelection,
    String? androidLayoutName,
    String? androidNotificationTitle,
    String? androidNotificationBody,
  }) => throw UnimplementedError('blockApps() has not been implemented.');

  Future<bool> stopBlockingAndroidApps() => throw UnimplementedError(
    'disableAppsBlocking() has not been implemented.',
  );

  Future<List<InstalledApp>> getAndroidInstalledApps({
    bool ignoreSystemApps = true,
  }) => throw UnimplementedError('installedApps() has not been implemented.');

  Future<bool> blockWebDomains({
    required List<String> webDomains,
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) => throw UnimplementedError('blockWebDomains() has not been implemented.');

  Future<bool> disableWebDomainsBlocking() => throw UnimplementedError(
    'disableWebDomainsBlocking() has not been implemented.',
  );

  Future<bool> updateBlockedWebDomains(List<String> webDomains) =>
      throw UnimplementedError(
        'updateBlockedWebDomains() has not been implemented.',
      );
}
