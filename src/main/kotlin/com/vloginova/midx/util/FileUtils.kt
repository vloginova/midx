package com.vloginova.midx.util

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files

/**
 * MIME types that do not start with 'text/', but having text-based content. The list is not complete, it was composed
 * based on files from <a href="https://github.com/JetBrains/intellij-community">IntelliJ IDEA Community Edition</a>
 */
private val otherTextMimeTypes = setOf("image/svg+xml", "message/rfc822", "application/relax-ng-compact-syntax",
    "application/x-php", "application/x-perl", "application/x-shellscript", "application/xml-dtd",
    "application/pkix-cert", "application/x-yaml", "application/x-trash", "application/rtf", "application/xml",
    "application/x-desktop", "application/x-wine-extension-vbs", "application/xhtml+xml", "application/javascript",
    "application/xml-external-parsed-entity", "application/x-wine-extension-ini", "application/x-ruby",
    "application/xslt+xml"
)

private val logger = KotlinLogging.logger {}

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

fun File.fullTextSearch(text: String, processMatch: (String, String, Int) -> Unit) {
    forEachLine(Charsets.UTF_8) { line ->
        var textPosition = line.indexOf(text)
        while (textPosition != -1) {
            // TODO: exception handling
            processMatch(path, line, textPosition)
            textPosition = line.indexOf(text, textPosition + 1)
        }
    }
}