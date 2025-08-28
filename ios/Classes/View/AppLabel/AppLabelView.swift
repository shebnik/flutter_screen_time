//
//  AppLabelView.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

import ManagedSettings
import SwiftUI

enum TokenType {
    case application(ApplicationToken)
    case category(ActivityCategoryToken)
    case webDomain(WebDomainToken)
}

struct AppLabelView: View {
    let tokenType: TokenType
    
    var body: some View {
        if #available(iOS 15.2, *) {
            switch tokenType {
            case .application(let token):
                Label(token)
                    .labelStyle(.titleAndIcon)
                    .foregroundColor(.black) // Use foregroundColor instead of foregroundStyle for better compatibility
                    .environment(\.colorScheme, .light)
                    .preferredColorScheme(.light) // Additional override for the view
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 50)
                    .background(Color.white) // Optional: add white background to ensure contrast
            case .category(let token):
                Label(token)
                    .labelStyle(.titleAndIcon)
                    .foregroundColor(.black)
                    .environment(\.colorScheme, .light)
                    .preferredColorScheme(.light)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 50)
                    .background(Color.white)
            case .webDomain(let token):
                Label(token)
                    .labelStyle(.titleAndIcon)
                    .foregroundColor(.black)
                    .environment(\.colorScheme, .light)
                    .preferredColorScheme(.light)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 50)
                    .background(Color.white)
            }
        } else {
            switch tokenType {
            case .application:
                Text("Application")
                    .font(.headline)
                    .foregroundColor(.black)
                    .padding()
            case .category:
                Text("Category")
                    .font(.headline)
                    .foregroundColor(.black)
                    .padding()
            case .webDomain:
                Text("Web Domain")
                    .font(.headline)
                    .foregroundColor(.black)
                    .padding()
            }
        }
    }
}

struct AppLabelView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 16) {
            Text("AppLabelView Preview")
                .font(.headline)
                .padding()
            Text("Requires valid tokens from Family Controls")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
        .previewLayout(.sizeThatFits)
        // Preview in both light and dark modes to test
        .environment(\.colorScheme, .dark)
    }
}
