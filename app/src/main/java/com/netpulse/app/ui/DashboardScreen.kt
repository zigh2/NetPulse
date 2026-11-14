package com.netpulse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.netpulse.app.data.computeStats
import com.netpulse.app.monitor.MonitorState

@Composable
fun DashboardScreen(vm: MainVM, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val live by MonitorState.flow.collectAsStateWithLifecycle()
    val periods by vm.allPeriods.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val now by rememberNow()

    val session = sessions.firstOrNull { live.running && it.id == live.sessionId } ?: sessions.firstOrNull()
    val sp = remember(periods, session) { periods.filter { it.sessionId == session?.id } }
    val stats = computeStats(sp, now)

    val (c1, c2) = when {
        !live.running -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.surface
        live.online == true -> Brand.Green to Brand.GreenDeep
        live.online == false -> Brand.Red to Brand.RedDeep
        else -> Brand.Slate to Color(0xFF334155)
    }
    val onCard = if (live.running) Color.White else MaterialTheme.colorScheme.onSurface
    val label = when {
        !live.running -> stringResource(R.string.status_idle)
        live.online == true -> stringResource(R.string.status_online)
        live.online == false -> stringResource(R.string.status_offline)
        else -> stringResource(R.string.status_waiting)
    }
    val sub = when {
        !live.running -> stringResource(R.string.status_idle_sub)
        live.online == true && live.host != null -> stringResource(R.string.latency_via, live.latencyMs, live.host!!)
        live.online == false -> stringResource(R.string.no_reply)
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
                PulseDot(color = onCard, active = live.running && live.online != null)
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
                if (live.running && live.online != null) {
                    Text(
                        Fmt.duration(now - live.sinceMs), color = onCard,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
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

        // ---- stats
        if (session != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stat_uptime), Fmt.percent(stats.uptimePct), Brand.Green, Modifier.weight(1f))
                StatCard(stringResource(R.string.stat_outages), stats.outages.toString(), Brand.Red, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stat_online), Fmt.duration(stats.onlineMs), Brand.Green, Modifier.weight(1f))
                StatCard(stringResource(R.string.stat_offline), Fmt.duration(stats.offlineMs), Brand.Red, Modifier.weight(1f))
            }

            // ---- timeline
            val start = session.startMs
            val end = session.endMs ?: now
            val total = (end - start).coerceAtLeast(1L).toFloat()
            val track = MaterialTheme.colorScheme.surfaceVariant
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.timeline), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.session_label, session.id.toInt()),
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
                        val x0 = (p.startMs - start) / total * size.width
                        val x1 = ((p.endMs ?: now) - start) / total * size.width
                        drawRect(
                            color = if (p.online) Brand.Green else Brand.Red,
                            topLeft = Offset(x0, 0f),
                            size = Size((x1 - x0).coerceAtLeast(1.5f), size.height)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        Fmt.shortDateTime(start), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (session.endMs == null) stringResource(R.string.running) else Fmt.shortDateTime(end),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- session info
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow(stringResource(R.string.started), Fmt.dateTime(session.startMs))
                InfoRow(
                    stringResource(R.string.stopped),
                    session.endMs?.let { Fmt.dateTime(it) } ?: stringResource(R.string.running)
                )
                if (live.running) InfoRow(stringResource(R.string.checks_label), live.checks.toString())
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
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
