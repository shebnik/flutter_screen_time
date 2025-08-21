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
        case MethodName.PERMISSION_STATUS:
            let status = methods.getAuthorizationStatus()
            logDebug("📋 Authorization status checked: \(status)")
            result(["status": status])
        case MethodName.REQUEST_PERMISSION:
            Task {
                do {
                    logInfo("🔐 Requesting Screen Time authorization")
                    try await FamilyControlsModel.shared.authorize()
                    logSuccess("Authorization granted successfully")
                    result(["status": "authorized"])
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
        case MethodName.UNBLOCK_APPS:
            
        case MethodName.DISABLE_ALL_BLOCKING:
            methods.disableAllBlocking(result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    
}
