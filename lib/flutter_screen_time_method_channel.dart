import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/const/argument.dart';
import 'package:flutter_screen_time/src/const/method_name.dart';
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
}
