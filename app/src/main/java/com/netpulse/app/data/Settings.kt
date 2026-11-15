package com.netpulse.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class SettingsState(
    val intervalSec: Int = 5,
    val timeoutMs: Int = 2000,
    val hosts: List<String> = DEFAULT_HOSTS,
    val failThreshold: Int = 2,
    /** 0 = system, 1 = light, 2 = dark */
    val theme: Int = 0,
    val wakeLock: Boolean = true,
    val resumeOnBoot: Boolean = true,
    // alerts
    val alertReconnect: Boolean = true,
    val alertOutage: Boolean = true,
    val outageAlertMin: Int = 2,
    // extra checks
    val httpCheck: Boolean = false,
    val dnsCheck: Boolean = false,
    // schedule (minutes after midnight)
    val scheduleEnabled: Boolean = false,
    val scheduleStartMin: Int = 20 * 60,
    val scheduleEndMin: Int = 0,
    // data
    val retentionDays: Int = 90,
    // look & language
    val dynamicColor: Boolean = false,
    /** "system", "en" or "ar" */
    val lang: String = "system",
) {
    companion object {
        val DEFAULT_HOSTS = listOf("8.8.8.8", "1.1.1.1", "9.9.9.9")

        fun parseHosts(raw: String): List<String> =
            raw.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("netpulse", Context.MODE_PRIVATE)

    val state = MutableStateFlow(load())

    private fun load(): SettingsState {
        val d = SettingsState()
        val hosts = SettingsState.parseHosts(sp.getString("hosts", null) ?: d.hosts.joinToString("\n"))
        return SettingsState(
            intervalSec = sp.getInt("interval", d.intervalSec).coerceIn(1, 3600),
            timeoutMs = sp.getInt("timeout", d.timeoutMs).coerceIn(500, 10000),
            hosts = hosts.ifEmpty { d.hosts },
            failThreshold = sp.getInt("threshold", d.failThreshold).coerceIn(1, 10),
            theme = sp.getInt("theme", d.theme).coerceIn(0, 2),
            wakeLock = sp.getBoolean("wakelock", d.wakeLock),
            resumeOnBoot = sp.getBoolean("boot", d.resumeOnBoot),
            alertReconnect = sp.getBoolean("alert_back", d.alertReconnect),
            alertOutage = sp.getBoolean("alert_out", d.alertOutage),
            outageAlertMin = sp.getInt("alert_min", d.outageAlertMin).coerceIn(0, 1440),
            httpCheck = sp.getBoolean("http", d.httpCheck),
            dnsCheck = sp.getBoolean("dns", d.dnsCheck),
            scheduleEnabled = sp.getBoolean("sched", d.scheduleEnabled),
            scheduleStartMin = sp.getInt("sched_start", d.scheduleStartMin).coerceIn(0, 1439),
            scheduleEndMin = sp.getInt("sched_end", d.scheduleEndMin).coerceIn(0, 1439),
            retentionDays = sp.getInt("retention", d.retentionDays).coerceIn(1, 3650),
            dynamicColor = sp.getBoolean("dynamic", d.dynamicColor),
            lang = sp.getString("lang", d.lang) ?: d.lang,
        )
    }

    fun update(transform: (SettingsState) -> SettingsState) {
        val n = transform(state.value)
        state.value = n
        sp.edit()
            .putInt("interval", n.intervalSec)
            .putInt("timeout", n.timeoutMs)
            .putString("hosts", n.hosts.joinToString("\n"))
            .putInt("threshold", n.failThreshold)
            .putInt("theme", n.theme)
            .putBoolean("wakelock", n.wakeLock)
            .putBoolean("boot", n.resumeOnBoot)
            .putBoolean("alert_back", n.alertReconnect)
            .putBoolean("alert_out", n.alertOutage)
            .putInt("alert_min", n.outageAlertMin)
            .putBoolean("http", n.httpCheck)
            .putBoolean("dns", n.dnsCheck)
            .putBoolean("sched", n.scheduleEnabled)
            .putInt("sched_start", n.scheduleStartMin)
            .putInt("sched_end", n.scheduleEndMin)
            .putInt("retention", n.retentionDays)
            .putBoolean("dynamic", n.dynamicColor)
            .putString("lang", n.lang)
            .apply()
    }

    /** True while a monitoring session is meant to be running (survives process death / reboot). */
    var monitoring: Boolean
        get() = sp.getBoolean("monitoring", false)
        set(v) { sp.edit().putBoolean("monitoring", v).apply() }

    var lastHeartbeat: Long
        get() = sp.getLong("heartbeat", 0L)
        set(v) { sp.edit().putLong("heartbeat", v).apply() }
}
