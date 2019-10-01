package com.vloginova.midx.util

import com.vloginova.midx.api.IOExceptionHandler
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Files

/**
 * MIME types that do not start with 'text/', but have text-based content. The list is not complete, it was composed
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
 * Determines if the receiver file has a text content type. For files with extensions [textFileExtensions] and
 * [binaryFileExtensions] gives an answer right away (heuristic). Otherwise, defines the MIME type
 * via [Files.probeContentType]. A file has text content when it has a text/\* MIME type, or the type is
 * in [otherTextMimeTypes].
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
 * Tries to execute [block]. In case [IOException] occurs, executes [ioExceptionHandler]
 *
 * @throws IOException If an I/O error occurs, and it wasn't swallowed by [ioExceptionHandler]
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

/**
 * Inline version of [kotlin.io.forEachLine]
 */
inline fun File.forEachLine(charset: Charset = Charsets.UTF_8, action: (line: String) -> Unit) {
    BufferedReader(InputStreamReader(FileInputStream(this), charset)).useLines { it.forEach(action) }
}
