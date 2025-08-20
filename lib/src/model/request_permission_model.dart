class RequestPermissionModel {
  const RequestPermissionModel({
    this.status = false,
    this.error,
  });

  factory RequestPermissionModel.fromJson(Map<String, dynamic> json) =>
      RequestPermissionModel(
        status: (json['status'] as bool?) ?? false,
        error: json['error'] as String?,
      );

  final bool status;
  final String? error;

  Map<String, dynamic> toJson() => {
    'status': status,
    'error': error,
  };
}
