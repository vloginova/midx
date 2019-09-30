package com.vloginova.midx.util

import com.vloginova.midx.api.SearchResult
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

private val lineSeparatorRegex = Regex("\\r\\n|\\n|\\r")

/**
 * Performs fulltext search, treating all line separators in [text] and the file itself uniformly.
 *
 * @throws IOException If an I/O error occurs
 */
internal fun File.fullTextSearch(text: String, ignoreCase: Boolean = false): Collection<SearchResult> {
    if (text.isEmpty()) return emptyList()

    return if (text.contains(lineSeparatorRegex)) {
        fullTextSearchMultiLine(text, ignoreCase)
    } else {
        fullTextSearchSingleLine(text, ignoreCase)
    }
}

private fun File.fullTextSearchSingleLine(
    text: String,
    ignoreCase: Boolean,
    charset: Charset = Charsets.UTF_8
): Collection<SearchResult> {
    val searchResults = mutableListOf<SearchResult>()
    var lineNumber = 1
    forEachLine(charset) { line ->
        var textPosition = line.indexOf(text, ignoreCase = ignoreCase)
        while (textPosition != -1) {
            searchResults.add(SearchResult(this, line, lineNumber, textPosition, textPosition + text.length))
            textPosition = line.indexOf(text, textPosition + 1, ignoreCase)
        }
        lineNumber++
    }
    return searchResults
}

private fun File.fullTextSearchMultiLine(
    text: String,
    ignoreCase: Boolean,
    charset: Charset = Charsets.UTF_8
): Collection<SearchResult> {
    val lines = text.split(lineSeparatorRegex)
    val splitTextLength = lines.joinToString("\n").length

    val searchResults = mutableListOf<SearchResult>()
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
            searchResults.add(SearchResult(this, matchingText, matchLineNumber, startIndex, endIndex))
        }

        if (isFullMatch || !isLineMatch) {
            do {
                accumulatedLines.removeFirst()
            } while (!isPotentialMatch(accumulatedLines, lines, ignoreCase))
        }

        lineNumber++
    }
    return searchResults
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
