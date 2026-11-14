package com.netpulse.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.netpulse.app.data.SettingsStore
import com.netpulse.app.data.Store
import com.netpulse.app.monitor.MonitorService

class NetPulseApp : Application() {
    lateinit var store: Store
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        settings = SettingsStore(this)
        val channel = NotificationChannel(
            MonitorService.CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
