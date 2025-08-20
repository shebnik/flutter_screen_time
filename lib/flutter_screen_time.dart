import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/model/app_category.dart';
import 'package:flutter_screen_time/src/model/installed_app.dart';
import 'package:flutter_screen_time/src/model/permission_status.dart';
import 'package:flutter_screen_time/src/model/permission_type.dart';

export 'package:flutter_screen_time/src/model/app_category.dart';
export 'package:flutter_screen_time/src/model/installed_app.dart';
export 'package:flutter_screen_time/src/model/permission_status.dart';
export 'package:flutter_screen_time/src/model/permission_type.dart';

class FlutterScreenTime {
  Future<PermissionStatus> permissionStatus({
    PermissionType permissionType = PermissionType.appUsage,
  }) {
    return FlutterScreenTimePlatform.instance.permissionStatus(
      permissionType: permissionType,
    );
  }

  Future<bool> requestPermission({
    PermissionType permissionType = PermissionType.appUsage,
  }) {
    return FlutterScreenTimePlatform.instance.requestPermission(
      permissionType: permissionType,
    );
  }

  Future<List<InstalledApp>> installedApps({
    bool ignoreSystemApps = true,
  }) {
    return FlutterScreenTimePlatform.instance.installedApps(
      ignoreSystemApps: ignoreSystemApps,
    );
  }

  Map<AppCategory, List<InstalledApp>> categorizeApps(List<InstalledApp> apps) {
    final categorized = <AppCategory, List<InstalledApp>>{};
    for (final app in apps) {
      categorized.putIfAbsent(app.category, () => []).add(app);
    }
    return categorized;
  }

  Future<bool> blockApps({
    List<String> bundleIds = const <String>[],
    String? layoutName,
    String? notificationTitle,
    String? notificationBody,
  }) {
    return FlutterScreenTimePlatform.instance.blockApps(
      bundleIds: bundleIds,
      layoutName: layoutName,
      notificationTitle: notificationTitle,
      notificationBody: notificationBody,
    );
  }

  Future<bool> stopBlockingApps() {
    return FlutterScreenTimePlatform.instance.stopBlockingApps();
  }

  Future<bool> unblockApps({
    List<String> bundleIds = const <String>[],
  }) async {
    return FlutterScreenTimePlatform.instance.unblockApps(
      bundleIds: bundleIds,
    );
  }
}
