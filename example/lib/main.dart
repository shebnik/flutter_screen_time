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

  final Map<PermissionType, PermissionStatus> _permissionStatus =
      Map.fromEntries(
        PermissionType.values.map(
          (type) => MapEntry(type, PermissionStatus.notDetermined),
        ),
      );

  Map<AppCategory, List<InstalledApp>> categorizedApps = {};
  List<AppCategory> categories = [];
  Map<AppCategory, Set<InstalledApp>> selectedApps = {};

  bool isLoading = true;
  bool isBlockingApps = false;

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
    for (final type in PermissionType.values) {
      try {
        _permissionStatus[type] = await _flutterScreenTimePlugin
            .permissionStatus(
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

  Future<void> requestPermission(PermissionType type) async {
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
            ? PermissionStatus.approved
            : PermissionStatus.denied;
      });
    }
  }

  Future<bool> checkPermissions() async {
    if (_permissionStatus.values.any(
      (status) => status != PermissionStatus.approved,
    )) {
      for (final type in PermissionType.values.where(
        (type) => _permissionStatus[type] != PermissionStatus.approved,
      )) {
        await requestPermission(type);
      }
    }

    return _permissionStatus.values.every(
      (status) => status == PermissionStatus.approved,
    );
  }

  //
  // ignore: avoid_positional_boolean_parameters
  Future<void> blockApps(bool? value) async {
    if (value == null) return;

    if (!(await checkPermissions())) {
      return;
    }

    final bundleIds = selectedApps.values
        .expand((e) => e)
        .map((e) => e.packageName)
        .whereType<String>()
        .toList();

    if (isBlockingApps) {
      await _flutterScreenTimePlugin.stopBlockingApps();
    } else {
      await _flutterScreenTimePlugin.blockApps(
        bundleIds: bundleIds,
      );
    }

    if (mounted) setState(() => isBlockingApps = value);
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
            : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _currentPermissionStatus(),
                  const SizedBox(height: 16),
                  _categorizedAppsList(),
                  const SizedBox(height: 16),
                  _blockApps(),
                  const SizedBox(height: 16),
                ],
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
            trailing: entry.value != PermissionStatus.approved
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

  Widget _blockApps() {
    return CheckboxListTile(
      title: const Text('Block Selected Apps'),
      value: isBlockingApps,
      onChanged: blockApps,
    );
  }
}
