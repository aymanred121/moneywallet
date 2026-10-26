package com.oriondev.moneywallet.next.data.csv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset

/** RFC 4180 CSV: fields holding the delimiter, quotes, line breaks or edge spaces are quoted (quotes doubled); rows end with CRLF. */
object Csv {
    /** Excel only reads a UTF-8 file as UTF-8 (Arabic included) when it starts with this mark. */
    const val BOM = "\uFEFF"

    fun write(rows: List<List<String>>, delimiter: Char = ','): String = buildString {
        rows.forEach { row ->
            row.forEachIndexed { i, f ->
                if (i > 0) append(delimiter)
                if (f.any { it == delimiter || it == '"' || it == '\r' || it == '\n' } || f != f.trim()) append('"').append(f.replace("\"", "\"\"")).append('"') else append(f)
            }
            append("\r\n")
        }
    }

    /** Parses [text] (a leading BOM is ignored, blank lines dropped); [delimiter] null = detect from the first line. */
    fun parse(text: String, delimiter: Char? = null): List<List<String>> {
        val s = text.removePrefix(BOM)
        val d = delimiter ?: detectDelimiter(s)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') { if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else quoted = false } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                d -> { row.add(field.toString()); field.setLength(0) }
                '\r', '\n' -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    row.add(field.toString()); field.setLength(0); rows.add(row); row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filterNot { r -> r.all { it.isBlank() } }
    }

    /** Comma, semicolon (Excel in comma-decimal locales) or tab, whichever the first line uses most outside quotes. */
    fun detectDelimiter(text: String): Char {
        val counts = HashMap<Char, Int>()
        var quoted = false
        for (c in text) {
            if (c == '"') quoted = !quoted
            else if (!quoted && (c == '\n' || c == '\r')) break
            else if (!quoted && (c == ',' || c == ';' || c == '\t')) counts[c] = (counts[c] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: ','
    }

    /** UTF-16 with BOM, else strict UTF-8, else Windows-1256 (older Arabic Excel exports). */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().removePrefix(BOM) } catch (e: CharacterCodingException) { String(bytes, Charset.forName("windows-1256")) }
    }
}
