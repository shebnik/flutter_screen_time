import 'package:flutter/foundation.dart';

/// Configuration model for web content blocking settings
@immutable
class WebContentBlockingConfiguration {
  /// Creates a web content blocking configuration
  const WebContentBlockingConfiguration({
    required this.isAdultContentBlocked,
    this.blockedWebDomains = const [],
  });

  /// Create from a map returned by the platform
  factory WebContentBlockingConfiguration.fromMap(Map<String, dynamic> map) {
    return WebContentBlockingConfiguration(
      isAdultContentBlocked: (map['isAdultContentBlocked'] as bool?) ?? false,
      blockedWebDomains:
          (map['blockedWebDomains'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
    );
  }

  /// Whether adult content filtering is blocked
  final bool isAdultContentBlocked;

  /// List of specifically blocked domains
  final List<String> blockedWebDomains;

  /// Convert to map for platform communication
  Map<String, dynamic> toMap() {
    return {
      'isAdultContentBlocked': isAdultContentBlocked,
      'blockedWebDomains': blockedWebDomains,
    };
  }

  /// Create a copy with optional parameter overrides
  WebContentBlockingConfiguration copyWith({
    bool? isAdultContentBlocked,
    List<String>? blockedWebDomains,
  }) {
    return WebContentBlockingConfiguration(
      isAdultContentBlocked:
          isAdultContentBlocked ?? this.isAdultContentBlocked,
      blockedWebDomains: blockedWebDomains ?? this.blockedWebDomains,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is WebContentBlockingConfiguration &&
        other.isAdultContentBlocked == isAdultContentBlocked &&
        listEquals(other.blockedWebDomains, blockedWebDomains);
  }

  @override
  int get hashCode {
    return isAdultContentBlocked.hashCode ^ blockedWebDomains.hashCode;
  }

  @override
  String toString() {
    return 'WebContentBlockingConfiguration(isAdultContentBlocked: '
    '$isAdultContentBlocked, blockedWebDomains: $blockedWebDomains)';
  }
}
