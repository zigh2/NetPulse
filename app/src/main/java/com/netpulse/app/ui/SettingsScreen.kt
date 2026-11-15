package com.netpulse.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netpulse.app.R
import com.netpulse.app.data.Fmt
import com.netpulse.app.data.SettingsState
import com.netpulse.app.monitor.MonitorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SettingsScreen(vm: MainVM, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s by vm.settings.state.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoring = remember(refresh) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    var hostsText by remember { mutableStateOf(s.hosts.joinToString("\n")) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val msgBackupDone = stringResource(R.string.backup_done)
    val msgBackupFail = stringResource(R.string.backup_failed)
    val msgRestoreDone = stringResource(R.string.restore_done)
    val msgRestoreFail = stringResource(R.string.restore_failed)
    val msgRestoreRunning = stringResource(R.string.restore_running)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) vm.backup(uri) { ok -> onMessage(if (ok) msgBackupDone else msgBackupFail) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )

        Section(stringResource(R.string.set_monitoring)) {
            NumberField(stringResource(R.string.set_interval), s.intervalSec, 1..3600) { v ->
                vm.settings.update { it.copy(intervalSec = v) }
            }
            NumberField(stringResource(R.string.set_timeout), s.timeoutMs, 500..10000) { v ->
                vm.settings.update { it.copy(timeoutMs = v) }
            }
            NumberField(stringResource(R.string.set_threshold), s.failThreshold, 1..10) { v ->
                vm.settings.update { it.copy(failThreshold = v) }
            }
            OutlinedTextField(
                value = hostsText,
                onValueChange = { v ->
                    hostsText = v
                    val parsed = SettingsState.parseHosts(v)
                    if (parsed.isNotEmpty()) vm.settings.update { it.copy(hosts = parsed) }
                },
                label = { Text(stringResource(R.string.set_hosts)) },
                supportingText = { Text(stringResource(R.string.set_hosts_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Section(stringResource(R.string.sec_checks)) {
            SwitchRow(stringResource(R.string.set_http), s.httpCheck) { v ->
                vm.settings.update { it.copy(httpCheck = v) }
            }
            SwitchRow(stringResource(R.string.set_dns), s.dnsCheck) { v ->
                vm.settings.update { it.copy(dnsCheck = v) }
            }
        }

        Section(stringResource(R.string.sec_alerts)) {
            SwitchRow(stringResource(R.string.set_alert_back), s.alertReconnect) { v ->
                vm.settings.update { it.copy(alertReconnect = v) }
            }
            SwitchRow(stringResource(R.string.set_alert_out), s.alertOutage) { v ->
                vm.settings.update { it.copy(alertOutage = v) }
            }
            if (s.alertOutage) {
                NumberField(stringResource(R.string.set_alert_min), s.outageAlertMin, 0..1440) { v ->
                    vm.settings.update { it.copy(outageAlertMin = v) }
                }
            }
        }

        Section(stringResource(R.string.sec_schedule)) {
            SwitchRow(stringResource(R.string.set_schedule), s.scheduleEnabled) { v ->
                vm.settings.update { it.copy(scheduleEnabled = v) }
            }
            if (s.scheduleEnabled) {
                TimeRow(stringResource(R.string.set_from), s.scheduleStartMin) { v ->
                    vm.settings.update { it.copy(scheduleStartMin = v) }
                }
                TimeRow(stringResource(R.string.set_to), s.scheduleEndMin) { v ->
                    vm.settings.update { it.copy(scheduleEndMin = v) }
                }
            }
        }

        Section(stringResource(R.string.set_appearance)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.theme == 0, { vm.settings.update { it.copy(theme = 0) } }, { Text(stringResource(R.string.theme_system)) })
                FilterChip(s.theme == 1, { vm.settings.update { it.copy(theme = 1) } }, { Text(stringResource(R.string.theme_light)) })
                FilterChip(s.theme == 2, { vm.settings.update { it.copy(theme = 2) } }, { Text(stringResource(R.string.theme_dark)) })
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow(stringResource(R.string.set_dynamic), s.dynamicColor) { v ->
                    vm.settings.update { it.copy(dynamicColor = v) }
                }
            }
            Text(
                stringResource(R.string.set_language),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to R.string.lang_system,
                    "en" to R.string.lang_en,
                    "ar" to R.string.lang_ar,
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = s.lang == code,
                        onClick = {
                            if (s.lang != code) {
                                vm.settings.update { it.copy(lang = code) }
                                ctx.findActivity()?.recreate()
                            }
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }

        Section(stringResource(R.string.set_reliability)) {
            SwitchRow(stringResource(R.string.set_wakelock), s.wakeLock) { v ->
                vm.settings.update { it.copy(wakeLock = v) }
            }
            SwitchRow(stringResource(R.string.set_boot), s.resumeOnBoot) { v ->
                vm.settings.update { it.copy(resumeOnBoot = v) }
            }
            Text(
                stringResource(R.string.set_battery),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
            )
            if (ignoring) {
                Text(
                    stringResource(R.string.battery_ok), color = Brand.Green,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    stringResource(R.string.battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val i = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                        try {
                            ctx.startActivity(i)
                        } catch (e: Exception) {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.battery_fix)) }
            }
        }

        Section(stringResource(R.string.sec_data)) {
            NumberField(stringResource(R.string.set_retention), s.retentionDays, 1..3650) { v ->
                vm.settings.update { it.copy(retentionDays = v) }
            }
            OutlinedButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    backupLauncher.launch("netpulse-backup-$stamp.db")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_backup)) }
            OutlinedButton(
                onClick = {
                    if (MonitorState.flow.value.running) onMessage(msgRestoreRunning)
                    else restoreLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_restore)) }
        }

        Section(stringResource(R.string.about)) {
            Text(
                "${stringResource(R.string.app_name)} · ${stringResource(R.string.version_fmt, version)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    vm.restore(uri) { ok -> onMessage(if (ok) msgRestoreDone else msgRestoreFail) }
                }) { Text(stringResource(R.string.btn_restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onChange((minutes - 15 + 1440) % 1440) }) { Text("−") }
        Text(
            Fmt.minutesToHm(minutes),
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        OutlinedButton(onClick = { onChange((minutes + 15) % 1440) }) { Text("+") }
    }
}

@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val digits = v.filter { it.isDigit() }.take(5)
            text = digits
            val n = digits.toIntOrNull()
            if (n != null && n in range) onChange(n)
        },
        label = { Text(label) },
        supportingText = { Text("${range.first} – ${range.last}") },
        isError = parsed == null || parsed !in range,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
