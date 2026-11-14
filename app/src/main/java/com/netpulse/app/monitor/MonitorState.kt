package com.netpulse.app.monitor

import kotlinx.coroutines.flow.MutableStateFlow

data class Live(
    val running: Boolean = false,
    val sessionId: Long = 0L,
    /** null = no confirmed state yet. */
    val online: Boolean? = null,
    val sinceMs: Long = 0L,
    val latencyMs: Long = -1L,
    val host: String? = null,
    val lastCheckMs: Long = 0L,
    val checks: Int = 0,
)

/** In-process live status shared between the service and the UI. */
object MonitorState {
    val flow = MutableStateFlow(Live())
}
