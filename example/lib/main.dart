import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/flutter_screen_time.dart';
import 'package:flutter_screen_time_example/android_permissions/android_permissions_sheet.dart';
import 'package:flutter_screen_time_example/android_screen_time_permissions.dart';

void main() {
  return runZonedGuarded(
    () async {
      WidgetsFlutterBinding.ensureInitialized();

      FlutterError.onError = FlutterError.dumpErrorToConsole;
      PlatformDispatcher.instance.onError = (error, stackTrace) {
        debugPrint('Platform error: $error');
        debugPrint('StackTrace: $stackTrace');
        return true;
      };

      runApp(const MyApp());
    },
    (error, stackTrace) {
      debugPrint('Error: $error');
      debugPrint('StackTrace: $stackTrace');
    },
  );
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _flutterScreenTimePlugin = FlutterScreenTime();

  Map<AndroidPermissionType, bool>? _androidPermissionStatus;
  final androidPermissions = AndroidScreenTimePermissions();

  AuthorizationStatus? iOSStatus;

  Map<AppCategory, List<InstalledApp>> categorizedApps = {};
  List<AppCategory> categories = [];
  Map<AppCategory, Set<InstalledApp>> selectedApps = {};

  List<String> selectedWebDomains = [
    'facebook.com',
    'twitter.com',
    'instagram.com',
    'tiktok.com',
  ];

  bool isLoading = true;
  bool hasValidPermissions = false;
  bool isBlockingApps = false;
  bool isBlockingWebDomains = false;

  @override
  void initState() {
    super.initState();
    unawaited(checkAuthorizationStatus());
  }

  Future<void> checkAuthorizationStatus() async {
    try {
      if (defaultTargetPlatform == TargetPlatform.android) {
        _androidPermissionStatus = await androidPermissions.checkPermissions();
        debugPrint('Android permissions status: $_androidPermissionStatus');
        if (!androidPermissions.hasAllPermissions(_androidPermissionStatus)) {
          return requestAuthorization();
        } else {
          hasValidPermissions = true;
        }
      } else {
        iOSStatus = await _flutterScreenTimePlugin.authorizationStatus();
        debugPrint('iOS authorization status: $iOSStatus');
        if (iOSStatus != AuthorizationStatus.approved) {
          return requestAuthorization();
        } else {
          hasValidPermissions = true;
        }
      }
      if (!mounted) return;
      if (hasValidPermissions) {
        await load();
        if (mounted) {
          setState(() => isLoading = false);
        }
      }
    } catch (e, s) {
      debugPrintStack(
        label: 'Failed to check authorization status: $e',
        stackTrace: s,
      );
      if (mounted) {
        setState(() => isLoading = false);
      }
    }
  }

  Future<void> requestAuthorization() async {
    try {
      if (defaultTargetPlatform == TargetPlatform.android) {
        _androidPermissionStatus = await requestAndroidPermissions();
        debugPrint('Android permissions status: $_androidPermissionStatus');
      } else {
        final result = await _flutterScreenTimePlugin.requestPermission();
        iOSStatus = result
            ? AuthorizationStatus.approved
            : AuthorizationStatus.denied;
        debugPrint('iOS authorization status: $iOSStatus');
      }
      if (mounted) {
        if (defaultTargetPlatform == TargetPlatform.iOS &&
                iOSStatus == AuthorizationStatus.approved ||
            defaultTargetPlatform == TargetPlatform.android &&
                androidPermissions.hasAllPermissions(
                  _androidPermissionStatus,
                )) {
          hasValidPermissions = true;
          await load();
        }
      }
    } catch (e, s) {
      debugPrintStack(
        label: 'Failed to request authorization. $e',
        stackTrace: s,
      );
    } finally {
      debugPrint('requestAuthorization Setting loading to false');
      if (mounted) {
        setState(() => isLoading = false);
      }
    }
  }

  Future<Map<AndroidPermissionType, bool>?> requestAndroidPermissions() async {
    final perms =
        _androidPermissionStatus ?? await androidPermissions.checkPermissions();
    if (!mounted) return perms;
    return showModalBottomSheet<Map<AndroidPermissionType, bool>?>(
      context: context,
      isScrollControlled: true,
      isDismissible: false,
      builder: (_) => PopScope(
        canPop: false,
        child: AndroidPermissionsSheet(
          permissions: perms,
        ),
      ),
    );
  }

  Future<void> load() async {
    await loadAppsList();

    if (mounted) setState(() => isLoading = false);
  }

  Future<void> loadAppsList() async {
    try {
      final apps = await _flutterScreenTimePlugin.getAndroidInstalledApps();
      debugPrint('Installed apps: ${apps.length}');
      categorizedApps = _flutterScreenTimePlugin.categorizeAndroidApps(apps);
      categories = categorizedApps.keys.toList()
        ..sort((a, b) => a.name.compareTo(b.name));
    } on PlatformException catch (e) {
      debugPrintStack(
        label: e.toString(),
        stackTrace: StackTrace.current,
      );
    }
  }

  Future<void> blockApps({required bool? value}) async {
    if (value == null) return;

    final bundleIds = selectedApps.values
        .expand((e) => e)
        .map((e) => e.packageName)
        .whereType<String>()
        .toList();

    if (isBlockingApps) {
      await _flutterScreenTimePlugin.disableAppsBlocking();
    } else {
      await _flutterScreenTimePlugin.blockApps(
        androidBundleIds: bundleIds,
      );
    }

    if (mounted) setState(() => isBlockingApps = value);
  }

  Future<void> blockWebDomains({required bool? value}) async {
    if (value == null) return;

    if (isBlockingWebDomains) {
      await _flutterScreenTimePlugin.disableWebDomainsBlocking();
    } else {
      await _flutterScreenTimePlugin.blockWebDomains(
        webDomains: selectedWebDomains,
      );
    }

    if (mounted) setState(() => isBlockingWebDomains = value);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('FlutterScreenTime Plugin Example'),
        ),
        body: isLoading
            ? const Center(child: CircularProgressIndicator())
            : SingleChildScrollView(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    spacing: 16,
                    children: [
                      _blockApps(),
                      _blockWebsites(),
                    ],
                  ),
                ),
              ),
      ),
    );
  }

  Widget _blockApps() {
    return Column(
      children: [
        Text('Block Apps', style: Theme.of(context).textTheme.headlineSmall),
        _categorizedAppsList(),
        _blockAppsToggle(),
      ],
    );
  }

  Widget _categorizedAppsList() {
    return ListView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: categories.length,
      itemBuilder: (context, index) {
        final category = categories[index];
        final appsInCategory = categorizedApps[category]!;

        return ExpansionTile(
          title: Text(category.name),
          subtitle: Text('${appsInCategory.length} apps'),
          children: appsInCategory
              .map(
                (app) => CheckboxListTile(
                  controlAffinity: ListTileControlAffinity.leading,
                  value: selectedApps[category]?.contains(app) ?? false,
                  onChanged: (isSelected) {
                    setState(() {
                      if (true == isSelected) {
                        selectedApps.putIfAbsent(
                          category,
                          () => <InstalledApp>{},
                        );
                        selectedApps[category]!.add(app);
                      } else {
                        selectedApps[category]?.remove(app);
                      }
                    });
                  },
                  title: Row(
                    spacing: 8,
                    children: [
                      if (app.iconInBytes != null)
                        Image.memory(
                          app.iconInBytes!,
                          width: 50,
                          height: 50,
                        ),
                      Flexible(child: Text(app.appName ?? 'App')),
                    ],
                  ),
                ),
              )
              .toList(),
        );
      },
    );
  }

  Widget _blockAppsToggle() {
    return CheckboxListTile(
      title: const Text('Block Selected Apps'),
      value: isBlockingApps,
      onChanged: (value) => blockApps(value: value),
    );
  }

  Widget _blockWebsites() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Block Web Domains',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        TextField(
          decoration: const InputDecoration(
            labelText: 'Add Web Domain',
            border: OutlineInputBorder(),
          ),
          onSubmitted: (value) {
            setState(() {
              selectedWebDomains.add(value);
            });
          },
        ),
        const SizedBox(height: 8),
        ...selectedWebDomains.map((website) {
          return ListTile(
            title: Text(website),
            trailing: IconButton(
              icon: const Icon(Icons.remove),
              onPressed: () {
                setState(() {
                  selectedWebDomains.remove(website);
                });
              },
            ),
          );
        }),
        const SizedBox(height: 16),
        CheckboxListTile(
          value: isBlockingWebDomains,
          onChanged: (value) => blockWebDomains(value: value),
          title: const Text('Block Web Domains'),
        ),
      ],
    );
  }
}
