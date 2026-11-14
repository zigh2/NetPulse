@file:OptIn(ExperimentalCoroutinesApi::class)

package com.netpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netpulse.app.NetPulseApp
import com.netpulse.app.data.Period
import com.netpulse.app.data.Session
import com.netpulse.app.export.Exporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainVM(app: Application) : AndroidViewModel(app) {
    private val netApp = app as NetPulseApp
    val store = netApp.store
    val settings = netApp.settings

    val sessionFilter = MutableStateFlow<Long?>(null)
    val statusFilter = MutableStateFlow<Boolean?>(null)

    val sessions: StateFlow<List<Session>> = store.changes
        .mapLatest { store.sessions() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPeriods: StateFlow<List<Period>> = store.changes
        .mapLatest { store.periods(null) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<Period>> = combine(allPeriods, sessionFilter, statusFilter) { ps, s, st ->
        ps.filter { (s == null || it.sessionId == s) && (st == null || it.online == st) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun <T> io(block: () -> T, done: (T) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { block() }
            done(r)
        }
    }

    fun deletePeriod(id: Long) = io({ store.deletePeriod(id) }, { })

    fun cleanShort(seconds: Int, online: Boolean?, done: (Int) -> Unit) =
        io({ store.deleteShorterThan(seconds, online) }, done)

    fun deleteSession(id: Long) = io({ store.deleteSession(id) }, {
        if (sessionFilter.value == id) sessionFilter.value = null
    })

    fun clearAll() = io({ store.clearFinished() }, { sessionFilter.value = null })

    /** Exports what the History tab currently shows. Calls [done] with null if there is nothing to export. */
    fun export(kind: Exporter.Kind, done: (File?) -> Unit) {
        val rows = history.value
        if (rows.isEmpty()) {
            done(null)
            return
        }
        val ids = rows.map { it.sessionId }.toSet()
        val sess = sessions.value.filter { it.id in ids }
        io({
            try {
                Exporter.export(getApplication<Application>(), kind, sess, rows)
            } catch (e: Exception) {
                null
            }
        }, done)
    }
}
