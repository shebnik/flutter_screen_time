import 'package:flutter_screen_time/flutter_screen_time_method_channel.dart';
import 'package:flutter_screen_time/src/model/installed_app.dart';
import 'package:flutter_screen_time/src/model/permission_status.dart';
import 'package:flutter_screen_time/src/model/permission_type.dart';
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

  Future<PermissionStatus> permissionStatus({
    PermissionType permissionType = PermissionType.appUsage,
  }) =>
      throw UnimplementedError('permissionStatus() has not been implemented.');

  Future<bool> requestPermission({
    PermissionType permissionType = PermissionType.appUsage,
  }) =>
      throw UnimplementedError('requestPermission() has not been implemented.');

  Future<List<InstalledApp>> installedApps({
    bool ignoreSystemApps = true,
  }) => throw UnimplementedError('installedApps() has not been implemented.');

  Future<bool> blockApps({
    List<String> bundleIds = const <String>[],
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) => throw UnimplementedError('blockApps() has not been implemented.');

  Future<bool> stopBlockingApps() =>
      throw UnimplementedError('stopBlockingApps() has not been implemented.');

  Future<bool> unblockApps({
    List<String> bundleIds = const <String>[],
  }) => throw UnimplementedError('unblockApps() has not been implemented.');
}
