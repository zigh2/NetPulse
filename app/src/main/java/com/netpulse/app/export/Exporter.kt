package com.netpulse.app.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.netpulse.app.R
import com.netpulse.app.data.Cause
import com.netpulse.app.data.Fmt
import com.netpulse.app.data.Net
import com.netpulse.app.data.Period
import com.netpulse.app.data.Session
import com.netpulse.app.data.computeStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CSV, XLSX (hand-written OOXML, no dependencies) and PDF (android.graphics.pdf) exporters. */
object Exporter {

    enum class Kind(val ext: String, val mime: String, val labelRes: Int) {
        CSV("csv", "text/csv", R.string.export_csv),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", R.string.export_xlsx),
        PDF("pdf", "application/pdf", R.string.export_pdf),
        PDF_OUTAGES("pdf", "application/pdf", R.string.export_outages_pdf),
    }

    fun export(ctx: Context, kind: Kind, sessions: List<Session>, periods: List<Period>): File {
        val now = System.currentTimeMillis()
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val file = File(dir, "netpulse_${if (kind == Kind.PDF_OUTAGES) "outages_" else ""}$stamp.${kind.ext}")
        val rows = periods.sortedWith(compareBy({ it.startMs }, { it.id }))
        val sess = sessions.sortedBy { it.startMs }
        when (kind) {
            Kind.CSV -> writeCsv(ctx, file, rows, now)
            Kind.XLSX -> writeXlsx(ctx, file, sess, rows, now)
            Kind.PDF -> writePdf(ctx, file, rows, now, outagesOnly = false)
            Kind.PDF_OUTAGES -> writePdf(ctx, file, rows, now, outagesOnly = true)
        }
        return file
    }

    private fun statusText(ctx: Context, online: Boolean) =
        ctx.getString(if (online) R.string.status_online else R.string.status_offline)

    private fun transportText(ctx: Context, net: Int): String = when (net) {
        Net.WIFI -> ctx.getString(R.string.net_wifi)
        Net.CELL -> ctx.getString(R.string.net_cell)
        Net.ETH -> ctx.getString(R.string.net_eth)
        Net.NONE -> ctx.getString(R.string.cause_no_network)
        Net.OTHER -> ctx.getString(R.string.net_other)
        else -> ""
    }

    private fun causeText(ctx: Context, cause: Int): String = when (cause) {
        Cause.NO_NETWORK -> ctx.getString(R.string.cause_no_network)
        Cause.NO_INTERNET -> ctx.getString(R.string.cause_no_internet)
        else -> ""
    }

    /** Short "Wi-Fi" / "Wi-Fi · No internet" / "No network" text used in the PDF. */
    private fun netShort(ctx: Context, p: Period): String = when {
        p.online -> transportText(ctx, p.net)
        p.cause == Cause.NO_NETWORK -> ctx.getString(R.string.cause_no_network)
        else -> listOf(transportText(ctx, p.net), causeText(ctx, p.cause)).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    // ------------------------------------------------------------------ CSV

    private fun csvEscape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun writeCsv(ctx: Context, file: File, rows: List<Period>, now: Long) {
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("\uFEFF") // BOM so Excel opens UTF-8 / Arabic correctly
            w.write(
                listOf(
                    R.string.col_no, R.string.col_session, R.string.col_status, R.string.col_start,
                    R.string.col_end, R.string.col_duration_s, R.string.col_duration, R.string.col_avg_ping,
                    R.string.col_network, R.string.col_cause, R.string.col_note
                ).joinToString(",") { csvEscape(ctx.getString(it)) }
            )
            w.write("\r\n")
            rows.forEachIndexed { i, p ->
                val d = p.durationMs(now)
                w.write(
                    listOf(
                        (i + 1).toString(),
                        p.sessionId.toString(),
                        statusText(ctx, p.online),
                        Fmt.dateTime(p.startMs),
                        p.endMs?.let { Fmt.dateTime(it) } ?: "",
                        (d / 1000).toString(),
                        Fmt.duration(d),
                        p.avgPingMs?.toString() ?: "",
                        transportText(ctx, p.net),
                        causeText(ctx, p.cause),
                        p.note,
                    ).joinToString(",") { csvEscape(it) }
                )
                w.write("\r\n")
            }
        }
    }

    // ------------------------------------------------------------------ XLSX

    private class C(val v: Any?, val style: Int = 0)

    private class Sheet(val name: String, val header: List<String>, val widths: List<Int>, val rows: List<List<C>>)

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 32 && ch != '\n' && ch != '\t' && ch != '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun sheetXml(s: Sheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("<cols>")
        s.widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sb.append("</cols><sheetData>")
        sb.append("""<row r="1">""")
        s.header.forEachIndexed { i, h ->
            sb.append("""<c r="${colName(i)}1" t="inlineStr" s="1"><is><t>${esc(h)}</t></is></c>""")
        }
        sb.append("</row>")
        s.rows.forEachIndexed { r, row ->
            val rn = r + 2
            sb.append("""<row r="$rn">""")
            row.forEachIndexed { i, c ->
                val ref = "${colName(i)}$rn"
                val st = if (c.style != 0) """ s="${c.style}"""" else ""
                when (val v = c.v) {
                    null -> Unit
                    is Number -> sb.append("""<c r="$ref"$st><v>$v</v></c>""")
                    else -> sb.append("""<c r="$ref" t="inlineStr"$st><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="4">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FF15803D"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FFB91C1C"/><name val="Calibri"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF1F3A5F"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private fun writeXlsx(ctx: Context, file: File, sessions: List<Session>, rows: List<Period>, now: Long) {
        val periodSheet = Sheet(
            ctx.getString(R.string.sheet_periods),
            listOf(
                R.string.col_no, R.string.col_session, R.string.col_status, R.string.col_start,
                R.string.col_end, R.string.col_duration_s, R.string.col_duration, R.string.col_avg_ping,
                R.string.col_network, R.string.col_cause, R.string.col_note
            ).map { ctx.getString(it) },
            listOf(6, 10, 14, 22, 22, 14, 14, 16, 16, 16, 40),
            rows.mapIndexed { i, p ->
                val d = p.durationMs(now)
                listOf(
                    C(i + 1), C(p.sessionId),
                    C(statusText(ctx, p.online), if (p.online) 2 else 3),
                    C(Fmt.dateTime(p.startMs)),
                    C(p.endMs?.let { Fmt.dateTime(it) } ?: ""),
                    C(d / 1000), C(Fmt.duration(d)), C(p.avgPingMs),
                    C(transportText(ctx, p.net)), C(causeText(ctx, p.cause)), C(p.note),
                )
            }
        )
        val bySession = rows.groupBy { it.sessionId }
        val sessionSheet = Sheet(
            ctx.getString(R.string.sheet_sessions),
            listOf(
                R.string.col_session, R.string.col_start, R.string.col_end, R.string.col_online_s,
                R.string.col_offline_s, R.string.col_outages, R.string.col_avg_ping
            ).map { ctx.getString(it) },
            listOf(10, 22, 22, 14, 14, 14, 16),
            sessions.filter { bySession.containsKey(it.id) }.map { s ->
                val st = computeStats(bySession[s.id].orEmpty(), now)
                listOf(
                    C(s.id), C(Fmt.dateTime(s.startMs)), C(s.endMs?.let { Fmt.dateTime(it) } ?: ""),
                    C(st.onlineMs / 1000), C(st.offlineMs / 1000), C(st.outages), C(st.avgPingMs),
                )
            }
        )
        val sheets = listOf(periodSheet, sessionSheet)

        ZipOutputStream(file.outputStream().buffered()).use { z ->
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val overrides = sheets.indices.joinToString("") {
                """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>$overrides</Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
            )
            val sheetTags = sheets.mapIndexed { i, s ->
                """<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
            }.joinToString("")
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheetTags</sheets></workbook>"""
            )
            val rels = sheets.indices.joinToString("") {
                """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
            }
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
            )
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    // ------------------------------------------------------------------ PDF

    private fun writePdf(ctx: Context, file: File, allRows: List<Period>, now: Long, outagesOnly: Boolean) {
        val pageW = 595
        val pageH = 842
        val margin = 36f
        val rowH = 22f
        // #, status, start, end, duration, network, avg ping
        val colX = floatArrayOf(margin + 6, margin + 30, margin + 96, margin + 196, margin + 296, margin + 366, margin + 455)

        val navy = Color.parseColor("#0B1220")
        val green = Color.parseColor("#15803D")
        val red = Color.parseColor("#B91C1C")
        val gray = Color.parseColor("#64748B")

        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 10f }
        val netP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 9f }
        val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gray; textSize = 9f }
        val headP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        val boldP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        val fillP = Paint().apply { style = Paint.Style.FILL }
        val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 13f; typeface = Typeface.DEFAULT_BOLD }

        // Outage report: the table lists only outages, the summary still covers everything.
        val rows = if (outagesOnly) allRows.filter { !it.online } else allRows

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            val p = page ?: return
            p.canvas.drawText(ctx.getString(R.string.pdf_page, pageNo), margin, pageH - 20f, smallP)
            p.canvas.drawText("NetPulse", pageW - margin - 40f, pageH - 20f, smallP)
            doc.finishPage(p)
            page = null
        }

        fun tableHeader() {
            val c = page!!.canvas
            fillP.color = Color.parseColor("#1F3A5F")
            c.drawRoundRect(margin, y, pageW - margin, y + rowH, 4f, 4f, fillP)
            val heads = listOf(
                R.string.col_no, R.string.col_status, R.string.col_start, R.string.col_end,
                R.string.col_duration, R.string.col_network, R.string.col_avg_ping
            )
            heads.forEachIndexed { i, h -> c.drawText(ctx.getString(h), colX[i], y + 15f, headP) }
            y += rowH
        }

        fun newPage(withHeader: Boolean) {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
            if (withHeader) tableHeader()
        }

        // ---- first page: title + summary
        newPage(false)
        var c = page!!.canvas
        fillP.color = navy
        c.drawRect(0f, 0f, pageW.toFloat(), 8f, fillP)
        y = margin + 14f
        c.drawText(
            ctx.getString(if (outagesOnly) R.string.pdf_title_outages else R.string.pdf_title),
            margin, y + 8f, titleP
        )
        y += 24f
        val first = allRows.minOfOrNull { it.startMs }
        val period = if (first != null) "${Fmt.dateTime(first)}  →  ${Fmt.dateTime(now)}" else ""
        c.drawText(ctx.getString(R.string.pdf_generated, Fmt.dateTime(now)), margin, y + 8f, smallP)
        if (period.isNotEmpty()) c.drawText(period, margin, y + 20f, smallP)
        y += 34f

        val st = computeStats(allRows, now)
        val boxW = (pageW - 2 * margin - 4 * 8f) / 5f
        val boxes = if (outagesOnly) listOf(
            Triple(R.string.stat_uptime, Fmt.percent(st.uptimePct), green),
            Triple(R.string.stat_total_outage, Fmt.duration(st.offlineMs), red),
            Triple(R.string.stat_outages, st.outages.toString(), red),
            Triple(R.string.stat_longest_outage, Fmt.duration(st.longestOutageMs), red),
            Triple(R.string.avg_ping, Fmt.ping(st.avgPingMs), navy),
        ) else listOf(
            Triple(R.string.stat_uptime, Fmt.percent(st.uptimePct), green),
            Triple(R.string.stat_online, Fmt.duration(st.onlineMs), green),
            Triple(R.string.stat_offline, Fmt.duration(st.offlineMs), red),
            Triple(R.string.stat_outages, st.outages.toString(), red),
            Triple(R.string.avg_ping, Fmt.ping(st.avgPingMs), navy),
        )
        boxes.forEachIndexed { i, (label, value, col) ->
            val x = margin + i * (boxW + 8f)
            fillP.color = Color.parseColor("#F1F5F9")
            c.drawRoundRect(x, y, x + boxW, y + 52f, 8f, 8f, fillP)
            fillP.color = col
            c.drawRoundRect(x, y, x + 4f, y + 52f, 2f, 2f, fillP)
            c.drawText(ctx.getString(label), x + 12f, y + 18f, smallP)
            bigP.color = col
            c.drawText(value, x + 12f, y + 40f, bigP)
        }
        y += 52f + 22f
        tableHeader()

        // ---- table rows
        rows.forEachIndexed { i, p ->
            val noteH = if (p.note.isNotBlank()) 13f else 0f
            if (y + rowH + noteH > pageH - 40f) newPage(true)
            c = page!!.canvas
            if (i % 2 == 0) {
                fillP.color = Color.parseColor("#F8FAFC")
                c.drawRect(margin, y, pageW - margin, y + rowH + noteH, fillP)
            }
            c.drawText((i + 1).toString(), colX[0], y + 15f, textP)
            boldP.color = if (p.online) green else red
            c.drawText(statusText(ctx, p.online), colX[1], y + 15f, boldP)
            c.drawText(Fmt.dateTime(p.startMs), colX[2], y + 15f, textP)
            c.drawText(p.endMs?.let { Fmt.dateTime(it) } ?: ctx.getString(R.string.now_label), colX[3], y + 15f, textP)
            c.drawText(Fmt.duration(p.durationMs(now)), colX[4], y + 15f, textP)
            c.drawText(netShort(ctx, p), colX[5], y + 15f, netP)
            c.drawText(Fmt.ping(p.avgPingMs), colX[6], y + 15f, textP)
            if (noteH > 0f) {
                c.drawText(ctx.getString(R.string.col_note) + ": " + p.note.take(100), colX[1], y + rowH + 6f, smallP)
            }
            y += rowH + noteH
        }
        finishPage()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
