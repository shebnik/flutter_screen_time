import 'package:flutter_screen_time/src/flutter_screen_time_method_channel.dart';
import 'package:flutter_screen_time/src/flutter_screen_time_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final initialPlatform = FlutterScreenTimePlatform.instance;

  test('$MethodChannelFlutterScreenTime is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterScreenTime>());
  });
}
