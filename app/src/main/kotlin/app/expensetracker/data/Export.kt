package app.expensetracker.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.expensetracker.core.Categories
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Excel and PDF reports, written with what Android already has, so the app needs no extra libraries. */
object Export {
    enum class Range(val label: String) { THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), ALL("Everything") }

    fun select(txns: List<Txn>, range: Range, today: LocalDate = LocalDate.now()): List<Txn> {
        val (from, to) = when (range) {
            Range.THIS_MONTH -> today.withDayOfMonth(1) to today
            Range.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
            Range.THIS_YEAR -> today.withDayOfYear(1) to today
            Range.ALL -> LocalDate.ofEpochDay(0) to LocalDate.of(9999, 12, 31)
        }
        return txns.filter { it.epochDay in from.toEpochDay()..to.toEpochDay() }.sortedBy { it.epochDay * 100_000_000_000L + it.atMillis }
    }

    private fun rupeesPlain(paise: Long) = Reports.formatRupees(paise).replace(",", "")

    // Excel

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun col(i: Int) = ('A' + i).toString()

    fun writeXlsx(out: OutputStream, txns: List<Txn>, accountName: (Long) -> String) {
        val header = listOf("Date", "Type", "Amount (INR)", "Category", "Comment", "Merchant", "Wallet", "Tags", "Bank", "Original amount")
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        fun text(r: Int, c: Int, v: String) = sb.append("<c r=\"${col(c)}$r\" t=\"inlineStr\"><is><t>${esc(v)}</t></is></c>")
        sb.append("<row r=\"1\">"); header.forEachIndexed { c, h -> text(1, c, h) }; sb.append("</row>")
        txns.forEachIndexed { i, t ->
            val r = i + 2
            sb.append("<row r=\"$r\">")
            text(r, 0, LocalDate.ofEpochDay(t.epochDay).toString())
            text(r, 1, if (t.type == TxnType.DEBIT) "Spent" else "Received")
            sb.append("<c r=\"C$r\"><v>${rupeesPlain(t.amountPaise)}</v></c>")
            text(r, 3, t.category); text(r, 4, t.comment); text(r, 5, t.merchant.orEmpty())
            text(r, 6, accountName(t.accountId)); text(r, 7, t.tags.replace(",", ", ")); text(r, 8, t.bank.orEmpty())
            text(r, 9, if (t.currency != "INR") "${t.currency} ${rupeesPlain(t.origPaise)}" else "")
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")

        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) { zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry() }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Spends" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            )
            put("xl/worksheets/sheet1.xml", sb.toString())
        }
    }

    // PDF

    fun writePdf(out: OutputStream, title: String, txns: List<Txn>, accountName: (Long) -> String) {
        val counted = txns.filter { it.kind == TxnKind.NORMAL && it.category !in Categories.excludedFromTotals }
        val spent = counted.filter { it.type == TxnType.DEBIT }
        val spentTotal = spent.sumOf { it.amountPaise }
        val receivedTotal = counted.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise }
        val byCategory = spent.groupBy { it.category.ifEmpty { Reports.UNCATEGORIZED_LABEL } }.mapValues { e -> e.value.sumOf { it.amountPaise } }
            .entries.sortedByDescending { it.value }

        val doc = PdfDocument()
        val w = 595
        val h = 842
        val m = 36f
        val body = Paint().apply { textSize = 10f; isAntiAlias = true }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val grey = Paint(body).apply { color = 0xFF777777.toInt() }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            y = m + 12
        }
        fun line(left: String, rightText: String? = null, paint: Paint = body, rightPaint: Paint = right) {
            if (y > h - m) newPage()
            val c = page!!.canvas
            c.drawText(left.take(70), m, y, paint)
            if (rightText != null) c.drawText(rightText, w - m, y, rightPaint)
            y += 15f
        }
        fun rs(p: Long) = "Rs " + Reports.formatRupees(p)

        newPage()
        page!!.canvas.drawText("Spendr report", m, y, Paint(bold).apply { textSize = 20f }); y += 20f
        line(title, paint = grey)
        y += 6f
        line("Spent", rs(spentTotal), bold, rightBold)
        line("Received", rs(receivedTotal), bold, rightBold)
        line("Net", (if (receivedTotal - spentTotal < 0) "-" else "") + rs(Math.abs(receivedTotal - spentTotal)), bold, rightBold)
        y += 8f
        if (byCategory.isNotEmpty()) {
            line("Where it went", paint = bold)
            byCategory.take(12).forEach { (c, v) -> line(c, rs(v)) }
            y += 8f
        }
        line("All transactions", paint = bold)
        val fmt = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH)
        txns.forEach { t ->
            val who = listOf(t.merchant, t.comment).filter { !it.isNullOrBlank() }.joinToString(" - ").ifEmpty { t.bank ?: "Transaction" }
            val label = "${LocalDate.ofEpochDay(t.epochDay).format(fmt)}  ${who.take(34)}  ${t.category}".take(70)
            line(label, (if (t.type == TxnType.DEBIT) "-" else "+") + rs(t.amountPaise))
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }
}
