package com.shebnik.flutter_screen_time.const

object MethodName {
    const val CONFIGURE = "configure"

    const val REQUEST_PERMISSION = "requestPermission"
    const val AUTHORIZATION_STATUS = "authorizationStatus"

    const val BLOCK_APPS = "blockApps"
    const val BLOCK_WEB_DOMAINS = "blockWebDomains"
    const val BLOCK_APPS_AND_WEB_DOMAINS = "blockAppsAndWebDomains"

    const val DISABLE_APPS_BLOCKING = "disableAppsBlocking"
    const val DISABLE_WEB_DOMAINS_BLOCKING = "disableWebDomainsBlocking"
    const val DISABLE_APPS_AND_WEB_DOMAINS_BLOCKING = "disableAppsAndWebDomainsBlocking"
    const val DISABLE_ALL_BLOCKING = "disableAllBlocking"

    // Android only
    const val INSTALLED_APPS = "installedApps"
}