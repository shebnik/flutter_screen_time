package com.shebnik.flutter_screen_time.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shebnik.flutter_screen_time.service.BlockingVpnService

class StopVpnReceiver(private val service: BlockingVpnService) :
    BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BlockingVpnService.ACTION_STOP_VPN -> {
                service.stopVpn()
            }
        }
    }
}