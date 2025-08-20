import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_screen_time/flutter_screen_time.dart';
import 'package:flutter_screen_time/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/flutter_screen_time_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterScreenTimePlatform
    with MockPlatformInterfaceMixin
    implements FlutterScreenTimePlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FlutterScreenTimePlatform initialPlatform = FlutterScreenTimePlatform.instance;

  test('$MethodChannelFlutterScreenTime is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterScreenTime>());
  });

  test('getPlatformVersion', () async {
    FlutterScreenTime flutterScreenTimePlugin = FlutterScreenTime();
    MockFlutterScreenTimePlatform fakePlatform = MockFlutterScreenTimePlatform();
    FlutterScreenTimePlatform.instance = fakePlatform;

    expect(await flutterScreenTimePlugin.getPlatformVersion(), '42');
  });
}
