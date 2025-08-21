/// Token types for Family Controls
enum TokenType {
  application('application'),
  category('category'),
  webDomain('webDomain');

  const TokenType(this.value);
  final String value;

  static TokenType fromString(String value) {
    switch (value) {
      case 'application':
        return TokenType.application;
      case 'category':
        return TokenType.category;
      case 'webDomain':
        return TokenType.webDomain;
      default:
        throw ArgumentError('Unknown token type: $value');
    }
  }

  @override
  String toString() => value;
}
