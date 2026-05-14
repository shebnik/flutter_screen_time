//
//  MethodName.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

public class MethodName {
    public static let CONFIGURE = "configure"
    
    public static let REQUEST_PERMISSION = "requestPermission"
    public static let AUTHORIZATION_STATUS = "authorizationStatus"
    
    public static let BLOCK_APPS = "blockApps"
    public static let BLOCK_WEB_DOMAINS = "blockWebDomains"
    public static let BLOCK_APPS_AND_WEB_DOMAINS = "blockAppsAndWebDomains"
    
    public static let DISABLE_APPS_BLOCKING = "disableAppsBlocking"
    public static let DISABLE_WEB_DOMAINS_BLOCKING = "disableWebDomainsBlocking"
    public static let DISABLE_ALL_BLOCKING = "disableAllBlocking"

    // iOS only
    public static let SHOW_FAMILY_ACTIVITY_PICKER = "showFamilyActivityPicker"
    public static let UNBLOCK_APPS = "unblockApps"
    public static let GET_BLOCKED_APPS = "getBlockedApps"
    public static let SET_ADULT_WEBSITES_BLOCKING = "setAdultWebsitesBlocking"
    public static let IS_ADULT_WEBSITES_BLOCKED = "isAdultWebsitesBlocked"
    public static let GET_WEB_CONTENT_BLOCKING = "getWebContentBlocking"
}
