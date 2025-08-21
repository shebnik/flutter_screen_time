import 'package:flutter_screen_time/flutter_screen_time.dart';
import 'package:flutter_screen_time/flutter_screen_time_method_channel.dart';
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

  /// Configure the plugin with various settings
  /// [logFilePath] - Optional path for log files
  /// Returns a map with configuration results
  Future<PluginConfiguration> configure({
    String? logFilePath,
  }) {
    throw UnimplementedError('configure() has not been implemented.');
  }

  Future<AuthorizationStatus> authorizationStatus({
    AndroidPermissionType permissionType = AndroidPermissionType.appUsage,
  }) => throw UnimplementedError(
    'authorizationStatus() has not been implemented.',
  );

  Future<bool> requestPermission({
    AndroidPermissionType permissionType = AndroidPermissionType.appUsage,
  }) =>
      throw UnimplementedError('requestPermission() has not been implemented.');

  Future<List<InstalledApp>> getAndroidInstalledApps({
    bool ignoreSystemApps = true,
  }) => throw UnimplementedError('installedApps() has not been implemented.');

  Future<bool> blockAndroidApps({
    List<String> bundleIds = const <String>[],
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) => throw UnimplementedError('blockApps() has not been implemented.');

  Future<bool> stopBlockingAndroidApps() =>
      throw UnimplementedError('stopBlockingApps() has not been implemented.');

  Future<bool> blockWebDomains({
    required List<String> webDomains,
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) => throw UnimplementedError('blockWebDomains() has not been implemented.');

  Future<bool> stopBlockingWebDomains() => throw UnimplementedError(
    'stopBlockingWebDomains() has not been implemented.',
  );

  Future<bool> updateBlockedWebDomains(List<String> webDomains) =>
      throw UnimplementedError(
        'updateBlockedWebDomains() has not been implemented.',
      );
}
