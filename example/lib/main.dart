import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/flutter_screen_time.dart';

void main() {
  runZonedGuarded(
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

  final Map<AndroidPermissionType, AuthorizationStatus> _permissionStatus =
      Map.fromEntries(
        AndroidPermissionType.values.map(
          (type) => MapEntry(type, AuthorizationStatus.notDetermined),
        ),
      );

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
  bool isBlockingApps = false;
  bool isBlockingWebDomains = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    await determinePermissions();
    await loadAppsList();

    if (mounted) setState(() => isLoading = false);
  }

  Future<void> determinePermissions() async {
    for (final type in AndroidPermissionType.values) {
      try {
        _permissionStatus[type] = await _flutterScreenTimePlugin
            .authorizationStatus(
              permissionType: type,
            );
      } on PlatformException catch (e) {
        debugPrintStack(
          label: e.toString(),
          stackTrace: StackTrace.current,
        );
      }
    }
  }

  Future<void> loadAppsList() async {
    try {
      final apps = await _flutterScreenTimePlugin.installedApps();
      debugPrint('Installed apps: ${apps.length}');
      categorizedApps = _flutterScreenTimePlugin.categorizeApps(apps);
      categories = categorizedApps.keys.toList()
        ..sort((a, b) => a.name.compareTo(b.name));
    } on PlatformException catch (e) {
      debugPrintStack(
        label: e.toString(),
        stackTrace: StackTrace.current,
      );
    }
  }

  Future<void> requestPermission(AndroidPermissionType type) async {
    bool result;

    try {
      result = await _flutterScreenTimePlugin.requestPermission(
        permissionType: type,
      );
    } on PlatformException catch (e) {
      debugPrintStack(
        label: e.toString(),
        stackTrace: StackTrace.current,
      );
      return;
    }

    debugPrint(
      'Permission for ${type.name} was ${result ? 'granted' : 'denied'}',
    );

    if (mounted) {
      setState(() {
        _permissionStatus[type] = result
            ? AuthorizationStatus.approved
            : AuthorizationStatus.denied;
      });
    }
  }

  Future<bool> checkAppBlockingPermissions() async {
    for (final type
        in _permissionStatus.entries
            .where(
              (e) => [
                AndroidPermissionType.appUsage,
                AndroidPermissionType.drawOverlay,
                AndroidPermissionType.notification,
              ].contains(e.key),
            )
            .where((e) => e.value != AuthorizationStatus.approved)) {
      await requestPermission(type.key);
    }

    return _permissionStatus.entries
        .where(
          (e) => [
            AndroidPermissionType.appUsage,
            AndroidPermissionType.drawOverlay,
            AndroidPermissionType.notification,
          ].contains(e.key),
        )
        .every(
          (e) => e.value == AuthorizationStatus.approved,
        );
  }

  Future<bool> checkDomainBlockingPermissions() async {
    debugPrint('Current permissions: $_permissionStatus');
    for (final type in _permissionStatus.entries.where(
      (e) => e.value != AuthorizationStatus.approved,
    )) {
      debugPrint('Requesting permission for ${type.key}');
      await requestPermission(type.key);
      debugPrint(
        'Permission for ${type.key.name} is now ${_permissionStatus[type.key]}',
      );
    }

    return _permissionStatus.values.every(
      (e) => e == AuthorizationStatus.approved,
    );
  }

  Future<void> blockApps({required bool? value}) async {
    if (value == null) return;

    if (!(await checkAppBlockingPermissions())) {
      return;
    }

    final bundleIds = selectedApps.values
        .expand((e) => e)
        .map((e) => e.packageName)
        .whereType<String>()
        .toList();

    if (isBlockingApps) {
      await _flutterScreenTimePlugin.disableAppsBlocking();
    } else {
      await _flutterScreenTimePlugin.blockApps(
        bundleIds: bundleIds,
      );
    }

    if (mounted) setState(() => isBlockingApps = value);
  }

  Future<void> blockWebDomains({required bool? value}) async {
    if (value == null) return;

    if (!(await checkDomainBlockingPermissions())) {
      return;
    }
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
                      _currentPermissionStatus(),
                      _blockApps(),
                      _blockWebsites(),
                    ],
                  ),
                ),
              ),
      ),
    );
  }

  Widget _currentPermissionStatus() {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Permissions:',
              style: Theme.of(context).textTheme.headlineLarge,
            ),
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: determinePermissions,
            ),
          ],
        ),
        const SizedBox(height: 8),
        ..._permissionStatus.entries.map((entry) {
          return ListTile(
            title: Text(entry.key.name),
            subtitle: Text(entry.value.name),
            trailing: entry.value != AuthorizationStatus.approved
                ? ElevatedButton(
                    onPressed: () => requestPermission(entry.key),
                    child: const Text('Request'),
                  )
                : null,
          );
        }),
      ],
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
