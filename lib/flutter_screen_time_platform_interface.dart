import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_screen_time_method_channel.dart';

abstract class FlutterScreenTimePlatform extends PlatformInterface {
  /// Constructs a FlutterScreenTimePlatform.
  FlutterScreenTimePlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterScreenTimePlatform _instance = MethodChannelFlutterScreenTime();

  /// The default instance of [FlutterScreenTimePlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterScreenTime].
  static FlutterScreenTimePlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterScreenTimePlatform] when
  /// they register themselves.
  static set instance(FlutterScreenTimePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
