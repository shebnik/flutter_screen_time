import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_screen_time_platform_interface.dart';

/// An implementation of [FlutterScreenTimePlatform] that uses method channels.
class MethodChannelFlutterScreenTime extends FlutterScreenTimePlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_screen_time');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
