package com.netpulse.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netpulse.app.R
import com.netpulse.app.data.SettingsState

@Composable
fun SettingsScreen(vm: MainVM, modifier: Modifier = Modifier) {
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

        Section(stringResource(R.string.set_appearance)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.theme == 0, { vm.settings.update { it.copy(theme = 0) } }, { Text(stringResource(R.string.theme_system)) })
                FilterChip(s.theme == 1, { vm.settings.update { it.copy(theme = 1) } }, { Text(stringResource(R.string.theme_light)) })
                FilterChip(s.theme == 2, { vm.settings.update { it.copy(theme = 2) } }, { Text(stringResource(R.string.theme_dark)) })
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

        Section(stringResource(R.string.about)) {
            Text(
                "${stringResource(R.string.app_name)} · ${stringResource(R.string.version_fmt, version)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
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
