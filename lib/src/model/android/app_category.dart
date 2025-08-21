enum AppCategory {
  game('Game'),
  audio('Audio'),
  video('Video'),
  image('Image'),
  social('Social'),
  news('News'),
  maps('Maps'),
  productivity('Productivity'),
  other('Other');

  const AppCategory(this.name);

  final String name;
}
