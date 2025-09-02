package com.shebnik.flutter_screen_time.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.service.BlockingService
import com.shebnik.flutter_screen_time.service.BlockingVpnService

class StopBlockingReceiver(private val service: BlockingService) :
    BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BlockingService.ACTION_STOP_BLOCKING -> {
                service.stopBlocking()
                stopVpn(context)
            }

            BlockingService.ACTION_STOP_BLOCKING_APPS -> service.stopBlockingApps()
            BlockingService.ACTION_STOP_BLOCKING_WEBSITES -> {
                service.stopBlockingWebsites()
                stopVpn(context)
            }
        }
    }

    fun stopVpn(context: Context?) {
        val intent = Intent(context, BlockingVpnService::class.java).apply {
            putExtra(Argument.ACTION, Argument.STOP_ACTION)
        }
        context?.startService(intent)
    }
}