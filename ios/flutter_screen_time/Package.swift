// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "flutter_screen_time",
    platforms: [
        .iOS("15.0")
    ],
    products: [
        .library(name: "flutter-screen-time", targets: ["flutter_screen_time"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "flutter_screen_time",
            dependencies: []
        )
    ]
)
