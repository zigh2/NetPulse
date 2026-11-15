package com.netpulse.app.monitor

import kotlinx.coroutines.flow.MutableStateFlow

/** Result of one probe against one target. [code] is the HTTP status when relevant. */
data class HostResult(
    val host: String,
    val ok: Boolean,
    val latencyMs: Long,
    val method: String,
    val code: Int = 0,
)

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
    val net: Int = 0,
    val hosts: List<HostResult> = emptyList(),
    val http: HostResult? = null,
    val dns: HostResult? = null,
    /** Servers answer but the HTTP check got something other than 204 (captive portal / limited network). */
    val captive: Boolean = false,
    /** True while the service runs but monitoring is paused by the schedule. */
    val paused: Boolean = false,
    val resumeAtMs: Long = 0L,
)

/** In-process live status shared between the service, the UI, the widget and the tile. */
object MonitorState {
    val flow = MutableStateFlow(Live())
}
