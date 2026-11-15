package com.netpulse.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

/**
 * Small SQLite store:
 *  - sessions: start/stop of a test
 *  - periods:  online/offline spans (with ping totals, network type, cause and a note)
 *  - buckets:  per-minute check statistics (loss, ping min/avg/max, jitter) for charts and quality
 */
class Store(context: Context) : SQLiteOpenHelper(context, "netpulse.db", null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 3
    }

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
                "online INTEGER NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER, " +
                "ping_sum INTEGER NOT NULL DEFAULT 0, ping_count INTEGER NOT NULL DEFAULT 0, " +
                "net INTEGER NOT NULL DEFAULT 0, cause INTEGER NOT NULL DEFAULT 0, " +
                "note TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX idx_periods_session ON periods(session_id, start_ms)")
        createBuckets(db)
    }

    private fun createBuckets(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE buckets(ts INTEGER NOT NULL, session_id INTEGER NOT NULL, " +
                "checks INTEGER NOT NULL DEFAULT 0, fails INTEGER NOT NULL DEFAULT 0, " +
                "ping_sum INTEGER NOT NULL DEFAULT 0, ping_count INTEGER NOT NULL DEFAULT 0, " +
                "ping_min INTEGER, ping_max INTEGER, " +
                "jit_sum INTEGER NOT NULL DEFAULT 0, jit_count INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(ts, session_id))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE periods ADD COLUMN ping_sum INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE periods ADD COLUMN ping_count INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE periods ADD COLUMN net INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE periods ADD COLUMN cause INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE periods ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            createBuckets(db)
        }
    }

    private val pCols = arrayOf(
        "id", "session_id", "online", "start_ms", "end_ms", "ping_sum", "ping_count", "net", "cause", "note"
    )

    private fun Cursor.toPeriod() = Period(
        id = getLong(0),
        sessionId = getLong(1),
        online = getInt(2) == 1,
        startMs = getLong(3),
        endMs = if (isNull(4)) null else getLong(4),
        pingSum = getLong(5),
        pingCount = getInt(6),
        net = getInt(7),
        cause = getInt(8),
        note = getString(9) ?: "",
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
    fun recordState(sessionId: Long, online: Boolean, at: Long, net: Int, cause: Int) {
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
                put("net", net)
                put("cause", cause)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    /**
     * Records one check: updates the running period's ping totals and the per-minute bucket.
     * [prevLatency] is the previous successful latency (for jitter) or null.
     */
    @Synchronized
    fun recordCheck(sessionId: Long, ts: Long, ok: Boolean, latency: Long, prevLatency: Long?) {
        val db = writableDatabase
        val bucket = ts - ts % 60_000L
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR IGNORE INTO buckets(ts, session_id) VALUES(?, ?)",
                arrayOf<Any>(bucket, sessionId)
            )
            if (ok) {
                val jit = if (prevLatency != null) abs(latency - prevLatency) else 0L
                val jitN = if (prevLatency != null) 1L else 0L
                db.execSQL(
                    "UPDATE buckets SET checks = checks + 1, ping_sum = ping_sum + ?, ping_count = ping_count + 1, " +
                        "ping_min = MIN(COALESCE(ping_min, ?), ?), ping_max = MAX(COALESCE(ping_max, ?), ?), " +
                        "jit_sum = jit_sum + ?, jit_count = jit_count + ? WHERE ts = ? AND session_id = ?",
                    arrayOf<Any>(latency, latency, latency, latency, latency, jit, jitN, bucket, sessionId)
                )
                db.execSQL(
                    "UPDATE periods SET ping_sum = ping_sum + ?, ping_count = ping_count + 1 " +
                        "WHERE session_id = ? AND end_ms IS NULL",
                    arrayOf<Any>(latency, sessionId)
                )
            } else {
                db.execSQL(
                    "UPDATE buckets SET checks = checks + 1, fails = fails + 1 WHERE ts = ? AND session_id = ?",
                    arrayOf<Any>(bucket, sessionId)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    @Synchronized
    fun pruneBuckets(beforeMs: Long) {
        writableDatabase.delete("buckets", "ts < ?", arrayOf(beforeMs.toString()))
    }

    @Synchronized
    fun setNote(id: Long, note: String) {
        writableDatabase.update(
            "periods", ContentValues().apply { put("note", note.trim()) }, "id = ?", arrayOf(id.toString())
        )
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
            db.delete("buckets", "session_id = ?", arrayOf(id.toString()))
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
            db.execSQL("DELETE FROM buckets WHERE session_id IN (SELECT id FROM sessions WHERE end_ms IS NOT NULL)")
            db.delete("sessions", "end_ms IS NOT NULL", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    // ---------- backup / restore ----------

    @Synchronized
    fun backupTo(out: OutputStream) {
        val db = writableDatabase
        db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        File(db.path).inputStream().use { it.copyTo(out) }
        out.flush()
    }

    /** Replaces the whole database with the given backup. Returns false if the file is not a valid backup. */
    @Synchronized
    fun restoreFrom(input: InputStream, cacheDir: File): Boolean {
        val tmp = File(cacheDir, "restore.db")
        tmp.outputStream().use { input.copyTo(it) }
        val valid = try {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
                val tables = d.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sessions','periods')", null
                ).use { it.count }
                tables == 2 && d.version in 1..DB_VERSION
            }
        } catch (e: Exception) {
            false
        }
        if (!valid) {
            tmp.delete()
            return false
        }
        val path = writableDatabase.path
        close()
        File("$path-wal").delete()
        File("$path-shm").delete()
        File("$path-journal").delete()
        tmp.copyTo(File(path), overwrite = true)
        tmp.delete()
        writableDatabase // reopen (runs onUpgrade if the backup is older)
        bump()
        return true
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

    @Synchronized
    fun quality(sessionId: Long?, from: Long, to: Long): Quality {
        val sel = StringBuilder("ts >= ? AND ts <= ?")
        val args = mutableListOf((from - 60_000L).toString(), to.toString())
        if (sessionId != null) {
            sel.append(" AND session_id = ?")
            args.add(sessionId.toString())
        }
        return readableDatabase.rawQuery(
            "SELECT SUM(checks), SUM(fails), SUM(ping_sum), SUM(ping_count), MIN(ping_min), MAX(ping_max), " +
                "SUM(jit_sum), SUM(jit_count) FROM buckets WHERE $sel",
            args.toTypedArray()
        ).use { c ->
            if (!c.moveToFirst()) Quality.EMPTY
            else Quality(
                checks = c.getLong(0), fails = c.getLong(1), pingSum = c.getLong(2), pingCount = c.getLong(3),
                minPing = if (c.isNull(4)) null else c.getLong(4),
                maxPing = if (c.isNull(5)) null else c.getLong(5),
                jitSum = c.getLong(6), jitCount = c.getLong(7),
            )
        }
    }

    /** About [points] aggregated points between [from] and [to] (minimum one minute each). */
    @Synchronized
    fun chart(sessionId: Long?, from: Long, to: Long, points: Int = 80): List<ChartPoint> {
        val span = (to - from).coerceAtLeast(60_000L)
        val bucketMs = maxOf(60_000L, span / points)
        val sel = StringBuilder("ts >= ? AND ts <= ?")
        val args = mutableListOf(bucketMs.toString(), (from - 60_000L).toString(), to.toString())
        if (sessionId != null) {
            sel.append(" AND session_id = ?")
            args.add(sessionId.toString())
        }
        return readableDatabase.rawQuery(
            "SELECT ts / ? AS g, SUM(ping_sum), SUM(ping_count), MAX(ping_max), SUM(fails), SUM(checks) " +
                "FROM buckets WHERE $sel GROUP BY g ORDER BY g",
            args.toTypedArray()
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val cnt = c.getLong(2)
                    val checks = c.getLong(5)
                    add(
                        ChartPoint(
                            ts = c.getLong(0) * bucketMs + bucketMs / 2,
                            avg = if (cnt > 0) c.getLong(1) / cnt else null,
                            max = if (c.isNull(3)) null else c.getLong(3),
                            lossPct = if (checks > 0) c.getLong(4) * 100.0 / checks else 0.0,
                        )
                    )
                }
            }
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
                cv.put("ping_sum", prev.pingSum + next.pingSum)
                cv.put("ping_count", prev.pingCount + next.pingCount)
                cv.put("note", listOf(prev.note, next.note).filter { it.isNotBlank() }.joinToString(" | "))
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
