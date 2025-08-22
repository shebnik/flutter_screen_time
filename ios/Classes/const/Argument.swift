//
//  Argument.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

public class Argument {
    public static let LOG_FILE_PATH = "logFilePath"
    public static let PERMISSION_TYPE = "permissionType"
    
    public static let BLOCKED_WEB_DOMAINS = "blockedWebDomains"

    // iOS Only
    public static let SELECTION = "selection"
    public static let APPLICATION_TOKENS = "applicationTokens"
    public static let CATEGORY_TOKENS = "categoryTokens"
    public static let WEB_DOMAIN_TOKENS = "webDomainTokens"
    
    public static let IS_ADULT_CONTENT_BLOCKED = "isAdultContentBlocked"
    public static let IS_WEB_FILTER_ACTIVE = "isWebFilterActive"
    public static let IS_ENABLED = "isEnabled"
}
