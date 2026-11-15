package com.netpulse.app.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netpulse.app.R
import com.netpulse.app.data.Fmt
import com.netpulse.app.data.Period
import com.netpulse.app.export.Exporter
import java.io.File

@Composable
fun HistoryScreen(vm: MainVM, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val items by vm.history.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val sf by vm.sessionFilter.collectAsStateWithLifecycle()
    val st by vm.statusFilter.collectAsStateWithLifecycle()
    val now by rememberNow()

    var exportMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var sessionMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Period?>(null) }
    var showClean by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showDeleteSession by remember { mutableStateOf(false) }

    val msgEmpty = stringResource(R.string.export_empty)
    val msgFail = stringResource(R.string.export_failed)

    fun share(file: File, kind: Exporter.Kind) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = kind.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, null))
    }

    fun doExport(kind: Exporter.Kind) {
        if (items.isEmpty()) {
            onMessage(msgEmpty)
            return
        }
        vm.export(kind) { f -> if (f == null) onMessage(msgFail) else share(f, kind) }
    }

    val selectedSession = sessions.firstOrNull { it.id == sf }

    Column(modifier.fillMaxSize()) {
        // ---- header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { exportMenu = true }) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export))
                }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    Exporter.Kind.entries.forEach { k ->
                        DropdownMenuItem(
                            text = { Text("${stringResource(R.string.export)} ${k.ext.uppercase()}") },
                            onClick = { exportMenu = false; doExport(k) }
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clean_short)) },
                        onClick = { moreMenu = false; showClean = true }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_session)) },
                        enabled = selectedSession?.endMs != null,
                        onClick = { moreMenu = false; showDeleteSession = true }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clear_all)) },
                        onClick = { moreMenu = false; showClear = true }
                    )
                }
            }
        }

        // ---- filters
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(st == null, { vm.statusFilter.value = null }, { Text(stringResource(R.string.filter_all)) })
            FilterChip(st == true, { vm.statusFilter.value = true }, { Text(stringResource(R.string.filter_online)) })
            FilterChip(st == false, { vm.statusFilter.value = false }, { Text(stringResource(R.string.filter_offline)) })
            Box {
                FilterChip(
                    selected = sf != null,
                    onClick = { sessionMenu = true },
                    label = {
                        Text(
                            if (sf == null) stringResource(R.string.all_sessions)
                            else stringResource(R.string.session_label, sf!!.toInt())
                        )
                    },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                )
                DropdownMenu(expanded = sessionMenu, onDismissRequest = { sessionMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_sessions)) },
                        onClick = { sessionMenu = false; vm.sessionFilter.value = null }
                    )
                    sessions.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${stringResource(R.string.session_label, s.id.toInt())}  ·  ${Fmt.shortDateTime(s.startMs)}") },
                            onClick = { sessionMenu = false; vm.sessionFilter.value = s.id }
                        )
                    }
                }
            }
        }

        // ---- list
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.no_data), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val groups = remember(items) { items.groupBy { it.sessionId } }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.hint_long_press),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
                groups.forEach { (sid, ps) ->
                    item(key = "session-$sid") {
                        SessionHeader(
                            sessionId = sid,
                            startMs = sessions.firstOrNull { it.id == sid }?.startMs,
                            avgPing = run {
                                val c = ps.sumOf { it.pingCount }
                                if (c > 0) ps.sumOf { it.pingSum } / c else null
                            }
                        )
                    }
                    items(ps, key = { it.id }) { p ->
                        PeriodRow(p, now, onLongPress = { deleteTarget = p })
                    }
                }
            }
        }
    }

    // ---- dialogs
    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_period_title)) },
            text = { Text(stringResource(R.string.delete_period_msg)) },
            confirmButton = {
                TextButton(onClick = { vm.deletePeriod(p.id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.clear_all)) },
            text = { Text(stringResource(R.string.clear_all_msg)) },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClear = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showDeleteSession) {
        AlertDialog(
            onDismissRequest = { showDeleteSession = false },
            title = { Text(stringResource(R.string.delete_session)) },
            text = { Text(stringResource(R.string.delete_session_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    sf?.let { vm.deleteSession(it) }
                    showDeleteSession = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteSession = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showClean) {
        var secs by remember { mutableStateOf("10") }
        var which by remember { mutableStateOf<Boolean?>(null) }
        val n = secs.toIntOrNull()
        val preview = if (n == null) 0 else items.count {
            !it.isActive && it.durationMs(now) < n * 1000L && (which == null || it.online == which)
        }
        AlertDialog(
            onDismissRequest = { showClean = false },
            title = { Text(stringResource(R.string.clean_short)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.clean_msg))
                    OutlinedTextField(
                        value = secs,
                        onValueChange = { v -> secs = v.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = n == null || n < 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(which == null, { which = null }, { Text(stringResource(R.string.filter_all)) })
                        FilterChip(which == true, { which = true }, { Text(stringResource(R.string.filter_online)) })
                        FilterChip(which == false, { which = false }, { Text(stringResource(R.string.filter_offline)) })
                    }
                    Text(
                        stringResource(R.string.clean_preview, preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = n != null && n >= 1,
                    onClick = {
                        showClean = false
                        vm.cleanShort(n ?: 0, which) { removed ->
                            onMessage(ctx.getString(R.string.clean_result, removed))
                        }
                    }
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { showClean = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SessionHeader(sessionId: Long, startMs: Long?, avgPing: Long?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.session_label, sessionId.toInt()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (startMs != null) {
            Text(
                "  ·  " + Fmt.shortDateTime(startMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (avgPing != null) {
            Text(
                stringResource(R.string.avg_ping_value, avgPing.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeriodRow(p: Period, now: Long, onLongPress: () -> Unit) {
    val color = if (p.online) Brand.Green else Brand.Red
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    if (!p.isActive) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (p.online) R.string.status_online else R.string.status_offline),
                    color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall
                )
                if (p.isActive) {
                    Text(
                        "  •  " + stringResource(R.string.active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                Fmt.range(p.startMs, p.endMs, stringResource(R.string.now_label)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.padding(end = 16.dp), horizontalAlignment = Alignment.End) {
            Text(
                Fmt.duration(p.durationMs(now)),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            p.avgPingMs?.let {
                Text(
                    stringResource(R.string.avg_short, it.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
