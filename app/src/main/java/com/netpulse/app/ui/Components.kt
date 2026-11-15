package com.netpulse.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.netpulse.app.R
import com.netpulse.app.data.ChartPoint
import com.netpulse.app.data.Net
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Wall-clock "now" that ticks every second. */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000)
    }
}

@Composable
fun PulseDot(color: Color, active: Boolean, size: Int = 14) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale"
    )
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha"
    )
    Box(
        Modifier
            .size(size.dp)
            .scale(if (active) s else 1f)
            .alpha(if (active) a else 1f)
            .background(color, CircleShape)
    )
}

@Composable
fun StatCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Box(Modifier.size(width = 22.dp, height = 4.dp).background(accent, RoundedCornerShape(2.dp)))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp).fillMaxWidth()
        )
    }
}

@Composable
fun netLabel(net: Int): String = when (net) {
    Net.WIFI -> stringResource(R.string.net_wifi)
    Net.CELL -> stringResource(R.string.net_cell)
    Net.ETH -> stringResource(R.string.net_eth)
    Net.NONE -> stringResource(R.string.cause_no_network)
    Net.OTHER -> stringResource(R.string.net_other)
    else -> ""
}

/** Average ping (solid) and max ping (faint) over time; red ticks mark minutes with lost checks. */
@Composable
fun PingChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val faint = line.copy(alpha = 0.28f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val yMax = remember(points) {
        val m = points.maxOfOrNull { it.max ?: it.avg ?: 0L } ?: 0L
        (m.coerceAtLeast(10L) * 1.1).toLong()
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0..2) {
                val gy = h * i / 2f
                drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            if (points.size < 2) return@Canvas
            val t0 = points.first().ts
            val span = (points.last().ts - t0).coerceAtLeast(1L).toFloat()
            fun px(p: ChartPoint) = (p.ts - t0) / span * w
            fun py(v: Long) = h - (v.toFloat() / yMax) * h

            val maxPath = Path()
            val avgPath = Path()
            var maxStarted = false
            var avgStarted = false
            for (p in points) {
                val m = p.max
                if (m == null) {
                    maxStarted = false
                } else if (!maxStarted) {
                    maxPath.moveTo(px(p), py(m)); maxStarted = true
                } else {
                    maxPath.lineTo(px(p), py(m))
                }
                val a = p.avg
                if (a == null) {
                    avgStarted = false
                } else {
                    if (!avgStarted) {
                        avgPath.moveTo(px(p), py(a)); avgStarted = true
                    } else {
                        avgPath.lineTo(px(p), py(a))
                    }
                    drawCircle(line, radius = 2.5.dp.toPx(), center = Offset(px(p), py(a)))
                }
                if (p.lossPct > 0.0) {
                    val lh = 4.dp.toPx() + (h * 0.3f * (p.lossPct / 100.0)).toFloat()
                    drawLine(
                        Brand.Red, Offset(px(p), h), Offset(px(p), h - lh),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                    )
                }
            }
            drawPath(maxPath, faint, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(avgPath, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(
            "$yMax ms", color = label, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            "0", color = label, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
