package com.netpulse.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Transport type at the start of a period. */
object Net {
    const val UNKNOWN = 0
    const val WIFI = 1
    const val CELL = 2
    const val ETH = 3
    const val NONE = 4
    const val OTHER = 5
}

/** Why a period is offline. */
object Cause {
    const val NONE = 0
    const val NO_NETWORK = 1
    const val NO_INTERNET = 2
}

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
    val net: Int = Net.UNKNOWN,
    val cause: Int = Cause.NONE,
    val note: String = "",
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

/** Aggregated per-check quality numbers (from the per-minute buckets). */
data class Quality(
    val checks: Long,
    val fails: Long,
    val pingSum: Long,
    val pingCount: Long,
    val minPing: Long?,
    val maxPing: Long?,
    val jitSum: Long,
    val jitCount: Long,
) {
    val lossPct: Double get() = if (checks == 0L) 0.0 else fails * 100.0 / checks
    val avgPing: Long? get() = if (pingCount > 0) pingSum / pingCount else null
    val jitter: Long? get() = if (jitCount > 0) jitSum / jitCount else null

    companion object {
        val EMPTY = Quality(0, 0, 0, 0, null, null, 0, 0)
    }
}

data class ChartPoint(val ts: Long, val avg: Long?, val max: Long?, val lossPct: Double)

data class DashData(val quality: Quality = Quality.EMPTY, val chart: List<ChartPoint> = emptyList())

/** The time range the dashboard looks at. [sessionId] is set for the "session" window. */
data class DashRange(val sessionId: Long?, val from: Long, val to: Long)

/** window: 0 = session, 1 = 24 h, 2 = 7 days, 3 = 30 days. */
fun dashRange(window: Int, sessions: List<Session>, liveRunning: Boolean, liveSessionId: Long, now: Long): DashRange? {
    if (window == 0) {
        val s = sessions.firstOrNull { liveRunning && it.id == liveSessionId } ?: sessions.firstOrNull() ?: return null
        return DashRange(s.id, s.startMs, s.endMs ?: now)
    }
    val hours = when (window) {
        1 -> 24L
        2 -> 24L * 7
        else -> 24L * 30
    }
    return DashRange(null, now - hours * 3_600_000L, now)
}

fun computeStats(periods: List<Period>, now: Long): Stats = computeStatsIn(periods, Long.MIN_VALUE, Long.MAX_VALUE, now)

/** Stats clipped to [from, to]. The ping figures are not clipped (a period counts fully if it overlaps). */
fun computeStatsIn(periods: List<Period>, from: Long, to: Long, now: Long): Stats {
    var on = 0L
    var off = 0L
    var outages = 0
    var longest = 0L
    var pSum = 0L
    var pCount = 0
    for (p in periods) {
        val s = maxOf(p.startMs, from)
        val e = minOf(p.endMs ?: now, to)
        if (e <= s) continue
        val d = e - s
        pSum += p.pingSum
        pCount += p.pingCount
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

    fun hm(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    fun minutesToHm(min: Int): String = String.format(Locale.US, "%02d:%02d", (min / 60) % 24, min % 60)

    private fun dayKey(ms: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    /** "11-14 23:07:35 → 23:37:35" (same day: date shown once); an open end shows [nowLabel]. */
    fun range(start: Long, end: Long?, nowLabel: String): String = when {
        end == null -> "${shortDateTime(start)}  →  $nowLabel"
        dayKey(start) == dayKey(end) -> "${shortDateTime(start)}  →  ${time(end)}"
        else -> "${shortDateTime(start)}  →  ${shortDateTime(end)}"
    }

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

    fun ping(ms: Long?): String = if (ms == null) "—" else "$ms ms"
}

/** Daily monitoring window helper (start/end are minutes after midnight; start > end wraps midnight). */
object Schedule {
    private fun minuteOfDay(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun inWindow(ms: Long, start: Int, end: Int): Boolean {
        if (start == end) return true
        val m = minuteOfDay(ms)
        return if (start < end) m in start until end else (m >= start || m < end)
    }

    fun nextStart(ms: Long, start: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, (start / 60) % 24)
            set(Calendar.MINUTE, start % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= ms) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}
