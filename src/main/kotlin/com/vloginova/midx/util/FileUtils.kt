package com.vloginova.midx.util

import com.vloginova.midx.api.SearchResult
import java.io.File
import java.nio.file.Files
import java.util.*

/**
 * MIME types that do not start with 'text/', but having text-based content. The list is not complete, it was composed
 * based on files from <a href="https://github.com/JetBrains/intellij-community">IntelliJ IDEA Community Edition</a>
 */
private val otherTextMimeTypes = setOf(
    "image/svg+xml", "message/rfc822", "application/relax-ng-compact-syntax",
    "application/x-php", "application/x-perl", "application/x-shellscript", "application/xml-dtd",
    "application/pkix-cert", "application/x-yaml", "application/x-trash", "application/rtf", "application/xml",
    "application/x-desktop", "application/x-wine-extension-vbs", "application/xhtml+xml", "application/javascript",
    "application/xml-external-parsed-entity", "application/x-wine-extension-ini", "application/x-ruby",
    "application/xslt+xml"
)

private val lineSeparatorRegex = Regex("\\r\\n|\\n|\\r")

/**
 * Consist of the files in the provided directory and all its subdirectories, which have text content.
 * [Files.probeContentType] defines a content type. A file has text content when it has a text/\* MIME type,
 * or the type is in [otherTextMimeTypes].
 */
internal fun File.walkTextFiles(): Sequence<File> =
    walk()
        .filter { file ->
            file.isFile && file.hasTextContent()
        }

/**
 * Performs fulltext search, treating all line separators in [text] and the file itself uniformly.
 */
internal fun File.fullTextSearch(text: String): Collection<SearchResult> {
    if (text.isEmpty()) return emptyList()

    return if (text.contains(lineSeparatorRegex)) {
        fullTextSearchMultiLine(text)
    } else {
        fullTextSearchSingleLine(text)
    }
}

internal fun File.hasTextContent(): Boolean {
    val contentType = Files.probeContentType(toPath()) ?: ""
    return contentType.startsWith("text/") || contentType in otherTextMimeTypes
}

private fun File.fullTextSearchSingleLine(text: String): Collection<SearchResult> {
    val searchResults = mutableListOf<SearchResult>()
    var lineNumber = 1
    forEachLine(Charsets.UTF_8) { line ->
        var textPosition = line.indexOf(text)
        while (textPosition != -1) {
            searchResults.add(SearchResult(this, line, lineNumber, textPosition, textPosition + text.length))
            textPosition = line.indexOf(text, textPosition + 1)
        }
        lineNumber++
    }
    return searchResults
}

private fun File.fullTextSearchMultiLine(text: String): Collection<SearchResult> {
    val splitText = text.split(lineSeparatorRegex)
    val splitTextLength = splitText.joinToString("\n").length

    val searchResults = mutableListOf<SearchResult>()
    val accumulatedLines = LinkedList<String>()

    var lineNumber = 1
    forEachLine(Charsets.UTF_8) { line ->
        accumulatedLines.add(line)

        if (accumulatedLines.size == splitText.size && line.startsWith(splitText.last())) {
            val matchingText = accumulatedLines.joinToString("\n")
            val startIndex = matchingText.indexOf(splitText.first())
            val endIndex = startIndex + splitTextLength
            val matchLineNumber = lineNumber - splitText.size + 1
            searchResults.add(SearchResult(this, matchingText, matchLineNumber, startIndex, endIndex))
            accumulatedLines.removeFirst()
        }

        dropWhileNoPotentialMatch(accumulatedLines, splitText)
        lineNumber++
    }
    return searchResults
}

private fun dropWhileNoPotentialMatch(accumulatedLines: LinkedList<String>, splitText: List<String>) {
    while (!accumulatedLines.isEmpty()
        && !(accumulatedLines.first.endsWith(splitText.first())
                && splitText.startsWith(accumulatedLines.subList(0, accumulatedLines.lastIndex)))
    ) {
        accumulatedLines.removeFirst()
    }
}

private fun List<String>.startsWith(other: List<String>): Boolean {
    if (this.size < other.size) return false

    if (this.isEmpty()) return true

    for (i in other.indices) {
        if (this[i] != other[i]) return false
    }
    return true
}

fun String.replaceFileSeparatorsWithLf(): String = replace(lineSeparatorRegex, "\n")