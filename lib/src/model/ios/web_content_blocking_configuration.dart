import 'package:flutter/foundation.dart';

/// Configuration model for web content blocking settings
@immutable
class WebContentBlockingConfiguration {
  /// Creates a web content blocking configuration
  const WebContentBlockingConfiguration({
    required this.isAdultWebsitesBlocked,
    this.blockedWebDomains = const [],
  });

  /// Create from a map returned by the platform
  factory WebContentBlockingConfiguration.fromMap(Map<String, dynamic> map) {
    return WebContentBlockingConfiguration(
      isAdultWebsitesBlocked: (map['isAdultWebsitesBlocked'] as bool?) ?? false,
      blockedWebDomains:
          (map['blockedWebDomains'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
    );
  }

  /// Whether adult websites filtering is blocked
  final bool isAdultWebsitesBlocked;

  /// List of specifically blocked domains
  final List<String> blockedWebDomains;

  /// Convert to map for platform communication
  Map<String, dynamic> toMap() {
    return {
      'isAdultWebsitesBlocked': isAdultWebsitesBlocked,
      'blockedWebDomains': blockedWebDomains,
    };
  }

  /// Create a copy with optional parameter overrides
  WebContentBlockingConfiguration copyWith({
    bool? isAdultWebsitesBlocked,
    List<String>? blockedWebDomains,
  }) {
    return WebContentBlockingConfiguration(
      isAdultWebsitesBlocked:
          isAdultWebsitesBlocked ?? this.isAdultWebsitesBlocked,
      blockedWebDomains: blockedWebDomains ?? this.blockedWebDomains,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is WebContentBlockingConfiguration &&
        other.isAdultWebsitesBlocked == isAdultWebsitesBlocked &&
        listEquals(other.blockedWebDomains, blockedWebDomains);
  }

  @override
  int get hashCode {
    return isAdultWebsitesBlocked.hashCode ^ blockedWebDomains.hashCode;
  }

  @override
  String toString() {
    return 'WebContentBlockingConfiguration(isAdultWebsitesBlocked: '
        '$isAdultWebsitesBlocked, blockedWebDomains: $blockedWebDomains)';
  }
}
