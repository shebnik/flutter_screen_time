//
//  FamilyPickerView.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

import FamilyControls
import SwiftUI

// Extension to convert SwiftUI Font to UIFont
extension Font {
    func toUIFont() -> UIFont? {
        switch self {
        case .largeTitle:
            return UIFont.preferredFont(forTextStyle: .largeTitle)
        case .title:
            return UIFont.preferredFont(forTextStyle: .title1)
        case .title2:
            return UIFont.preferredFont(forTextStyle: .title2)
        case .title3:
            return UIFont.preferredFont(forTextStyle: .title3)
        case .headline:
            return UIFont.preferredFont(forTextStyle: .headline)
        case .subheadline:
            return UIFont.preferredFont(forTextStyle: .subheadline)
        case .body:
            return UIFont.preferredFont(forTextStyle: .body)
        case .callout:
            return UIFont.preferredFont(forTextStyle: .callout)
        case .footnote:
            return UIFont.preferredFont(forTextStyle: .footnote)
        case .caption:
            return UIFont.preferredFont(forTextStyle: .caption1)
        case .caption2:
            return UIFont.preferredFont(forTextStyle: .caption2)
        default:
            // For custom fonts, return system font as fallback
            return UIFont.systemFont(ofSize: 17)
        }
    }
}

struct FamilyPickerView: View {
    @StateObject var model = FamilyControlsModel.shared
    @Environment(\.presentationMode) var presentationMode
    @State private var pickerKey = UUID()  // Add this to force picker refresh
    var onSave: (() -> Void)?
    var onCancel: (() -> Void)?
    let uiConfig: FamilyPickerConfiguration

    init(
        onSave: (() -> Void)? = nil, onCancel: (() -> Void)? = nil,
        uiConfig: FamilyPickerConfiguration = .default
    ) {
        self.onSave = onSave
        self.onCancel = onCancel
        self.uiConfig = uiConfig
    }

    var body: some View {
        VStack(spacing: 12) {
            // Family Activity Picker
            FamilyActivityPicker(
                selection: $model.tempSelection
            )
            .id(pickerKey)  // Add this to force refresh when key changes
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Selection summary at the bottom
            VStack(spacing: 12) {
                HStack(spacing: 16) {
                    Text(
                        "\(uiConfig.appsCountText): \(model.tempSelection.applicationTokens.count)")
                    Text(
                        "\(uiConfig.websitesCountText): \(model.tempSelection.webDomainTokens.count)"
                    )
                    if model.tempSelection.categoryTokens.count > 0 {
                        Text(
                            "\(uiConfig.categoriesCountText): \(model.tempSelection.categoryTokens.count)"
                        )
                    }
                }
                .font(uiConfig.countTextFont)
                .foregroundColor(uiConfig.countTextColor)
                .padding(.horizontal)

                // Save button below the count
                Button(action: saveSelection) {
                    Text(uiConfig.saveButtonText)
                        .font(uiConfig.saveButtonFont)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(uiConfig.saveButtonColor)
                        .foregroundColor(uiConfig.saveButtonTextColor)
                        .cornerRadius(10)
                }
                .padding(.horizontal, 60)
                .padding(.bottom, 20)
            }
            .background(Color(.systemBackground))
        }
        .navigationTitle(uiConfig.navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: cancelSelection) {
                    Image(systemName: "xmark")
                        .font(.title2)
                        .foregroundColor(uiConfig.navigationTintColor ?? .primary)
                }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: clearSelection) {
                    Image(systemName: "arrow.clockwise")
                        .font(.title2)
                        .foregroundColor(uiConfig.navigationTintColor ?? .primary)
                }
            }
        }
        .onAppear {
            // Don't reset temp selection on appear - keep whatever was pre-set from Flutter
            // Just apply custom navigation title font if provided
            if let titleFont = uiConfig.navigationTitleFont {
                let appearance = UINavigationBarAppearance()
                appearance.configureWithOpaqueBackground()
                if let uiFont = titleFont.toUIFont() {
                    appearance.titleTextAttributes = [.font: uiFont]
                }
                UINavigationBar.appearance().standardAppearance = appearance
                UINavigationBar.appearance().scrollEdgeAppearance = appearance
            }
        }
    }

    private func saveSelection() {
        // Return the current temp selection to Flutter
        onSave?()
    }

    private func cancelSelection() {
        // Return null to Flutter by triggering cancel callback
        onCancel?()
    }

    private func clearSelection() {
        withAnimation {
            model.clearTempSelection()
            // Force the picker to refresh by changing its identity
            pickerKey = UUID()
        }
    }
}

struct FamilyPickerView_Previews: PreviewProvider {
    static var previews: some View {
        FamilyPickerView(uiConfig: .default)
    }
}
