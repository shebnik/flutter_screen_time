import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';

class FlutterScreenTime {
  Future<String?> getPlatformVersion() {
    return FlutterScreenTimePlatform.instance.getPlatformVersion();
  }
}
