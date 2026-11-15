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
    /** Sum of successful ping latencies (ms) and how many there were. */
    val pingSum: Long = 0L,
    val pingCount: Int = 0,
) {
    val avgPingMs: Long? get() = if (pingCount > 0) pingSum / pingCount else null
    val isActive: Boolean get() = endMs == null
    fun durationMs(now: Long): Long = ((endMs ?: now) - startMs).coerceAtLeast(0L)
}

data class Stats(
    val onlineMs: Long,
    val offlineMs: Long,
    val outages: Int,
    val longestOutageMs: Long,
    val pingSum: Long = 0L,
    val pingCount: Int = 0,
) {
    val avgPingMs: Long? get() = if (pingCount > 0) pingSum / pingCount else null
    val totalMs: Long get() = onlineMs + offlineMs
    val uptimePct: Double get() = if (totalMs == 0L) 100.0 else onlineMs * 100.0 / totalMs
}

fun computeStats(periods: List<Period>, now: Long): Stats {
    var on = 0L
    var off = 0L
    var outages = 0
    var longest = 0L
    var pSum = 0L
    var pCount = 0
    for (p in periods) {
        pSum += p.pingSum
        pCount += p.pingCount
        val d = p.durationMs(now)
        if (p.online) {
            on += d
        } else {
            off += d
            outages++
            if (d > longest) longest = d
        }
    }
    return Stats(on, off, outages, longest, pSum, pCount)
}

object Fmt {
    fun dateTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    fun shortDateTime(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    fun time(ms: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    private fun dayKey(ms: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    /** "11-14 23:07:35 → 23:37:35" (same day: date shown once); open end shows [nowLabel]. */
    fun range(start: Long, end: Long?, nowLabel: String): String = when {
        end == null -> "${shortDateTime(start)}  →  $nowLabel"
        dayKey(start) == dayKey(end) -> "${shortDateTime(start)}  →  ${time(end)}"
        else -> "${shortDateTime(start)}  →  ${shortDateTime(end)}"
    }

    fun ping(ms: Long?): String = if (ms == null) "—" else "$ms ms"

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
