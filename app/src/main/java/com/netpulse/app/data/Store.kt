package com.netpulse.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny SQLite store: sessions (start/stop of a test) and periods (online/offline spans).
 */
class Store(context: Context) : SQLiteOpenHelper(context, "netpulse.db", null, 1) {

    /** Incremented after every write so the UI can reload. */
    val changes = MutableStateFlow(0)

    private fun bump() {
        changes.value = changes.value + 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "start_ms INTEGER NOT NULL, end_ms INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE periods(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, " +
                "online INTEGER NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER)"
        )
        db.execSQL("CREATE INDEX idx_periods_session ON periods(session_id, start_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private val pCols = arrayOf("id", "session_id", "online", "start_ms", "end_ms")

    private fun Cursor.toPeriod() = Period(
        id = getLong(0),
        sessionId = getLong(1),
        online = getInt(2) == 1,
        startMs = getLong(3),
        endMs = if (isNull(4)) null else getLong(4),
    )

    // ---------- writes ----------

    @Synchronized
    fun startSession(now: Long): Long {
        val id = writableDatabase.insert("sessions", null, ContentValues().apply { put("start_ms", now) })
        bump()
        return id
    }

    /** Closes any open session and open period at the given time. */
    @Synchronized
    fun closeOpenSession(at: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE periods SET end_ms = MAX(?, start_ms) WHERE end_ms IS NULL", arrayOf<Any>(at))
            db.execSQL("UPDATE sessions SET end_ms = MAX(?, start_ms) WHERE end_ms IS NULL", arrayOf<Any>(at))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    /** Ends the open period of [sessionId] at [at] and opens a new one with the given state. */
    @Synchronized
    fun recordState(sessionId: Long, online: Boolean, at: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE periods SET end_ms = MAX(?, start_ms) WHERE session_id = ? AND end_ms IS NULL",
                arrayOf<Any>(at, sessionId)
            )
            db.insert("periods", null, ContentValues().apply {
                put("session_id", sessionId)
                put("online", if (online) 1 else 0)
                put("start_ms", at)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    /**
     * Deletes a finished period. The hole is absorbed by the previous period (or the next one if
     * there is no previous); if that makes two same-state periods adjacent they are merged.
     */
    @Synchronized
    fun deletePeriod(id: Long): Boolean {
        val db = writableDatabase
        var ok: Boolean
        db.beginTransaction()
        try {
            ok = deleteLocked(db, id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (ok) bump()
        return ok
    }

    /** Removes finished periods shorter than [seconds] (optionally only online or offline ones). */
    @Synchronized
    fun deleteShorterThan(seconds: Int, online: Boolean?): Int {
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            while (true) {
                val where = StringBuilder("end_ms IS NOT NULL AND (end_ms - start_ms) < ?")
                val args = mutableListOf((seconds * 1000L).toString())
                if (online != null) {
                    where.append(" AND online = ?")
                    args.add(if (online) "1" else "0")
                }
                val id = db.query(
                    "periods", arrayOf("id"), where.toString(), args.toTypedArray(),
                    null, null, "(end_ms - start_ms) ASC", "1"
                ).use { if (it.moveToFirst()) it.getLong(0) else null } ?: break
                if (!deleteLocked(db, id)) break
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (count > 0) bump()
        return count
    }

    @Synchronized
    fun deleteSession(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("periods", "session_id = ?", arrayOf(id.toString()))
            db.delete("sessions", "id = ? AND end_ms IS NOT NULL", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    /** Deletes all finished data; the running session (if any) keeps its active period. */
    @Synchronized
    fun clearFinished() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("periods", "end_ms IS NOT NULL", null)
            db.delete("sessions", "end_ms IS NOT NULL", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    // ---------- reads ----------

    @Synchronized
    fun sessions(): List<Session> =
        readableDatabase.query("sessions", arrayOf("id", "start_ms", "end_ms"), null, null, null, null, "start_ms DESC")
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Session(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2)))
                    }
                }
            }

    /** Newest first. */
    @Synchronized
    fun periods(sessionId: Long?): List<Period> {
        val sel = if (sessionId == null) null else "session_id = ?"
        val args = if (sessionId == null) null else arrayOf(sessionId.toString())
        return readableDatabase.query("periods", pCols, sel, args, null, null, "start_ms DESC, id DESC").use { c ->
            buildList { while (c.moveToNext()) add(c.toPeriod()) }
        }
    }

    // ---------- helpers ----------

    private fun queryOne(db: SQLiteDatabase, sel: String, args: Array<String>, order: String?): Period? =
        db.query("periods", pCols, sel, args, null, null, order, "1")
            .use { if (it.moveToFirst()) it.toPeriod() else null }

    private fun deleteLocked(db: SQLiteDatabase, id: Long): Boolean {
        val p = queryOne(db, "id = ?", arrayOf(id.toString()), null) ?: return false
        val pEnd = p.endMs ?: return false
        val prev = queryOne(
            db, "session_id = ? AND start_ms < ?",
            arrayOf(p.sessionId.toString(), p.startMs.toString()), "start_ms DESC"
        )
        val next = queryOne(
            db, "session_id = ? AND start_ms > ?",
            arrayOf(p.sessionId.toString(), p.startMs.toString()), "start_ms ASC"
        )
        if (prev != null) {
            if (next != null && next.online == prev.online) {
                val cv = ContentValues()
                val nEnd = next.endMs
                if (nEnd == null) cv.putNull("end_ms") else cv.put("end_ms", nEnd)
                db.update("periods", cv, "id = ?", arrayOf(prev.id.toString()))
                db.delete("periods", "id = ?", arrayOf(next.id.toString()))
            } else {
                db.update("periods", ContentValues().apply { put("end_ms", pEnd) }, "id = ?", arrayOf(prev.id.toString()))
            }
        } else if (next != null) {
            db.update("periods", ContentValues().apply { put("start_ms", p.startMs) }, "id = ?", arrayOf(next.id.toString()))
        }
        db.delete("periods", "id = ?", arrayOf(p.id.toString()))
        return true
    }
}
