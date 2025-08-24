package com.shebnik.flutter_screen_time.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shebnik.flutter_screen_time.service.WebsitesBlockingAccessibilityService

class StopWebsitesBlockingReceiver(private val service: WebsitesBlockingAccessibilityService) :
    BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == WebsitesBlockingAccessibilityService.ACTION_STOP_BLOCKING) {
            service.stopBlocking()
        }
    }
}