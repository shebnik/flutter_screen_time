import 'dart:convert';

import 'package:flutter/foundation.dart';

import 'package:flutter_screen_time/src/model/android/app_category.dart';

class BaseInstalledApp {
  BaseInstalledApp({
    required this.status,
    required this.data,
    this.error,
  });

  factory BaseInstalledApp.fromJson(Map<String, dynamic> json) =>
      BaseInstalledApp(
        status: json['status'] as bool,
        data: List<InstalledApp>.from(
          (json['data'] as Iterable).map(
            (x) => InstalledApp.fromJson(x as Map<String, dynamic>),
          ),
        ),
        error: json['error'] as String?,
      );

  final bool status;
  final List<InstalledApp> data;
  final String? error;

  Map<String, dynamic> toJson() => {
    'status': status,
    'data': data.map((x) => x.toJson()).toList(),
    'error': error,
  };
}

class InstalledApp {
  const InstalledApp({
    this.appName,
    this.packageName,
    this.enabled = false,
    this.category = AppCategory.other,
    this.versionName,
    this.versionCode,
    this.iconInBytes,
  });

  factory InstalledApp.fromJson(Map<String, dynamic> json) => InstalledApp(
    appName: json['appName'] as String?,
    packageName: json['packageName'] as String?,
    enabled: json['enabled'] as bool,
    category: AppCategory.values.firstWhere(
      (element) => element.name == json['category'],
      orElse: () => AppCategory.other,
    ),
    versionName: json['versionName'] as String?,
    versionCode: json['versionCode'] as int?,
    iconInBytes: (json['appIcon'] != null)
        ? base64Decode((json['appIcon'] as String).replaceAll('\n', ''))
        : null,
  );

  final String? appName;
  final String? packageName;
  final bool enabled;
  final AppCategory category;
  final String? versionName;
  final int? versionCode;
  final Uint8List? iconInBytes;

  Map<String, dynamic> toJson() => {
    'appName': appName,
    'packageName': packageName,
    'enabled': enabled,
    'category': category.name,
    'versionName': versionName,
    'versionCode': versionCode,
    'appIcon': iconInBytes,
  };
}
