package com.vloginova.midx.util

import com.vloginova.midx.api.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

private val lineSeparatorRegex = Regex("\\r\\n|\\n|\\r")

/**
 * Performs the fulltext search, treating all line separators in [text] and the file itself uniformly.
 *
 * @throws IOException If an I/O error occurs
 */
internal fun File.searchFulltext(text: String, ignoreCase: Boolean = false): Flow<SearchResult> {
    if (text.isEmpty()) return emptyFlow()

    return if (text.contains(lineSeparatorRegex)) {
        searchFulltextMultiline(text, ignoreCase)
    } else {
        searchFulltextSingleLine(text, ignoreCase)
    }
}

private fun File.searchFulltextSingleLine(
    text: String,
    ignoreCase: Boolean,
    charset: Charset = Charsets.UTF_8
): Flow<SearchResult> =
    flow {
        var lineNumber = 1
        forEachLine(charset) { line ->
            var pos = line.indexOf(text, ignoreCase = ignoreCase)
            while (pos != -1) {
                val searchResult = SearchResult(this@searchFulltextSingleLine, line, lineNumber, pos, pos + text.length)
                emit(searchResult)
                pos = line.indexOf(text, pos + 1, ignoreCase)
            }
            lineNumber++
        }
    }


private fun File.searchFulltextMultiline(
    text: String,
    ignoreCase: Boolean,
    charset: Charset = Charsets.UTF_8
): Flow<SearchResult> =
    flow {
        val lines = text.split(lineSeparatorRegex)
        val splitTextLength = lines.joinToString("\n").length

        val accumulatedLines = LinkedList<String>()

        var lineNumber = 1
        forEachLine(charset) { line ->
            assert(accumulatedLines.size < lines.size) {
                "Invariant: accumulatedLines size must be less than lines size"
            }
            accumulatedLines.add(line)

            val isLineMatch = isLineMatchAt(accumulatedLines.lastIndex, accumulatedLines, lines, ignoreCase)
            val isFullMatch = isLineMatch && accumulatedLines.size == lines.size

            if (isFullMatch) {
                val matchingText = accumulatedLines.joinToString("\n")
                val startIndex = accumulatedLines.first().length - lines.first().length
                val endIndex = startIndex + splitTextLength
                val matchLineNumber = lineNumber - lines.size + 1
                emit(SearchResult(this@searchFulltextMultiline, matchingText, matchLineNumber, startIndex, endIndex))
            }

            if (isFullMatch || !isLineMatch) {
                do {
                    accumulatedLines.removeFirst()
                } while (!isPotentialMatch(accumulatedLines, lines, ignoreCase))
            }

            lineNumber++
        }
    }

private fun isPotentialMatch(accumulatedLines: List<String>, lines: List<String>, ignoreCase: Boolean): Boolean {
    return accumulatedLines.indices.all { i ->
        isLineMatchAt(i, accumulatedLines, lines, ignoreCase)
    }
}

private fun isLineMatchAt(
    i: Int,
    accumulatedLines: List<String>,
    lines: List<String>,
    ignoreCase: Boolean
): Boolean {
    assert(i < lines.size)
    return when (i) {
        0 -> accumulatedLines[i].endsWith(lines[i], ignoreCase)
        lines.lastIndex -> accumulatedLines[i].startsWith(lines[i], ignoreCase)
        else -> accumulatedLines[i].equals(lines[i], ignoreCase)
    }
}
