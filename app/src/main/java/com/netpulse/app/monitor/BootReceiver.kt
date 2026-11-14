package com.netpulse.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netpulse.app.NetPulseApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NetPulseApp
        if (app.settings.monitoring && app.settings.state.value.resumeOnBoot) {
            MonitorService.start(context, resume = true)
        }
    }
}
