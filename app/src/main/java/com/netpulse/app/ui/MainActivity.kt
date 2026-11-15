package com.netpulse.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netpulse.app.NetPulseApp
import com.netpulse.app.data.LocaleHelper
import com.netpulse.app.monitor.MonitorService
import com.netpulse.app.monitor.MonitorState

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AUTOSTART = "autostart"
    }

    private val vm: MainVM by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutostart(intent)
    }

    private fun handleAutostart(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTOSTART, false) == true && !MonitorState.flow.value.running) {
            MonitorService.start(this, resume = false)
            intent.removeExtra(EXTRA_AUTOSTART)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NetPulseApp

        // If a session should be running but the service was killed, bring it back.
        if (app.settings.monitoring && !MonitorState.flow.value.running) {
            MonitorService.start(this, resume = true)
        }

        handleAutostart(intent)

        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val system = isSystemInDarkTheme()
            val dark = when (settings.theme) {
                1 -> false
                2 -> true
                else -> system
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            NetPulseTheme(dark, settings.dynamicColor) { AppRoot(vm) }
        }
    }
}
