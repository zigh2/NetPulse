package com.netpulse.app.monitor

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.netpulse.app.R
import com.netpulse.app.data.LocaleHelper
import com.netpulse.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Quick Settings tile: shows the status and starts / stops monitoring. */
class MonitorTileService : TileService() {
    private var job: Job? = null

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onStartListening() {
        super.onStartListening()
        job = CoroutineScope(Dispatchers.Main).launch {
            MonitorState.flow.collect { refresh(it) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (MonitorState.flow.value.running) {
            MonitorService.stop(this)
            return
        }
        try {
            MonitorService.start(this, resume = false)
        } catch (e: Exception) {
            // Background start not allowed on this device: open the app and start from there.
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_AUTOSTART, true)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 2, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        }
    }

    private fun refresh(l: Live) {
        val t = qsTile ?: return
        t.state = if (l.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            t.subtitle = when {
                !l.running -> getString(R.string.status_idle)
                l.paused -> getString(R.string.paused_label)
                l.online == true -> getString(R.string.status_online)
                l.online == false -> getString(R.string.status_offline)
                else -> getString(R.string.status_waiting)
            }
        }
        t.updateTile()
    }
}
