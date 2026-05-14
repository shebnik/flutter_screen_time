import 'package:flutter/material.dart';
import 'package:flutter_screen_time/flutter_screen_time.dart';
import 'package:flutter_screen_time_example/android_permissions/permission_card.dart';
import 'package:flutter_screen_time_example/android_screen_time_permissions.dart';

class AndroidPermissionsSheet extends StatefulWidget {
  const AndroidPermissionsSheet({
    required this.permissions,
    super.key,
  });

  final Map<AndroidPermissionType, bool> permissions;

  @override
  State<AndroidPermissionsSheet> createState() =>
      _AndroidPermissionsSheetState();
}

class _AndroidPermissionsSheetState extends State<AndroidPermissionsSheet> {
  final androidPermissions = AndroidScreenTimePermissions();
  Map<AndroidPermissionType, bool> permissions = {};
  Map<AndroidPermissionType, String> permissionName = {};

  int grantedCount = 0;

  @override
  void initState() {
    permissions = widget.permissions;
    grantedCount = permissions.values.where((granted) => granted).length;
    super.initState();
  }

  Future<void> requestPermission(AndroidPermissionType type) async {
    final granted = await androidPermissions.requestPermission(type);
    debugPrint('Permission $type granted: $granted');
    if (!mounted) return;
    setState(() {
      permissions[type] = granted;
      grantedCount = permissions.values.where((granted) => granted).length;
    });
    if (grantedCount == permissions.length) {
      Navigator.of(context).pop(permissions);
    }
  }

  @override
  Widget build(BuildContext context) {
    permissionName = {
      AndroidPermissionType.appUsage: 'Usage Access',
      AndroidPermissionType.accessibilitySettings: 'Accessibility',
      AndroidPermissionType.notification: 'Notifications',
      AndroidPermissionType.vpn: 'VPN',
      AndroidPermissionType.drawOverlay: 'Appear on Top',
    };
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.symmetric(vertical: 25, horizontal: 42),
        children: [
          Text(
            'Allow Permissions',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          Text(
            'To use all features of the app, please allow the following permissions.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          ...permissions.entries.map((entry) {
            final type = entry.key;
            final granted = entry.value;
            return Padding(
              padding: const EdgeInsets.only(top: 6),
              child: PermissionCard(
                name: permissionName[type]!,
                value: granted,
                onTap: granted ? null : () async => requestPermission(type),
              ),
            );
          }),
        ],
      ),
    );
  }
}
