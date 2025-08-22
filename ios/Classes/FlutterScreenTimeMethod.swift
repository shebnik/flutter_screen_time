//
//  FlutterScreenTimeMethod.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

import Flutter
import SwiftUI
import FamilyControls
import ManagedSettings
import UIKit

class FlutterScreenTimeMethod {
    private var pendingResult: FlutterResult?
    
    func checkAuthorization(result: @escaping FlutterResult) -> Bool {
        let authStatus = getAuthorizationStatus()
        if authStatus != "authorized" {
            logWarning("Family activity picker requested but not authorized: \(authStatus)")
            result(
                FlutterError(
                    code: "NOT_AUTHORIZED",
                    message:
                        "Screen Time API not authorized. Call requestAuthorization first.",
                    details: "Current status: \(authStatus)"
                ))
            return false
        }
        
        return true
    }
    
    func showFamilyActivityPicker(arguments: [String: Any]?, result: @escaping FlutterResult) {
        Task {
            // Check authorization first
            if (!checkAuthorization(result: result)) {return}
            
            // Extract UI configuration and pre-selected apps from arguments
            let uiConfigArgs = arguments?[Argument.FAMILY_PICKER_CONFIGURATION] as? [String: Any] ?? [:]

            // Handle pre-selected apps if provided, otherwise start with empty selection
            if let preSelectedAppsData = arguments?[Argument.SELECTION] as? [String: Any] {
                do {
                    // Decode the pre-selected apps and set as temp selection
                    let preSelection = try decodeSelectionFromMap(preSelectedAppsData)
                    FamilyControlsModel.shared.setTempSelection(with: preSelection)
                    logInfo(
                        "🎯 Pre-selected apps set: \(preSelection.applicationTokens.count) apps, \(preSelection.categoryTokens.count) categories"
                    )
                } catch {
                    logError(
                        "Failed to decode pre-selected apps: \(error.localizedDescription)")
                    // Start with empty selection if decoding fails
                    FamilyControlsModel.shared.clearTempSelection()
                }
            } else {
                // No pre-selection provided, start with empty selection
                FamilyControlsModel.shared.clearTempSelection()
            }
            
            logInfo("📱 Showing family activity picker")
            
            await MainActor.run {
                pendingResult = result
                showController(with: uiConfigArgs)
            }
        }
    }
    
    func blockApps(arguments: [String: Any], result: @escaping FlutterResult) {
        Task {
            // Check authorization first
            if (!checkAuthorization(result: result)) {return}
            
            do {
                try await discourageSelection(with: arguments[Argument.SELECTION] as? [String: Any] ?? [:])
                result(true)
            } catch {
                result(
                    FlutterError(
                        code: "DISCOURAGE_FAILED",
                        message: "Failed to discourage selection in Screen Time API",
                        details: error.localizedDescription
                    ))
            }
        }
    }

    func disableAppsBlocking(result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        logInfo("🔓 Encouraging all apps - removing restrictions")
        
        // Clear all selections and encourage all apps
        FamilyControlsModel.shared.encourageAllApps()
        logSuccess("All apps encouraged and restrictions removed")
        result(true)
    }

    func disableWebDomainsBlocking(result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        logInfo("🔓 Encouraging all web domains - removing restrictions")

        // Clear all selections and encourage all web domains
        FamilyControlsModel.shared.encourageAllWebDomains()
        logSuccess("All web domains encouraged and restrictions removed")
        result(true)
    }
    
    func disableAllBlocking(result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        logInfo("🔓 Encouraging all apps and web domains - removing restrictions")

        // Clear all selections and encourage all apps
        FamilyControlsModel.shared.encourageAll()
        logSuccess("All apps and web domains encouraged and restrictions removed")
        result(true)
    }
    
    func unblockApps(arguments: [String:Any], result: @escaping FlutterResult){
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        Task {
            do {
                try await encourageSelection(with: arguments)
                logSuccess("Successfully encouraged selected apps")
                result(true)
            } catch {
                logError("Failed to encourage apps: \(error.localizedDescription)")
                result(
                    FlutterError(
                        code: "ENCOURAGE_FAILED",
                        message: "Failed to encourage selection in Screen Time API",
                        details: error.localizedDescription
                    ))
            }
        }
    }
    
    func getBlockedApps(result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        
        let discouragedApps = FamilyControlsModel.shared.getDiscouragedApps()
        result(discouragedApps)
    }
    
    func setAdultWebsitesBlocking(isEnabled: Bool, result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        
        FamilyControlsModel.shared.setAdultWebsiteBlocking(enabled: isEnabled)
        logInfo("🔒 Adult website blocking \(isEnabled ? "enabled" : "disabled")")
        result(true)
    }
    
    func isAdultWebsitesBlocked(result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        let isBlocked = FamilyControlsModel.shared.getAdultWebsiteBlocking()
        logInfo("Get adult website blocking called, isBlocked: \(isBlocked)")
        result(isBlocked)
    }
    
    func setWebContentBlocking(adultWebsitesBlocked: Bool, blockedDomains: [String], result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        
        if blockedDomains.count > 50 {
            result(
                FlutterError(
                    code: "TOO_MANY_BLOCKED_DOMAINS",
                    message: "Too many blocked domains. Maximum is 50.",
                    details: "Provided: \(blockedDomains.count)"
                ))
            return
        }
        
        Task {
            do {
                logInfo(
                    "Setting web content blocking - adult websites blocked: \(adultWebsitesBlocked), blocked domains: \(blockedDomains.count)"
                )
                try await FamilyControlsModel.shared.setWebContentBlocking(
                    adultWebsitesBlocked: adultWebsitesBlocked,
                    blockedDomains: blockedDomains
                )
                logSuccess("Web content blocking set successfully")
                result(true)
            } catch {
                logError("Failed to set web content blocking: \(error.localizedDescription)")
                result(
                    FlutterError(
                        code: "WEB_CONTENT_BLOCKING_FAILED",
                        message: "Failed to set web content blocking",
                        details: error.localizedDescription
                    ))
            }
        }
    }
    
    func getWebContentBlocking(result: @escaping FlutterResult) {
        // Check authorization first
        if(!checkAuthorization(result: result)) {return}
        
        Task {
            do {
                let webContentConfig = try await FamilyControlsModel.shared
                    .getWebContentBlocking()
                logInfo(
                    "Get web content blocking completed - adult websites: \(webContentConfig[Argument.IS_ADULT_WEBSITES_BLOCKED] as? Bool ?? false), blocked domains: \((webContentConfig[Argument.BLOCKED_WEB_DOMAINS] as? [String])?.count ?? 0))"
                )
                result(webContentConfig)
            } catch {
                logError("Failed to get web content blocking: \(error.localizedDescription)")
                result(
                    FlutterError(
                        code: "WEB_CONTENT_BLOCKING_FETCH_FAILED",
                        message: "Failed to get web content blocking",
                        details: error.localizedDescription
                    ))
            }
        }
    }
    
    @objc func onSelectionSaved() {
        logInfo("Selection saved - processing new family activity selection")
        
        // Get the temp selection (what user just selected)
        let tempSelection = FamilyControlsModel.shared.tempSelection
        let selectedTokens = encodeSelection(tempSelection)
        logInfo(
            "Saved tokens - apps: \(selectedTokens["applicationTokens"] as? [String] ?? []), categories: \(selectedTokens["categoryTokens"] as? [String] ?? [])"
        )
        
        dismiss()
        
        // Return the saved tokens to Flutter
        if let result = pendingResult {
            logSuccess("Returning saved selection result to Flutter")
            result(selectedTokens)
            pendingResult = nil
        }
    }
    
    @objc func onPressClose() {
        logInfo("Close button pressed - canceling family activity selection")
        
        dismiss()
        
        // Return null to Flutter (using NSNull)
        if let result = pendingResult {
            logInfo("Returning null result to Flutter - selection was cancelled")
            result(NSNull())
            pendingResult = nil
        }
    }
    
    private func discourageSelection(with arguments: [String: Any]) async throws {
        let model = FamilyControlsModel.shared
        
        // Decode tokens from Flutter
        let tokenManager = TokenManager()
        
        var applications: Set<ApplicationToken> = []
        var categories: Set<ActivityCategoryToken> = []
        var webDomains: Set<WebDomainToken> = []
        
        if let appTokens = arguments[Argument.APPLICATION_TOKENS] as? [String] {
            for tokenString in appTokens {
                do {
                    let token = try tokenManager.decodeApplicationToken(tokenString)
                    applications.insert(token)
                } catch {
                    logWarning("Failed to decode application token: \(tokenString)")
                }
            }
        }
        
        if let catTokens = arguments[Argument.CATEGORY_TOKENS] as? [String] {
            for tokenString in catTokens {
                do {
                    let token = try tokenManager.decodeCategoryToken(tokenString)
                    categories.insert(token)
                } catch {
                    logWarning("Failed to decode category token: \(tokenString)")
                }
            }
        }
        
        if let webTokens = arguments[Argument.WEB_DOMAIN_TOKENS] as? [String] {
            for tokenString in webTokens {
                do {
                    let token = try tokenManager.decodeWebDomainToken(tokenString)
                    webDomains.insert(token)
                } catch {
                    logWarning("Failed to decode web domain token: \(tokenString)")
                }
            }
        }
        
        model.discourage(
            applications: applications, categories: categories, webDomains: webDomains)
    }
    
    private func encourageSelection(with arguments: [String: Any]) async throws {
        let model = FamilyControlsModel.shared
        
        // Decode tokens from Flutter
        let tokenManager = TokenManager()
        
        var applications: Set<ApplicationToken> = []
        var categories: Set<ActivityCategoryToken> = []
        var webDomains: Set<WebDomainToken> = []
        
        if let appTokens = arguments[Argument.APPLICATION_TOKENS] as? [String] {
            for tokenString in appTokens {
                do {
                    let token = try tokenManager.decodeApplicationToken(tokenString)
                    applications.insert(token)
                } catch {
                    logWarning("Failed to decode application token: \(tokenString)")
                }
            }
        }
        
        if let catTokens = arguments[Argument.CATEGORY_TOKENS] as? [String] {
            for tokenString in catTokens {
                do {
                    let token = try tokenManager.decodeCategoryToken(tokenString)
                    categories.insert(token)
                } catch {
                    logWarning("Failed to decode category token: \(tokenString)")
                }
            }
        }
        
        if let webTokens = arguments[Argument.WEB_DOMAIN_TOKENS] as? [String] {
            for tokenString in webTokens {
                do {
                    let token = try tokenManager.decodeWebDomainToken(tokenString)
                    webDomains.insert(token)
                } catch {
                    logWarning("Failed to decode web domain token: \(tokenString)")
                }
            }
        }
        
        logInfo(
            "🔓 Encouraging specific apps - apps: \(applications.count), categories: \(categories.count), webDomains: \(webDomains.count)"
        )
        
        model.encourage(
            applications: applications, categories: categories, webDomains: webDomains)
    }
    
    private func encodeSelection(_ selection: FamilyActivitySelection) -> [String: Any] {
        let tokenManager = TokenManager()
        
        var applicationTokens: [String] = []
        for token in selection.applicationTokens {
            do {
                applicationTokens.append(try tokenManager.encodeApplicationToken(token))
            } catch {
                logWarning("Failed to encode application token: \(error)")
            }
        }
        
        var categoryTokens: [String] = []
        for token in selection.categoryTokens {
            do {
                categoryTokens.append(try tokenManager.encodeCategoryToken(token))
            } catch {
                logWarning("Failed to encode category token: \(error)")
            }
        }
        
        var webDomainTokens: [String] = []
        for token in selection.webDomainTokens {
            do {
                webDomainTokens.append(try tokenManager.encodeWebDomainToken(token))
            } catch {
                logWarning("Failed to encode web domain token: \(error)")
            }
        }
        
        let result: [String: Any] = [
            Argument.APPLICATION_TOKENS: applicationTokens,
            Argument.CATEGORY_TOKENS: categoryTokens,
            Argument.WEB_DOMAIN_TOKENS: webDomainTokens,
        ]
        
        return result
    }
    
    public func getAuthorizationStatus() -> String {
        switch AuthorizationCenter.shared.authorizationStatus {
        case .notDetermined:
            return String(describing: AuthorizationStatus.notDetermined)
        case .denied:
            return String(describing: AuthorizationStatus.denied)
        case .approved:
            return String(describing: AuthorizationStatus.approved)
        @unknown default:
            return String(describing: AuthorizationStatus.notDetermined)
        }
    }
    
    func showController(with uiConfigArgs: [String: Any]? = nil) {
        DispatchQueue.main.async {
            let scenes = UIApplication.shared.connectedScenes
            let windowScene = scenes.first as? UIWindowScene
            let windows = windowScene?.windows
            let controller =
            windows?.filter({ (w) -> Bool in
                return w.isHidden == false
            }).first?.rootViewController as? FlutterViewController
            
            // Create FamilyPickerConfiguration from arguments
            let uiConfig = FamilyPickerConfiguration(from: uiConfigArgs)
            
            // Create FamilyPickerView with callbacks and configuration
            let contentView = FamilyPickerView(
                onSave: { [weak self] in
                    self?.onSelectionSaved()
                },
                onCancel: { [weak self] in
                    self?.onPressClose()
                },
                uiConfig: uiConfig
            )
            
            // Display the app selection UI
            let selectAppVC: UIViewController = UIHostingController(rootView: contentView)
            let naviVC = UINavigationController(rootViewController: selectAppVC)
            controller?.present(naviVC, animated: true, completion: nil)
        }
    }
    
    func dismiss() {
        DispatchQueue.main.async {
            let scenes = UIApplication.shared.connectedScenes
            let windowScene = scenes.first as? UIWindowScene
            let windows = windowScene?.windows
            let controller =
            windows?.filter({ (w) -> Bool in
                return w.isHidden == false
            }).first?.rootViewController as? FlutterViewController
            controller?.dismiss(animated: true, completion: nil)
        }
    }
    
    private func decodeSelectionFromMap(_ selectionMap: [String: Any]) throws
    -> FamilyActivitySelection
    {
        let tokenManager = TokenManager()
        
        var applications: Set<ApplicationToken> = []
        var categories: Set<ActivityCategoryToken> = []
        var webDomains: Set<WebDomainToken> = []
        
        // Decode application tokens
        if let appTokens = selectionMap[Argument.APPLICATION_TOKENS] as? [String] {
            for tokenString in appTokens {
                do {
                    let token = try tokenManager.decodeApplicationToken(tokenString)
                    applications.insert(token)
                } catch {
                    logWarning("Failed to decode application token: \(tokenString)")
                }
            }
        }
        
        // Decode category tokens
        if let catTokens = selectionMap[Argument.CATEGORY_TOKENS] as? [String] {
            for tokenString in catTokens {
                do {
                    let token = try tokenManager.decodeCategoryToken(tokenString)
                    categories.insert(token)
                } catch {
                    logWarning("Failed to decode category token: \(tokenString)")
                }
            }
        }
        
        // Decode web domain tokens
        if let webTokens = selectionMap[Argument.WEB_DOMAIN_TOKENS] as? [String] {
            for tokenString in webTokens {
                do {
                    let token = try tokenManager.decodeWebDomainToken(tokenString)
                    webDomains.insert(token)
                } catch {
                    logWarning("Failed to decode web domain token: \(tokenString)")
                }
            }
        }
        
        // Create the selection with decoded tokens
        var selection = FamilyActivitySelection()
        selection.applicationTokens = applications
        selection.categoryTokens = categories
        selection.webDomainTokens = webDomains
        
        return selection
    }
}
