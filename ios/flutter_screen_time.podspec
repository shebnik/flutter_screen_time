#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint flutter_screen_time.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'flutter_screen_time'
  s.version          = '0.0.1'
  s.summary          = 'A Flutter plugin for Screen Time API.'
  s.description      = <<-DESC
A Flutter plugin that provides access to Screen Time and Family Controls APIs on iOS.
                       DESC
  s.homepage         = 'http://example.com'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'flutter_screen_time/Sources/flutter_screen_time/**/*.swift'
  s.dependency 'Flutter'
  s.platform = :ios, '15.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # If your plugin requires a privacy manifest, for example if it uses any
  # required reason APIs, update the PrivacyInfo.xcprivacy file to describe your
  # plugin's privacy impact, and then uncomment this line. For more information,
  # see https://developer.apple.com/documentation/bundleresources/privacy_manifest_files
  # s.resource_bundles = {'flutter_screen_time_privacy' => ['flutter_screen_time/Sources/flutter_screen_time/PrivacyInfo.xcprivacy']}
end
