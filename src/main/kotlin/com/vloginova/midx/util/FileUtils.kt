package com.vloginova.midx.util

import com.vloginova.midx.api.IOExceptionHandlingStrategy
import com.vloginova.midx.api.IOExceptionHandlingStrategy.Strategy.*
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
internal fun File.walkFiles(): Sequence<File> = walk().filter { it.isFile }

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
 * Tries to execute [block]. In case [IOException] occurs, follows the provided [handlingStrategy]
 *
 * @throws IOException If an I/O error occurs, and it wasn't ignored according to [handlingStrategy]
 */
internal fun <T> File.tryProcess(
    handlingStrategy: IOExceptionHandlingStrategy,
    block: () -> T?
): T? {
    return try {
        return block()
    } catch (e: IOException) {
        handlingStrategy.callback(this, e)
        when (handlingStrategy.strategy) {
            RETRY_THEN_IGNORE, RETRY_THEN_ABORT -> tryProcess(handlingStrategy.degrade(), block)
            ABORT -> throw e
            IGNORE -> null
        }
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
    val splitText = text.split(lineSeparatorRegex)
    val splitTextLength = splitText.joinToString("\n").length

    val searchResults = mutableListOf<SearchResult>()
    val accumulatedLines = LinkedList<String>()

    var lineNumber = 1
    forEachLine(charset) { line ->
        accumulatedLines.add(line)

        if (accumulatedLines.size == splitText.size && line.startsWith(splitText.last(), ignoreCase)) {
            val matchingText = accumulatedLines.joinToString("\n")
            val startIndex = matchingText.indexOf(splitText.first(), ignoreCase = ignoreCase)
            val endIndex = startIndex + splitTextLength
            val matchLineNumber = lineNumber - splitText.size + 1
            searchResults.add(SearchResult(this, matchingText, matchLineNumber, startIndex, endIndex))
            accumulatedLines.removeFirst()
        }

        dropWhileNoPotentialMatch(accumulatedLines, splitText, ignoreCase)
        lineNumber++
    }
    return searchResults
}

private fun dropWhileNoPotentialMatch(
    accumulatedLines: LinkedList<String>,
    splitText: List<String>,
    ignoreCase: Boolean
) {
    while (!accumulatedLines.isEmpty()
        && !(accumulatedLines.first.endsWith(splitText.first(), ignoreCase)
                && splitText.startsWith(accumulatedLines.subList(0, accumulatedLines.lastIndex), ignoreCase))
    ) {
        accumulatedLines.removeFirst()
    }
}

private fun List<String>.startsWith(other: List<String>, ignoreCase: Boolean): Boolean {
    if (this.size < other.size) return false

    if (this.isEmpty()) return true

    for (i in other.indices) {
        if (!this[i].equals(other[i], ignoreCase)) return false
    }
    return true
}

fun String.replaceFileSeparatorsWithLf(): String = replace(lineSeparatorRegex, "\n")