package com.vloginova.midx.util

import com.vloginova.midx.api.IOExceptionHandler
import com.vloginova.midx.api.SearchResult
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
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

/**
 * [textFileExtensions] and [binaryFileExtensions] are also gathered from
 * <a href="https://github.com/JetBrains/intellij-community">IntelliJ IDEA Community Edition</a>.
 * This shortcut helps to not use heavy [Files.probeContentType]
 */
private val textFileExtensions = setOf("java", "xml", "py", "kt", "html", "txt", "svg", "groovy", "json", "gradle")
private val binaryFileExtensions = setOf("class", "png")

private val lineSeparatorRegex = Regex("\\r\\n|\\n|\\r")

/**
 * Consist of the files in the provided directory and all its subdirectories, which have text content.
 */
internal fun Iterable<File>.walkFiles(ioExceptionHandler: IOExceptionHandler): Sequence<File> = sequence {
    forEach { file ->
        file.tryProcess(ioExceptionHandler) {
            when {
                file.isDirectory -> yieldAll(file.walk().onFail(ioExceptionHandler).filter { it.isFile })
                file.isFile -> yield(file)
                else -> throw IOException("Not a file or directory: $file")
            }
        }
    }
}

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

/**
 * Defines if the file has text content via [Files.probeContentType]. A file has text content when it has a text/\* MIME
 * type, or the type is in [otherTextMimeTypes]. Heuristic: for files with extensions [textFileExtensions] and
 * [binaryFileExtensions] gives an answer right away.
 *
 * @throws IOException If an I/O error occurs
 */
internal fun File.hasTextContent(): Boolean {
    if (extension in binaryFileExtensions) return false
    if (extension in textFileExtensions) return true

    val contentType = Files.probeContentType(toPath()) ?: ""
    return contentType.startsWith("text/") || contentType in otherTextMimeTypes
}

/**
 * Tries to execute [block]. In case [IOException] occurs, follows the provided [ioExceptionHandler]
 *
 * @throws IOException If an I/O error occurs, and it wasn't ignored according to [ioExceptionHandler]
 */
internal inline fun <T> File.tryProcess(
    ioExceptionHandler: IOExceptionHandler,
    block: () -> T?
): T? {
    return try {
        return block()
    } catch (e: IOException) {
        ioExceptionHandler(this, e)
        null
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
