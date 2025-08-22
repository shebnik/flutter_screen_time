import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/const/argument.dart';
import 'package:flutter_screen_time/src/const/method_name.dart';
import 'package:flutter_screen_time/src/model/android/android_permission_type.dart';
import 'package:flutter_screen_time/src/model/android/installed_app.dart';
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
    AndroidPermissionType? permissionType,
  }) async {
    final status =
        await methodChannel.invokeMethod<String>(
          MethodName.authorizationStatus,
          {
            Argument.permissionType: permissionType?.name,
          },
        ) ??
        AuthorizationStatus.notDetermined.name;
    return AuthorizationStatus.values.byName(status);
  }

  @override
  Future<bool> blockApps({
    List<String>? androidBundleIds,
    FamilyActivitySelection? iOSSelection,
    String? androidLayoutName,
    String? androidNotificationTitle,
    String? androidNotificationBody,
  }) async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.blockApps,
          {
            Argument.bundleIds: androidBundleIds,
            Argument.selection: iOSSelection?.toMap(),
            Argument.blockOverlayLayoutName: androidLayoutName,
            Argument.notificationTitle: androidNotificationTitle,
            Argument.notificationBody: androidNotificationBody,
          },
        ) ??
        false;
  }

  @override
  Future<List<InstalledApp>> getAndroidInstalledApps({
    bool ignoreSystemApps = true,
  }) async {
    final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
      MethodName.installedApps,
      {
        Argument.ignoreSystemApps: ignoreSystemApps,
      },
    );

    return Isolate.run(() async {
      final map = await _convertToStringDynamicMap(result);
      final response = BaseInstalledApp.fromJson(map);
      if (response.status) {
        return response.data;
      } else {
        debugPrint(map.toString());
        return <InstalledApp>[];
      }
    });
  }

  @override
  Future<bool> stopBlockingAndroidApps() async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.disableAppsBlocking,
        ) ??
        false;
  }

  @override
  Future<bool> blockWebDomains({
    required List<String> webDomains,
    bool isAdultContentBlocked = false,
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.blockWebDomains,
          {
            Argument.blockedWebDomains: webDomains,
            Argument.isAdultContentBlocked: isAdultContentBlocked,
            Argument.blockOverlayLayoutName: layoutName,
            Argument.notificationTitle: notificationTitle,
            Argument.notificationBody: notificationBody,
          },
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

  Future<Map<String, dynamic>> _convertToStringDynamicMap(
    Map<Object?, Object?>? result,
  ) async {
    if (result == null) {
      final error = result?['error'];
      throw Exception(error);
    }

    return Isolate.run(() {
      final convertedMap = <String, dynamic>{};

      result.forEach((key, value) {
        if (key is String) {
          if (value is Map) {
            // Recursively convert nested maps
            convertedMap[key] = _convertNestedMap(value);
          } else if (value is List) {
            // Convert lists
            convertedMap[key] = _convertList(value);
          } else {
            // Direct assignment for primitive types
            convertedMap[key] = value;
          }
        }
      });

      return convertedMap;
    });
  }

  dynamic _convertNestedMap(Map<dynamic, dynamic> map) {
    final convertedMap = <String, dynamic>{};

    map.forEach((key, value) {
      if (key is String) {
        if (value is Map) {
          convertedMap[key] = _convertNestedMap(value);
        } else if (value is List) {
          convertedMap[key] = _convertList(value);
        } else {
          convertedMap[key] = value;
        }
      }
    });

    return convertedMap;
  }

  List<dynamic> _convertList(List<dynamic> list) {
    return list.map((item) {
      if (item is Map) {
        return _convertNestedMap(item);
      } else if (item is List) {
        return _convertList(item);
      } else {
        return item;
      }
    }).toList();
  }
}
