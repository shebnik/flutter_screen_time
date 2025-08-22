import 'package:flutter_screen_time/src/model/authorization_status.dart';

/// Response object for authorization operations
class AuthorizationResponse {
  /// Creates an authorization response
  const AuthorizationResponse({
    required this.status,
    this.error,
  });

  /// Create from a map returned by the platform
  factory AuthorizationResponse.fromMap(Map<String, dynamic> map) {
    return AuthorizationResponse(
      status: AuthorizationStatus.fromString(
        (map['status'] ?? 'unknown') as String,
      ),
      error: map['error'] as String?,
    );
  }

  /// The authorization status
  final AuthorizationStatus status;

  /// Error message if authorization failed
  final String? error;

  /// Whether the authorization was successful
  bool get isSuccess => status.isAuthorized && error == null;

  /// Whether authorization failed
  bool get isFailure => !isSuccess;

  @override
  String toString() {
    return 'AuthorizationResponse(status: ${status.value}, error: $error)';
  }
}
