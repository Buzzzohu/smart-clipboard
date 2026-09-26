package com.smartclipboard.app.data

/** Each non-empty line becomes one entry; order is kept and repeats are removed. */
object BatchImportParser {
    fun parse(raw: String, stripSender: Boolean = false): ParsedBatch {
        val lines = raw.lines()
        val unique = LinkedHashSet<String>()
        lines.map { ImportTextCleaner.clean(it, stripSender) }
            .filter(String::isNotEmpty).forEach(unique::add)
        return ParsedBatch(unique.toList(), lines.size - unique.size)
    }
}

data class ParsedBatch(val entries: List<String>, val skipped: Int)
data class BatchImportResult(val added: Int, val skipped: Int)
