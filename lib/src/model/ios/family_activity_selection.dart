import 'package:flutter/foundation.dart';

/// Represents a collection of selected family activities including apps, 
/// categories, and web domains
@immutable
class FamilyActivitySelection {
  const FamilyActivitySelection({
    required this.applicationTokens,
    required this.categoryTokens,
    required this.webDomainTokens,
  });

  /// Create a FamilyActivitySelection from a map
  factory FamilyActivitySelection.fromMap(Map<String, dynamic> map) {
    return FamilyActivitySelection(
      applicationTokens:
          (map['applicationTokens'] as List<dynamic>?)?.cast<String>() ?? [],
      categoryTokens:
          (map['categoryTokens'] as List<dynamic>?)?.cast<String>() ?? [],
      webDomainTokens:
          (map['webDomainTokens'] as List<dynamic>?)?.cast<String>() ?? [],
    );
  }

  /// Create an empty selection
  factory FamilyActivitySelection.empty() {
    return const FamilyActivitySelection(
      applicationTokens: [],
      categoryTokens: [],
      webDomainTokens: [],
    );
  }

  /// Tokens representing applications selected by the user
  final List<String> applicationTokens;

  /// Tokens representing categories selected by the user
  final List<String> categoryTokens;

  /// Tokens representing web domains selected by the user
  final List<String> webDomainTokens;

  /// Convert the selection to a map
  Map<String, dynamic> toMap() {
    final map = <String, dynamic>{
      'applicationTokens': applicationTokens,
      'categoryTokens': categoryTokens,
      'webDomainTokens': webDomainTokens,
    };
    return map;
  }

  /// Check if the selection is empty (no tokens of any type)
  bool get isEmpty =>
      applicationTokens.isEmpty &&
      categoryTokens.isEmpty &&
      webDomainTokens.isEmpty;

  /// Get the total count of all tokens
  int get totalCount =>
      applicationTokens.length + categoryTokens.length + webDomainTokens.length;

  /// Check if the selection has any tokens
  bool get isNotEmpty => !isEmpty;

  /// Get the total number of tokens across all types
  int get totalTokenCount =>
      applicationTokens.length + categoryTokens.length + webDomainTokens.length;

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is FamilyActivitySelection &&
        _listEquals(other.applicationTokens, applicationTokens) &&
        _listEquals(other.categoryTokens, categoryTokens) &&
        _listEquals(other.webDomainTokens, webDomainTokens);
  }

  @override
  int get hashCode {
    return Object.hash(
      Object.hashAll(applicationTokens),
      Object.hashAll(categoryTokens),
      Object.hashAll(webDomainTokens),
    );
  }

  @override
  String toString() {
    return 'FamilyActivitySelection(apps: ${applicationTokens.length}, '
        'categories: ${categoryTokens.length}, '
        'webDomains: ${webDomainTokens.length})';
  }

  /// Helper method to compare lists
  bool _listEquals<T>(List<T> a, List<T> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }
}
