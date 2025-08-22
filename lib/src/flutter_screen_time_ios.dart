import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screen_time/src/const/argument.dart';
import 'package:flutter_screen_time/src/const/method_name.dart';
import 'package:flutter_screen_time/src/flutter_screen_time_platform_interface.dart';
import 'package:flutter_screen_time/src/model/ios/family_activity_selection.dart';
import 'package:flutter_screen_time/src/model/ios/family_picker_configuration.dart';
import 'package:flutter_screen_time/src/model/ios/web_content_blocking_configuration.dart';

class FlutterScreenTimeIos extends FlutterScreenTimePlatform {
  static const methodChannel = MethodChannel('flutter_screen_time');

  @override
  Future<FamilyActivitySelection?> showFamilyActivityPicker({
    FamilyPickerConfiguration? familyPickerConfiguration,
    FamilyActivitySelection? selection,
  }) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>?>(
      MethodName.showFamilyActivityPicker,
      {
        Argument.familyPickerConfiguration: familyPickerConfiguration?.toMap(),
        Argument.selection: selection?.toMap(),
      },
    );

    if (result == null) {
      return null;
    }

    return FamilyActivitySelection.fromMap(Map<String, dynamic>.from(result));
  }

  @override
  Future<bool> unblockApps(FamilyActivitySelection selection) async {
    final result = await methodChannel.invokeMethod<bool>(
      MethodName.unblockApps,
      {
        Argument.selection: selection.toMap(),
      },
    );
    return result ?? false;
  }

  @override
  Future<FamilyActivitySelection> getBlockedApps() async {
    try {
      final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
        MethodName.getBlockedApps,
      );
      if (result != null) {
        final convertedMap = _convertToStringDynamicMap(result);
        return FamilyActivitySelection.fromMap(convertedMap);
      }
      return FamilyActivitySelection.empty();
    } catch (e) {
      debugPrint('Error in getBlockedApps: $e');
      return FamilyActivitySelection.empty();
    }
  }

  @override
  Future<bool> setAdultWebsitesBlocking({required bool isEnabled}) async {
    final result = await methodChannel.invokeMethod<bool?>(
      MethodName.setAdultWebsitesBlocking,
      {
        Argument.isEnabled: isEnabled,
      },
    );
    return result ?? false;
  }

  @override
  Future<bool> isAdultWebsitesBlocked() async {
    final result = await methodChannel.invokeMethod<bool>(
      MethodName.isAdultWebsitesBlocked,
    );
    return result ?? false;
  }

  @override
  Future<WebContentBlockingConfiguration?> getWebContentBlocking() async {
    try {
      final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
        MethodName.getWebContentBlocking,
      );
      if (result != null) {
        return WebContentBlockingConfiguration.fromMap(
          _convertToStringDynamicMap(result),
        );
      }
      return null;
    } catch (e) {
      debugPrint('Error in getWebContentBlocking: $e');
      return null;
    }
  }

  /// Helper method to safely convert nested Map objects
  Map<String, dynamic> _convertToStringDynamicMap(Map<Object?, Object?> map) {
    final result = <String, dynamic>{};

    map.forEach((key, value) {
      final stringKey = key.toString();

      if (value is Map<Object?, Object?>) {
        result[stringKey] = _convertToStringDynamicMap(value);
      } else if (value is List) {
        result[stringKey] = value.map((item) {
          if (item is Map<Object?, Object?>) {
            return _convertToStringDynamicMap(item);
          }
          return item;
        }).toList();
      } else {
        result[stringKey] = value;
      }
    });

    return result;
  }
}
