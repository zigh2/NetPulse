package com.netpulse.app.monitor

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

/**
 * Probes all servers in parallel. Per server: ICMP ping first, then TCP connect to port 53 and 443
 * as a fallback (some networks block ICMP). Optional extra checks: HTTP (generate_204) and DNS lookup.
 * The connection counts as online when at least one *server* answers.
 */
object Pinger {
    data class Result(
        val ok: Boolean,
        val host: String?,
        val latencyMs: Long,
        val hosts: List<HostResult>,
        val http: HostResult?,
        val dns: HostResult?,
        val captive: Boolean,
    )

    private val pool = Executors.newCachedThreadPool()
    private val timeRegex = Regex("time[=<]([0-9.]+)")
    private const val HTTP_URL = "http://connectivitycheck.gstatic.com/generate_204"
    private const val DNS_NAME = "google.com"

    fun probe(hosts: List<String>, timeoutMs: Int, httpCheck: Boolean, dnsCheck: Boolean): Result {
        val tasks = ArrayList<Callable<HostResult>>()
        hosts.forEach { h -> tasks.add(Callable { probeHost(h, timeoutMs) }) }
        if (httpCheck) tasks.add(Callable { http(timeoutMs) })
        if (dnsCheck) tasks.add(Callable { dns() })

        val results = ArrayList<HostResult>()
        try {
            val futures = pool.invokeAll(tasks, timeoutMs * 4L + 1000L, TimeUnit.MILLISECONDS)
            futures.forEachIndexed { i, f ->
                val r = try {
                    f.get()
                } catch (e: Exception) {
                    null
                }
                val fallbackHost = if (i < hosts.size) hosts[i] else if (httpCheck && i == hosts.size) "HTTP" else "DNS"
                results.add(r ?: HostResult(fallbackHost, false, -1, "timeout"))
            }
        } catch (e: Exception) {
            return Result(false, null, -1, emptyList(), null, null, false)
        }

        val servers = results.take(hosts.size)
        var idx = hosts.size
        val httpRes = if (httpCheck) results.getOrNull(idx++) else null
        val dnsRes = if (dnsCheck) results.getOrNull(idx) else null

        val best = servers.filter { it.ok }.minByOrNull { it.latencyMs }
        val captive = best != null && httpRes != null && !httpRes.ok && httpRes.code > 0
        return Result(best != null, best?.host, best?.latencyMs ?: -1, servers, httpRes, dnsRes, captive)
    }

    private fun probeHost(host: String, timeoutMs: Int): HostResult {
        icmp(host, timeoutMs)?.let { return HostResult(host, true, it, "ICMP") }
        for (port in intArrayOf(53, 443)) {
            tcp(host, port, timeoutMs)?.let { return HostResult(host, true, it, "TCP $port") }
        }
        return HostResult(host, false, -1, "—")
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

    private fun http(timeoutMs: Int): HostResult {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            val c = URL(HTTP_URL).openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.useCaches = false
            val code = try {
                c.responseCode
            } finally {
                c.disconnect()
            }
            HostResult("HTTP", code == 204, max(1L, SystemClock.elapsedRealtime() - t0), "HTTP $code", code)
        } catch (e: Exception) {
            HostResult("HTTP", false, -1, "HTTP")
        }
    }

    private fun dns(): HostResult {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            InetAddress.getByName(DNS_NAME)
            HostResult("DNS", true, max(1L, SystemClock.elapsedRealtime() - t0), "DNS")
        } catch (e: Exception) {
            HostResult("DNS", false, -1, "DNS")
        }
    }
}
