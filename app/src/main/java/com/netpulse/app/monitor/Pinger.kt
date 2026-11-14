package com.netpulse.app.monitor

import android.os.SystemClock
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

/**
 * Probes all hosts in parallel and succeeds as soon as one answers.
 * Per host: ICMP ping first, then TCP connect to port 53 and 443 as a fallback
 * (some networks block ICMP).
 */
object Pinger {
    data class Result(val ok: Boolean, val host: String?, val latencyMs: Long)

    private val pool = Executors.newCachedThreadPool()
    private val timeRegex = Regex("time[=<]([0-9.]+)")

    fun probe(hosts: List<String>, timeoutMs: Int): Result {
        if (hosts.isEmpty()) return Result(false, null, -1)
        val tasks = hosts.map { h ->
            Callable<Result> { probeHost(h, timeoutMs) ?: throw IOException("no reply from $h") }
        }
        return try {
            pool.invokeAny(tasks)
        } catch (e: Exception) {
            Result(false, null, -1)
        }
    }

    private fun probeHost(host: String, timeoutMs: Int): Result? {
        icmp(host, timeoutMs)?.let { return Result(true, host, it) }
        for (port in intArrayOf(53, 443)) {
            tcp(host, port, timeoutMs)?.let { return Result(true, host, it) }
        }
        return null
    }

    private fun icmp(host: String, timeoutMs: Int): Long? = try {
        val sec = max(1, ceil(timeoutMs / 1000.0).toInt())
        val t0 = SystemClock.elapsedRealtime()
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", sec.toString(), host)
            .redirectErrorStream(true)
            .start()
        if (!p.waitFor(sec + 2L, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            null
        } else if (p.exitValue() == 0) {
            val out = p.inputStream.bufferedReader().readText()
            timeRegex.find(out)?.groupValues?.get(1)?.toDoubleOrNull()?.let { max(1L, it.toLong()) }
                ?: max(1L, SystemClock.elapsedRealtime() - t0)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    private fun tcp(host: String, port: Int, timeoutMs: Int): Long? = try {
        val t0 = SystemClock.elapsedRealtime()
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        max(1L, SystemClock.elapsedRealtime() - t0)
    } catch (e: Exception) {
        null
    }
}
