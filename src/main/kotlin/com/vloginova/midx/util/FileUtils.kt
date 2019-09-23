package com.vloginova.midx.util

import com.vloginova.midx.api.SearchResult
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
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

private val logger = KotlinLogging.logger {}

private val lineSeparatorRegex = Regex("\\r\\n|\\n|\\r")

fun File.forEachFile(process: (File) -> Unit) {
    val suspendProcess: suspend (File) -> Unit = { process(it) }
    runBlocking { this@forEachFile.forEachFileSuspend(suspendProcess) }
}

/**
 * Calls {@code process} for those files in the provided directory and all its subdirectories, which have text content
 * and can be read. A content type is defined by [Files.probeContentType], and it is considered to be text when it has
 * text/\* MIME type or it is listed in [otherTextMimeTypes].
 */
suspend fun File.forEachFileSuspend(process: suspend (File) -> Unit) {
    for (file in walk()) {
        if (!file.isFile) continue

        if (!file.hasTextContent()) continue

        if (!file.canRead()) {
            logger.warn("Cannot process file ${file.path}, skip")
            continue
        }

        process(file)
    }
}

private fun File.hasTextContent(): Boolean {
    val contentType = Files.probeContentType(toPath()) ?: ""
    return contentType.startsWith("text/") || contentType in otherTextMimeTypes
}

fun File.fullTextSearch(text: String, processMatch: (SearchResult) -> Unit) {
    if (text.isEmpty()) return

    if (text.contains(lineSeparatorRegex)) {
        fullTextSearchMultiLine(text, processMatch)
    } else {
        fullTextSearchSingleLine(text, processMatch)
    }
}

private fun File.fullTextSearchSingleLine(text: String, processMatch: (SearchResult) -> Unit) {
    forEachLine(Charsets.UTF_8) { line ->
        var textPosition = line.indexOf(text)
        while (textPosition != -1) {
            processMatch(SearchResult(path, line, textPosition, textPosition + text.length))
            textPosition = line.indexOf(text, textPosition + 1)
        }
    }
}

private fun File.fullTextSearchMultiLine(text: String, processMatch: (SearchResult) -> Unit) {
    val splitText = text.split(lineSeparatorRegex)
    val splitTextLength = splitText.joinToString("\n").length

    val accumulatedLines = LinkedList<String>()
    forEachLine(Charsets.UTF_8) { line ->
        accumulatedLines.add(line)

        if (accumulatedLines.size == splitText.size && line.startsWith(splitText.last())) {
            val matchingText = accumulatedLines.joinToString("\n")
            val startIndex = matchingText.indexOf(splitText.first())
            val endIndex = startIndex + splitTextLength
            processMatch(SearchResult(path, matchingText, startIndex, endIndex))
            accumulatedLines.removeFirst()
        }

        dropTillNotPotentialMatch(accumulatedLines, splitText)
    }
}

private fun dropTillNotPotentialMatch(accumulatedLines: LinkedList<String>, splitText: List<String>) {
    while (!accumulatedLines.isEmpty()
        && !(accumulatedLines.first.endsWith(splitText.first())
                && splitText.startsWith(accumulatedLines.subList(0, accumulatedLines.lastIndex)))
    ) {
        accumulatedLines.removeFirst()
    }
}

private fun List<String>.startsWith(another: List<String>): Boolean {
    if (this.size < another.size) return false

    if (this.isEmpty()) return true

    for (i in another.indices) {
        if (this[i] != another[i]) return false
    }
    return true
}
fun String.replaceFileSeparatorsWithLf(): String {
    return replace(lineSeparatorRegex, "\n")
}