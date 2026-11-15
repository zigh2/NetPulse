package com.netpulse.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netpulse.app.R
import com.netpulse.app.monitor.MonitorService
import com.netpulse.app.monitor.MonitorState
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: MainVM) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val live by MonitorState.flow.collectAsStateWithLifecycle()
    val onMessage: (String) -> Unit = { m -> scope.launch { snack.showSnackbar(m) } }

    // On Android 13+ ask for the notification permission, then start regardless of the answer.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        MonitorService.start(ctx, resume = false)
    }
    val onToggle: () -> Unit = {
        if (live.running) {
            MonitorService.stop(ctx)
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            MonitorService.start(ctx, resume = false)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_dashboard)) }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_history)) }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> DashboardScreen(vm, onToggle)
                1 -> HistoryScreen(vm, onMessage)
                else -> SettingsScreen(vm, onMessage)
            }
        }
    }
}
