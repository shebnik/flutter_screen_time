import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/const/argument.dart';
import 'package:flutter_screen_time/src/const/method_name.dart';
import 'package:flutter_screen_time/src/model/installed_app.dart';
import 'package:flutter_screen_time/src/model/permission_status.dart';
import 'package:flutter_screen_time/src/model/permission_type.dart';

class MethodChannelFlutterScreenTime extends FlutterScreenTimePlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_screen_time');

  @override
  Future<PermissionStatus> permissionStatus({
    PermissionType permissionType = PermissionType.appUsage,
  }) async {
    final status =
        await methodChannel.invokeMethod<String>(MethodName.permissionStatus, {
          Argument.permissionType: permissionType.name,
        }) ??
        PermissionStatus.notDetermined.name;
    return PermissionStatus.values.byName(status);
  }

  @override
  Future<bool> requestPermission({
    PermissionType permissionType = PermissionType.appUsage,
  }) async {
    return await methodChannel.invokeMethod<bool>(
          MethodName.requestPermission,
          {
            Argument.permissionType: permissionType.name,
          },
        ) ??
        false;
  }

  @override
  Future<List<InstalledApp>> installedApps({
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
