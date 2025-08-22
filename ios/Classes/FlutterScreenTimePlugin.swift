import Flutter

public class FlutterScreenTimePlugin: NSObject, FlutterPlugin {
    private var methods = FlutterScreenTimeMethod()
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_screen_time", binaryMessenger: registrar.messenger())
        let instance = FlutterScreenTimePlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        // Register the platform view factory for app labels
        let factory = AppLabelViewFactory()
        registrar.register(factory, withId: "app_label_view")
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case MethodName.CONFIGURE:
            guard let arguments = call.arguments as? [String: Any] else {
                logError("Configure failed: missing arguments")
                result(
                    FlutterError(
                        code: "INVALID_ARGUMENTS", message: "Missing configuration arguments",
                        details: nil))
                return
            }
            
            var configuredItems: [String] = []
            
            // Configure logging if logFilePath is provided
            if let logFilePath = arguments[Argument.LOG_FILE_PATH] as? String {
                Logger.shared.configureLogFile(path: logFilePath)
                configuredItems.append("logging")
                logSuccess("Logging configured: \(logFilePath)")
            }
            
            result([
                "configured": configuredItems,
            ])
        case MethodName.REQUEST_PERMISSION:
            Task {
                do {
                    logInfo("🔐 Requesting Screen Time authorization")
                    try await FamilyControlsModel.shared.authorize()
                    logSuccess("Authorization granted successfully")
                    result(true)
                } catch {
                    logError("Authorization failed: \(error.localizedDescription)")
                    result(
                        FlutterError(
                            code: "AUTHORIZATION_FAILED",
                            message: "Failed to authorize Screen Time API",
                            details: error.localizedDescription
                        ))
                }
            }
        case MethodName.AUTHORIZATION_STATUS:
            let status = methods.getAuthorizationStatus()
            logDebug("📋 Authorization status checked: \(status)")
            result(status)
        case MethodName.BLOCK_APPS:
            guard let arguments = call.arguments as? [String: Any] else {
                result(
                    FlutterError(
                        code: "INVALID_ARGUMENTS",
                        message: "Invalid arguments provided to \(MethodName.BLOCK_APPS)",
                        details: nil
                    ))
                return
            }
            methods.blockApps(arguments: arguments, result: result)
        case MethodName.BLOCK_WEB_DOMAINS:
            guard let arguments = call.arguments as? [String: Any],
                  let adultWebsitesBlocked = arguments[Argument.IS_ADULT_WEBSITES_BLOCKED] as? Bool,
                  let blockedDomains = arguments[Argument.BLOCKED_WEB_DOMAINS] as? [String]
            else {
                result(
                    FlutterError(
                        code: "INVALID_ARGUMENTS",
                        message: "Invalid arguments provided to blockWebDomains",
                        details:
                            "Expected: adultWebsitesBlocked (Bool), blockedDomains (List<String>)"
                    ))
                return
            }
            methods.setWebContentBlocking(adultWebsitesBlocked: adultWebsitesBlocked, blockedDomains: blockedDomains, result: result)
        case MethodName.DISABLE_APPS_BLOCKING:
            methods.disableAppsBlocking(result: result)
        case MethodName.DISABLE_WEB_DOMAINS_BLOCKING:
            methods.disableWebDomainsBlocking(result: result)
        case MethodName.DISABLE_ALL_BLOCKING:
            methods.disableAllBlocking(result: result)
        case MethodName.SHOW_FAMILY_ACTIVITY_PICKER:
            guard let arguments = call.arguments as? [String: Any] else {
                result(
                    FlutterError(
                        code: "INVALID_ARGUMENTS",
                        message: "Invalid arguments provided to \(MethodName.SHOW_FAMILY_ACTIVITY_PICKER)",
                        details: nil
                    ))
                return
            }
            methods.showFamilyActivityPicker(arguments: arguments, result: result)
         case MethodName.UNBLOCK_APPS:
            guard let arguments = call.arguments as? [String: Any] else {
                result(
                    FlutterError(
                        code: "INVALID_ARGUMENTS",
                        message: "Invalid arguments provided to encourage",
                        details: nil
                    ))
                return
            }
            methods.unblockApps(arguments: arguments, result: result)
        case MethodName.GET_BLOCKED_APPS:
            methods.getBlockedApps(result: result)
        case MethodName.SET_ADULT_WEBSITES_BLOCKING:
            guard let arguments = call.arguments as? [String: Any],
                  let enabled = arguments[Argument.IS_ENABLED] as? Bool
            else {
                result(
                    FlutterError(
                        code: "INVALID_ARGUMENTS",
                        message: "Invalid arguments provided to setAdultWebsiteBlocking",
                        details: nil
                    ))
                return
            }
            methods.setAdultWebsitesBlocking(isEnabled: enabled, result: result)
        case MethodName.IS_ADULT_WEBSITES_BLOCKED:
            methods.isAdultWebsitesBlocked(result: result)
        case MethodName.GET_WEB_CONTENT_BLOCKING:
            methods.getWebContentBlocking(result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    
}
