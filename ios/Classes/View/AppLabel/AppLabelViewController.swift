//
//  AppLabelViewController.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

import Combine
import FamilyControls
import Flutter
import ManagedSettings
import SwiftUI
import UIKit

class AppLabelViewController: UIViewController {
    private var hostingController: UIHostingController<AnyView>?
    private let tokenType: String
    private let encodedToken: String
    
    init(tokenType: String, encodedToken: String) {
        self.tokenType = tokenType
        self.encodedToken = encodedToken
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupAppLabelView()
    }
    
    private func setupAppLabelView() {
        logInfo("Setting up AppLabelView: tokenType=\(tokenType), encodedToken=\(encodedToken)")
        
        var tokenTypeResult: TokenType?
        let tokenManager = TokenManager()
        
        switch tokenType {
        case "application":
            if let token = try? tokenManager.decodeApplicationToken(encodedToken) {
                tokenTypeResult = .application(token)
                logSuccess("Successfully decoded ApplicationToken")
            } else {
                logWarning("Failed to decode ApplicationToken from string: \(encodedToken)")
            }
            
        case "category":
            if let token = try? tokenManager.decodeCategoryToken(encodedToken) {
                tokenTypeResult = .category(token)
                logSuccess("Successfully decoded ActivityCategoryToken")
            } else {
                logWarning("Failed to decode ActivityCategoryToken from string: \(encodedToken)")
            }
            
        case "webDomain":
            if let token = try? tokenManager.decodeWebDomainToken(encodedToken) {
                tokenTypeResult = .webDomain(token)
                logSuccess("Successfully decoded WebDomainToken")
            } else {
                logWarning("Failed to decode WebDomainToken from string: \(encodedToken)")
            }
            
        default:
            logError("Unknown token type: \(tokenType)")
        }
        
        if let tokenTypeResult = tokenTypeResult {
            let appLabelView = AppLabelView(tokenType: tokenTypeResult)
            hostingController = UIHostingController(rootView: AnyView(appLabelView))
            logSuccess("AppLabelView created successfully")
        } else {
            // If token not found, show an error message
            logError("Token could not be decoded - showing error view")
            let errorView = VStack {
                Text("Token not available")
                    .foregroundColor(.red)
                Text("Type: \(tokenType)")
                    .font(.caption)
                Text("Token: \(encodedToken)")
                    .font(.caption)
            }
                .padding()
            hostingController = UIHostingController(rootView: AnyView(errorView))
        }
        
        setupHostingController()
    }
    
    private func setupHostingController() {
        guard let newHostingController = hostingController else { return }
        
        // Remove any existing hosting controller first
        if let existingController = children.first(where: { $0 is UIHostingController<AnyView> }) {
            existingController.willMove(toParent: nil)
            existingController.view.removeFromSuperview()
            existingController.removeFromParent()
        }
        
        addChild(newHostingController)
        view.addSubview(newHostingController.view)
        newHostingController.didMove(toParent: self)
        
        // Set up constraints
        newHostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            newHostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            newHostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            newHostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            newHostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        
        // Set transparent background
        view.backgroundColor = UIColor.clear
        newHostingController.view.backgroundColor = UIColor.clear
    }
}

class AppLabelViewFactory: NSObject, FlutterPlatformViewFactory {
    func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?)
    -> FlutterPlatformView
    {
        return AppLabelPlatformView(frame: frame, viewId: viewId, args: args)
    }
    
    func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol {
        return FlutterStandardMessageCodec.sharedInstance()
    }
}

class AppLabelPlatformView: NSObject, FlutterPlatformView {
    private let _view: UIView
    private let _controller: AppLabelViewController
    
    init(frame: CGRect, viewId: Int64, args: Any?) {
        // Extract token type and encoded token from arguments
        var tokenType = "application"
        var encodedToken = ""
        
        logDebug("AppLabelViewFactory init with args: \(String(describing: args))")
        
        if let arguments = args as? [String: Any] {
            logInfo("Processing AppLabelView arguments: \(arguments.keys.joined(separator: ", "))")
            if let type = arguments["tokenType"] as? String {
                tokenType = type
                logDebug("Extracted tokenType: \(tokenType)")
            }
            if let token = arguments["encodedToken"] as? String {
                encodedToken = token
                logDebug("Extracted encodedToken: \(encodedToken.prefix(20))...")
            }
        } else {
            logWarning("Arguments is not a dictionary - using defaults")
        }
        
        logInfo(
            "Final AppLabelView configuration - tokenType: \(tokenType), encodedToken length: \(encodedToken.count)"
        )
        
        _controller = AppLabelViewController(tokenType: tokenType, encodedToken: encodedToken)
        _view = _controller.view
        super.init()
        
        _view.frame = frame
        logSuccess("AppLabelPlatformView initialized successfully")
    }
    
    func view() -> UIView {
        return _view
    }
}
