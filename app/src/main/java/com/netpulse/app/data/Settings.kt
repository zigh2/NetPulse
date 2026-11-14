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
