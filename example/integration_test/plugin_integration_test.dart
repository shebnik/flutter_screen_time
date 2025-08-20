import 'package:flutter_screen_time/flutter_screen_time.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('getPlatformVersion test', (WidgetTester tester) async {
    final plugin = FlutterScreenTime();
    final version = await plugin.getPlatformVersion();
    expect(version?.isNotEmpty, true);
  });
}
