/// Represents the possible authorization statuses for Screen Time API access
enum AuthorizationStatus {
  /// The user has not yet been asked for Screen Time authorization
  notDetermined('notDetermined'),

  /// The user has denied Screen Time authorization
  denied('denied'),

  /// The user has granted Screen Time authorization
  approved('approved');

  const AuthorizationStatus(this.value);

  /// The string value of the authorization status
  final String value;

  /// Create an AuthorizationStatus from a string value
  static AuthorizationStatus fromString(String value) {
    return values.firstWhere(
      (status) => status.value == value,
      orElse: () => AuthorizationStatus.notDetermined,
    );
  }

  /// Whether the user is authorized to use Screen Time features
  bool get isAuthorized => this == AuthorizationStatus.approved;

  /// Whether authorization can be requested (not denied or already authorized)
  bool get canRequestAuthorization => this == AuthorizationStatus.notDetermined;
}
