package com.netpulse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netpulse.app.R
import com.netpulse.app.data.Fmt
import com.netpulse.app.data.Net
import com.netpulse.app.data.Stats
import com.netpulse.app.data.computeStatsIn
import com.netpulse.app.data.dashRange
import com.netpulse.app.monitor.HostResult
import com.netpulse.app.monitor.MonitorState

@Composable
fun DashboardScreen(vm: MainVM, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val live by MonitorState.flow.collectAsStateWithLifecycle()
    val periods by vm.allPeriods.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val win by vm.dashWindow.collectAsStateWithLifecycle()
    val dash by vm.dashData.collectAsStateWithLifecycle()
    val now by rememberNow()

    val range = dashRange(win, sessions, live.running, live.sessionId, now)
    val sid = range?.sessionId
    val sp = remember(periods, sid) { if (sid != null) periods.filter { it.sessionId == sid } else periods }
    val stats = if (range != null) computeStatsIn(sp, range.from, range.to, now) else Stats(0, 0, 0, 0)
    val q = dash.quality
    val avgPing = q.avgPing ?: stats.avgPingMs
    val session = sessions.firstOrNull { it.id == sid }

    val (c1, c2) = when {
        !live.running -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.surface
        live.paused -> Brand.Slate to Color(0xFF334155)
        live.online == true -> Brand.Green to Brand.GreenDeep
        live.online == false -> Brand.Red to Brand.RedDeep
        else -> Brand.Slate to Color(0xFF334155)
    }
    val onCard = if (live.running) Color.White else MaterialTheme.colorScheme.onSurface
    val label = when {
        !live.running -> stringResource(R.string.status_idle)
        live.paused -> stringResource(R.string.paused_label)
        live.online == true -> stringResource(R.string.status_online)
        live.online == false -> stringResource(R.string.status_offline)
        else -> stringResource(R.string.status_waiting)
    }
    val netText = if (live.running && !live.paused) netLabel(live.net) else ""
    val sub = when {
        !live.running -> stringResource(R.string.status_idle_sub)
        live.paused -> stringResource(R.string.paused_sub, Fmt.hm(live.resumeAtMs))
        live.online == true && live.host != null ->
            stringResource(R.string.latency_via, live.latencyMs, live.host!!) +
                (if (netText.isNotEmpty()) "  ·  $netText" else "")
        live.online == false -> {
            val cause = if (live.net == Net.NONE) R.string.cause_no_network else R.string.cause_no_internet
            stringResource(cause) + (if (live.net != Net.NONE && netText.isNotEmpty()) "  ·  $netText" else "")
        }
        else -> ""
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- status hero
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(c1, c2)))
                .padding(vertical = 28.dp, horizontal = 20.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PulseDot(color = onCard, active = live.running && !live.paused && live.online != null)
                Spacer(Modifier.height(14.dp))
                Text(
                    label, color = onCard, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
                )
                if (sub.isNotEmpty()) {
                    Text(
                        sub, color = onCard.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (live.running && !live.paused && live.online != null) {
                    Text(
                        Fmt.duration(now - live.sinceMs), color = onCard,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }

        // ---- warnings
        if (live.running && !live.paused) {
            if (live.captive) Warning(stringResource(R.string.captive_warning))
            if (live.dns != null && !live.dns!!.ok && live.online == true) Warning(stringResource(R.string.dns_failing))
        }

        // ---- start / stop
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = if (live.running) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) else ButtonDefaults.buttonColors()
        ) {
            if (live.running) {
                Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.onErrorContainer, RoundedCornerShape(3.dp)))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (live.running) R.string.btn_stop else R.string.btn_start),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
        }

        // ---- window selector
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val names = listOf(R.string.win_session, R.string.win_24h, R.string.win_7d, R.string.win_30d)
            names.forEachIndexed { i, n ->
                FilterChip(win == i, { vm.dashWindow.value = i }, { Text(stringResource(n)) })
            }
        }

        if (range != null) {
            // ---- stats
            StatRow(
                stringResource(R.string.stat_uptime), Fmt.percent(stats.uptimePct), Brand.Green,
                stringResource(R.string.stat_outages), stats.outages.toString(), Brand.Red
            )
            StatRow(
                stringResource(R.string.stat_online), Fmt.duration(stats.onlineMs), Brand.Green,
                stringResource(R.string.stat_offline), Fmt.duration(stats.offlineMs), Brand.Red
            )
            StatRow(
                stringResource(R.string.avg_ping), Fmt.ping(avgPing), MaterialTheme.colorScheme.primary,
                stringResource(R.string.stat_longest_outage), Fmt.duration(stats.longestOutageMs), Brand.Red
            )
            StatRow(
                stringResource(R.string.stat_loss), if (q.checks > 0) Fmt.percent(q.lossPct) else "—", Brand.Amber,
                stringResource(R.string.stat_jitter), Fmt.ping(q.jitter), Brand.Amber
            )
            StatRow(
                stringResource(R.string.stat_min_ping), Fmt.ping(q.minPing), MaterialTheme.colorScheme.primary,
                stringResource(R.string.stat_max_ping), Fmt.ping(q.maxPing), MaterialTheme.colorScheme.primary
            )

            // ---- timeline
            val track = MaterialTheme.colorScheme.surfaceVariant
            val total = (range.to - range.from).coerceAtLeast(1L).toFloat()
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.timeline), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (sid != null) stringResource(R.string.session_label, sid.toInt())
                        else stringResource(listOf(0, R.string.win_24h, R.string.win_7d, R.string.win_30d)[win]),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Canvas(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    drawRect(track, size = size)
                    sp.forEach { p ->
                        val s0 = maxOf(p.startMs, range.from)
                        val e0 = minOf(p.endMs ?: now, range.to)
                        if (e0 > s0) {
                            val x0 = (s0 - range.from) / total * size.width
                            val x1 = (e0 - range.from) / total * size.width
                            drawRect(
                                color = if (p.online) Brand.Green else Brand.Red,
                                topLeft = Offset(x0, 0f),
                                size = Size((x1 - x0).coerceAtLeast(1.5f), size.height)
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        Fmt.shortDateTime(range.from), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (session != null && session.endMs == null && win == 0) stringResource(R.string.running)
                        else if (win != 0) stringResource(R.string.now_label)
                        else Fmt.shortDateTime(range.to),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- ping chart
            Card {
                Text(
                    stringResource(R.string.card_ping_chart), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (dash.chart.size >= 2) {
                    PingChart(dash.chart, Modifier.padding(top = 12.dp).fillMaxWidth().height(130.dp))
                } else {
                    Text(
                        stringResource(R.string.no_chart),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            // ---- servers (live)
            if (live.running && !live.paused && live.hosts.isNotEmpty()) {
                Card {
                    Text(
                        stringResource(R.string.card_servers), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    live.hosts.forEach { ServerRow(it) }
                    live.http?.let { ServerRow(it) }
                    live.dns?.let { ServerRow(it) }
                }
            }

            // ---- session info
            if (win == 0 && session != null) {
                Card {
                    InfoRow(stringResource(R.string.started), Fmt.dateTime(session.startMs))
                    InfoRow(
                        stringResource(R.string.stopped),
                        session.endMs?.let { Fmt.dateTime(it) } ?: stringResource(R.string.running)
                    )
                    if (live.running && !live.paused) {
                        InfoRow(stringResource(R.string.checks_label), live.checks.toString())
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.no_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun StatRow(t1: String, v1: String, a1: Color, t2: String, v2: String, a2: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(t1, v1, a1, Modifier.weight(1f))
        StatCard(t2, v2, a2, Modifier.weight(1f))
    }
}

@Composable
private fun Warning(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ServerRow(h: HostResult) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(if (h.ok) Brand.Green else Brand.Red, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(h.host, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            h.method, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (h.ok) "${h.latencyMs} ms" else "—",
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
