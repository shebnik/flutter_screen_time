import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/flutter_screen_time.dart';

void main() {
  runApp(const MyApp());
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

  bool isLoading = true;

  @override
  void initState() {
    super.initState();
    determinePermissions();
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

    if (!mounted) return;

    setState(() {
      isLoading = false;
    });
  }

  Future<void> requestPermission(PermissionType type) async {
    final result = await _flutterScreenTimePlugin.requestPermission(
      permissionType: type,
    );
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
              ),
      ),
    );
  }
}
