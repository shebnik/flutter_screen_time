import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/src/const/argument.dart';
import 'package:flutter_screen_time/src/const/method_name.dart';
import 'package:flutter_screen_time/src/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/model/android/android_permission_type.dart';
import 'package:flutter_screen_time/src/model/authorization_status.dart';
import 'package:flutter_screen_time/src/model/ios/family_activity_selection.dart';
import 'package:flutter_screen_time/src/model/ios/plugin_configuration.dart';

class MethodChannelFlutterScreenTime extends FlutterScreenTimePlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_screen_time');

  @override
  Future<PluginConfiguration> configure({
    String? logFilePath,
  }) async {
    try {
      final arguments = <String, dynamic>{};

      if (logFilePath != null) {
        arguments[Argument.logFilePath] = logFilePath;
      }

      final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
        MethodName.configure,
        arguments.isNotEmpty ? arguments : null,
      );

      return PluginConfiguration.fromMap(
        Map<String, dynamic>.from(result ?? {}),
      );
    } catch (e) {
      debugPrint('Error in configure: $e');
      return PluginConfiguration(error: e.toString());
    }
  }

  @override
  Future<bool> requestPermission({
    AndroidPermissionType? permissionType,
  }) async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.requestPermission,
          {
            Argument.permissionType: permissionType?.name,
          },
        ) ??
        false;
  }

  @override
  Future<AuthorizationStatus> authorizationStatus({
    AndroidPermissionType? androidPermissionType,
  }) async {
    final status =
        await methodChannel.invokeMethod<String>(
          MethodName.authorizationStatus,
          {
            Argument.permissionType: androidPermissionType?.name,
          },
        ) ??
        AuthorizationStatus.notDetermined.name;
    return AuthorizationStatus.values.byName(status);
  }

  @override
  Future<bool> blockApps({
    FamilyActivitySelection? iOSSelection,
    List<String>? androidBundleIds,
    String? androidLayoutName,
    String? androidNotificationTitle,
    String? androidNotificationBody,
  }) async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.blockApps,
          {
            Argument.selection: iOSSelection?.toMap(),
            Argument.bundleIds: androidBundleIds,
            Argument.blockOverlayLayoutName: androidLayoutName,
            Argument.notificationTitle: androidNotificationTitle,
            Argument.notificationBody: androidNotificationBody,
          },
        ) ??
        false;
  }

  @override
  Future<bool> blockWebDomains({
    required List<String> webDomains,
    bool isAdultWebsitesBlocked = false,
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.blockWebDomains,
          {
            Argument.blockedWebDomains: webDomains,
            Argument.isAdultWebsitesBlocked: isAdultWebsitesBlocked,
            Argument.blockOverlayLayoutName: layoutName,
            Argument.notificationTitle: notificationTitle,
            Argument.notificationBody: notificationBody,
          },
        ) ??
        false;
  }

  @override
  Future<bool> disableAppsBlocking() async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.disableAppsBlocking,
        ) ??
        false;
  }

  @override
  Future<bool> disableWebDomainsBlocking() async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.disableWebDomainsBlocking,
        ) ??
        false;
  }

  @override
  Future<bool> disableAllBlocking() async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.disableAllBlocking,
        ) ??
        false;
  }
}
