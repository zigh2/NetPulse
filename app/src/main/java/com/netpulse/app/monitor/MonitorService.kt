package com.netpulse.app.monitor

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.netpulse.app.NetPulseApp
import com.netpulse.app.R
import com.netpulse.app.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Foreground service that probes the connection every N seconds and keeps a persistent,
 * colour-coded notification (green = online, red = offline).
 */
class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "monitor"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.netpulse.app.START"
        const val ACTION_RESUME = "com.netpulse.app.RESUME"
        const val ACTION_STOP = "com.netpulse.app.STOP"

        private const val GREEN = 0xFF16A34A.toInt()
        private const val RED = 0xFFDC2626.toInt()
        private const val GRAY = 0xFF475569.toInt()

        fun start(ctx: Context, resume: Boolean) {
            val i = Intent(ctx, MonitorService::class.java)
                .setAction(if (resume) ACTION_RESUME else ACTION_START)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        if (loop == null) {
            // A null intent means the system restarted us after killing the process.
            startLoop(resume = intent?.action != ACTION_START)
        }
        return START_STICKY
    }

    private fun startLoop(resume: Boolean) {
        val app = application as NetPulseApp
        val store = app.store
        val settings = app.settings

        loop = scope.launch {
            val now0 = System.currentTimeMillis()
            if (resume) {
                // The process / device was down: close the old session where we last heard from it.
                val hb = settings.lastHeartbeat
                store.closeOpenSession(if (hb in 1..now0) hb else now0)
            } else {
                store.closeOpenSession(now0)
            }
            val sessionId = store.startSession(now0)
            settings.monitoring = true
            settings.lastHeartbeat = now0
            MonitorState.flow.value = Live(running = true, sessionId = sessionId, sinceMs = now0)
            applyWakeLock(settings.state.value.wakeLock)

            var confirmed: Boolean? = null
            var failStreak = 0
            var firstFailAt = 0L
            var checks = 0

            while (isActive) {
                val s = settings.state.value
                applyWakeLock(s.wakeLock)
                val t0 = SystemClock.elapsedRealtime()
                val wallStart = System.currentTimeMillis()
                try {
                    val r = Pinger.probe(s.hosts, s.timeoutMs)
                    val now = System.currentTimeMillis()
                    checks++
                    settings.lastHeartbeat = now
                    var since = MonitorState.flow.value.sinceMs

                    if (r.ok) {
                        failStreak = 0
                        if (confirmed != true) {
                            val at = if (confirmed == null) now0 else now
                            store.recordState(sessionId, true, at)
                            confirmed = true
                            since = at
                        }
                    } else {
                        if (failStreak == 0) firstFailAt = wallStart
                        failStreak++
                        if (confirmed == null) {
                            store.recordState(sessionId, false, now0)
                            confirmed = false
                            since = now0
                        } else if (confirmed == true && failStreak >= s.failThreshold) {
                            store.recordState(sessionId, false, firstFailAt)
                            confirmed = false
                            since = firstFailAt
                        }
                    }

                    if (r.ok) store.addPing(sessionId, r.latencyMs)

                    MonitorState.flow.value = Live(
                        running = true,
                        sessionId = sessionId,
                        online = confirmed,
                        sinceMs = since,
                        latencyMs = r.latencyMs,
                        host = r.host,
                        lastCheckMs = now,
                        checks = checks,
                    )
                    // Re-posting every cycle also brings the notification back if it was swiped away.
                    nm.notify(NOTIF_ID, buildNotification())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Never let the loop die.
                }
                val spent = SystemClock.elapsedRealtime() - t0
                delay(max(500L, s.intervalSec * 1000L - spent))
            }
        }
    }

    private fun shutdown() {
        val app = application as NetPulseApp
        val job = loop
        loop = null
        scope.launch {
            job?.cancelAndJoin()
            app.store.closeOpenSession(System.currentTimeMillis())
            app.settings.monitoring = false
            MonitorState.flow.value = Live()
            releaseWakeLock()
            withContext(Dispatchers.Main) {
                ServiceCompat.stopForeground(this@MonitorService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun applyWakeLock(enabled: Boolean) {
        if (enabled) {
            if (wakeLock?.isHeld != true) {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "netpulse:monitor").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val live = MonitorState.flow.value
        val (color, title) = when (live.online) {
            true -> GREEN to getString(R.string.notif_online)
            false -> RED to getString(R.string.notif_offline)
            null -> GRAY to getString(R.string.notif_waiting)
        }
        val text = when {
            live.online == true && live.host != null ->
                getString(R.string.latency_via, live.latencyMs, live.host)
            live.online == false -> getString(R.string.no_reply)
            else -> getString(R.string.status_waiting)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val since = if (live.sinceMs > 0) live.sinceMs else System.currentTimeMillis()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_net)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(true)
            .setWhen(since)
            .setUsesChronometer(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
