import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/src/const/argument.dart';
import 'package:flutter_screen_time/src/const/method_name.dart';
import 'package:flutter_screen_time/src/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/model/android/installed_app.dart';

class FlutterScreenTimeAndroid extends FlutterScreenTimePlatform {
  static const methodChannel = MethodChannel('flutter_screen_time');

  @override
  Future<bool> blockAppsAndWebDomains({
    required List<String>? androidBundleIds,
    required List<String> webDomains,
    bool blockWebsitesOnlyInBrowsers = true,
    String? androidNotificationTitle,
    String? androidNotificationBody,
    String? androidNotificationIcon,
    String? androidNotificationGroupIcon,
    String? androidLayoutName,
    bool? androidUseOverlayCountdown,
    int? androidOverlayCountdownSeconds,
  }) async {
    final result =
        await methodChannel.invokeMethod<bool>(
          MethodName.blockAppsAndWebDomains,
          {
            Argument.bundleIds: androidBundleIds,
            Argument.blockedWebDomains: webDomains,
            Argument.notificationTitle: androidNotificationTitle,
            Argument.notificationBody: androidNotificationBody,
            Argument.notificationIcon: androidNotificationIcon,
            Argument.notificationGroupIcon: androidNotificationGroupIcon,
            Argument.blockWebsitesOnlyInBrowsers: blockWebsitesOnlyInBrowsers,
            Argument.blockOverlayLayoutName: androidLayoutName,
            Argument.useOverlayCountdown: androidUseOverlayCountdown,
            Argument.overlayCountdownSeconds: androidOverlayCountdownSeconds,
          },
        ) ??
        false;

    return result;
  }

  @override
  Future<bool> disableAppsAndWebDomainsBlocking() async {
    final result = await methodChannel.invokeMethod<bool>(
      MethodName.disableAppsAndWebDomainsBlocking,
    );
    return result ?? false;
  }

  @override
  Future<List<InstalledApp>> getAndroidInstalledApps({
    bool ignoreSystemApps = true,
    List<String>? bundleIds,
  }) async {
    final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
      MethodName.installedApps,
      {
        Argument.ignoreSystemApps: ignoreSystemApps,
        Argument.bundleIds: bundleIds,
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
