//
//  FamilyPickerConfiguration.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

import SwiftUI

struct FamilyPickerConfiguration {
    // Text Configuration
    let navigationTitle: String
    let saveButtonText: String
    let appsCountText: String
    let websitesCountText: String
    let categoriesCountText: String
    
    // Color Configuration
    let saveButtonColor: Color
    let saveButtonTextColor: Color
    let countTextColor: Color
    let navigationTintColor: Color?
    let darkTextColor: Color
    
    // Font Configuration
    let navigationTitleFont: Font?
    let saveButtonFont: Font
    let countTextFont: Font
    
    // Default initializer
    init(
        navigationTitle: String,
        saveButtonText: String,
        appsCountText: String,
        websitesCountText: String,
        categoriesCountText: String,
        saveButtonColor: Color,
        saveButtonTextColor: Color,
        countTextColor: Color,
        navigationTintColor: Color?,
        darkTextColor: Color,
        navigationTitleFont: Font?,
        saveButtonFont: Font,
        countTextFont: Font
    ) {
        self.navigationTitle = navigationTitle
        self.saveButtonText = saveButtonText
        self.appsCountText = appsCountText
        self.websitesCountText = websitesCountText
        self.categoriesCountText = categoriesCountText
        self.saveButtonColor = saveButtonColor
        self.saveButtonTextColor = saveButtonTextColor
        self.countTextColor = countTextColor
        self.navigationTintColor = navigationTintColor
        self.darkTextColor = darkTextColor
        self.navigationTitleFont = navigationTitleFont
        self.saveButtonFont = saveButtonFont
        self.countTextFont = countTextFont
    }
    
    // Default Configuration
    static let `default` = FamilyPickerConfiguration(
        navigationTitle: "Select Apps",
        saveButtonText: "Save",
        appsCountText: "Apps",
        websitesCountText: "Websites",
        categoriesCountText: "Categories",
        saveButtonColor: .blue,
        saveButtonTextColor: .white,
        countTextColor: .secondary,
        navigationTintColor: nil,
        darkTextColor: .primary,
        navigationTitleFont: nil,
        saveButtonFont: .body,
        countTextFont: .subheadline
    )
    
    // Initialize from Flutter arguments
    init(from arguments: [String: Any]?) {
        // Text defaults
        self.navigationTitle =
        arguments?["navigationTitle"] as? String ?? FamilyPickerConfiguration.default.navigationTitle
        self.saveButtonText =
        arguments?["saveButtonText"] as? String ?? FamilyPickerConfiguration.default.saveButtonText
        self.appsCountText =
        arguments?["appsCountText"] as? String ?? FamilyPickerConfiguration.default.appsCountText
        self.websitesCountText =
        arguments?["websitesCountText"] as? String ?? FamilyPickerConfiguration.default.websitesCountText
        self.categoriesCountText =
        arguments?["categoriesCountText"] as? String
        ?? FamilyPickerConfiguration.default.categoriesCountText
        
        // Color configuration
        self.saveButtonColor =
        Self.colorFromHex(arguments?["saveButtonColor"] as? String)
        ?? FamilyPickerConfiguration.default.saveButtonColor
        self.saveButtonTextColor =
        Self.colorFromHex(arguments?["saveButtonTextColor"] as? String)
        ?? FamilyPickerConfiguration.default.saveButtonTextColor
        self.countTextColor =
        Self.colorFromHex(arguments?["countTextColor"] as? String)
        ?? FamilyPickerConfiguration.default.countTextColor
        self.navigationTintColor = Self.colorFromHex(arguments?["navigationTintColor"] as? String)
        self.darkTextColor =
        Self.colorFromHex(arguments?["darkTextColor"] as? String)
        ?? FamilyPickerConfiguration.default.darkTextColor
        
        // Font configuration (simplified - could be expanded)
        let saveButtonFontSize = arguments?["saveButtonFontSize"] as? Double
        self.saveButtonFont =
        saveButtonFontSize != nil
        ? .system(size: CGFloat(saveButtonFontSize!)) : FamilyPickerConfiguration.default.saveButtonFont
        
        let countTextFontSize = arguments?["countTextFontSize"] as? Double
        self.countTextFont =
        countTextFontSize != nil
        ? .system(size: CGFloat(countTextFontSize!)) : FamilyPickerConfiguration.default.countTextFont
        
        let navigationTitleFontSize = arguments?["navigationTitleFontSize"] as? Double
        self.navigationTitleFont =
        navigationTitleFontSize != nil ? .system(size: CGFloat(navigationTitleFontSize!)) : nil
    }
    
    // Helper to convert hex string to Color
    private static func colorFromHex(_ hex: String?) -> Color? {
        guard let hex = hex else { return nil }
        
        let hexString = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        var cleanedHex = hexString.replacingOccurrences(of: "#", with: "")
        
        // Ensure we have 6 characters
        if cleanedHex.count == 6 {
            cleanedHex = "FF" + cleanedHex  // Add alpha if not provided
        }
        
        guard cleanedHex.count == 8 else { return nil }
        
        var rgbValue: UInt64 = 0
        guard Scanner(string: cleanedHex).scanHexInt64(&rgbValue) else { return nil }
        
        let red = Double((rgbValue & 0xFF0000) >> 16) / 255.0
        let green = Double((rgbValue & 0x00FF00) >> 8) / 255.0
        let blue = Double(rgbValue & 0x0000FF) / 255.0
        
        return Color(red: red, green: green, blue: blue)
    }
}
