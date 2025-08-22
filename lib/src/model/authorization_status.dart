/// Represents the possible authorization statuses
enum AuthorizationStatus {
  notDetermined('notDetermined'),
  denied('denied'),
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

  /// Whether the user is authorized
  bool get isAuthorized => this == AuthorizationStatus.approved;

  /// Whether authorization can be requested (not denied or already authorized)
  bool get canRequestAuthorization => this == AuthorizationStatus.notDetermined;
}
