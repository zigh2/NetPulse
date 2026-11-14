package com.netpulse.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Session(val id: Long, val startMs: Long, val endMs: Long?)

data class Period(
    val id: Long,
    val sessionId: Long,
    val online: Boolean,
    val startMs: Long,
    val endMs: Long?,
) {
    val isActive: Boolean get() = endMs == null
    fun durationMs(now: Long): Long = ((endMs ?: now) - startMs).coerceAtLeast(0L)
}

data class Stats(
    val onlineMs: Long,
    val offlineMs: Long,
    val outages: Int,
    val longestOutageMs: Long,
) {
    val totalMs: Long get() = onlineMs + offlineMs
    val uptimePct: Double get() = if (totalMs == 0L) 100.0 else onlineMs * 100.0 / totalMs
}

fun computeStats(periods: List<Period>, now: Long): Stats {
    var on = 0L
    var off = 0L
    var outages = 0
    var longest = 0L
    for (p in periods) {
        val d = p.durationMs(now)
        if (p.online) {
            on += d
        } else {
            off += d
            outages++
            if (d > longest) longest = d
        }
    }
    return Stats(on, off, outages, longest)
}

object Fmt {
    fun dateTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    fun shortDateTime(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val d = total / 86400
        val h = (total % 86400) / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val hms = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        return if (d > 0) "${d}d $hms" else hms
    }

    fun percent(p: Double): String = String.format(Locale.US, "%.2f%%", p)
}
