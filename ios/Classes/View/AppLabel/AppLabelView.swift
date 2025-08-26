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
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 50)
            case .category(let token):
                Label(token)
                    .labelStyle(.titleAndIcon)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 50)
            case .webDomain(let token):
                Label(token)
                    .labelStyle(.titleAndIcon)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 50)
            }
        } else {
            switch tokenType {
            case .application:
                Text("Application")
                    .font(.headline)
                    .padding()
            case .category:
                Text("Category")
                    .font(.headline)
                    .padding()
            case .webDomain:
                Text("Web Domain")
                    .font(.headline)
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
    }
}
