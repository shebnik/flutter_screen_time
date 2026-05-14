import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/flutter_screen_time.dart';

class AndroidScreenTimePermissions {
  final api = FlutterScreenTime();

  Future<bool> requestPermission(AndroidPermissionType type) async {
    try {
      return await api.requestPermission(
        permissionType: type,
        isOnlyWebsitesBlocking: false,
      );
    } on PlatformException catch (e) {
      debugPrintStack(
        label: 'Error requesting permission for $type. $e',
        stackTrace: StackTrace.current,
      );
      return false;
    }
  }

  Future<Map<AndroidPermissionType, bool>> checkPermissions() async {
    final permissions = {
      AndroidPermissionType.appUsage,
      AndroidPermissionType.accessibilitySettings,
      AndroidPermissionType.notification,
      AndroidPermissionType.vpn,
      AndroidPermissionType.drawOverlay,
    };

    final results = <AndroidPermissionType, bool>{};

    for (final type in permissions) {
      try {
        final status = await api.authorizationStatus(
          androidPermissionType: type,
          isOnlyWebsitesBlocking: false,
        );
        results[type] = status == AuthorizationStatus.approved;
      } on PlatformException catch (e) {
        debugPrintStack(
          label: 'Error checking permission for $type. $e',
          stackTrace: StackTrace.current,
        );
        results[type] = false;
      }
    }

    return results;
  }

  bool hasAllPermissions(
    Map<AndroidPermissionType, bool>? permissions,
  ) => permissions?.values.every((status) => status) ?? false;

  Future<bool> hasPermissions() async {
    final statuses = await checkPermissions();
    return hasAllPermissions(statuses);
  }
}
