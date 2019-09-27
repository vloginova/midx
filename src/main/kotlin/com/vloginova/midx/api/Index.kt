package com.vloginova.midx.api

import com.vloginova.midx.api.IOExceptionHandlingStrategy.Strategy.*
import java.io.File
import java.io.IOException

val DEFAULT_IO_EXCEPTION_HANDLING_STRATEGY = IOExceptionHandlingStrategy(IGNORE) { _, _ -> }

/**
 * Index represents a structure for effective searching of text data in the defined set of files.
 */
interface Index {
    /**
     * Searcher for [text] in [Index]. In case of I/O Exception applies provided [handlingStrategy]. Each match is
     * processed by [processMatch] in place, the context is unspecified.
     */
    suspend fun search(
        text: String,
        handlingStrategy: IOExceptionHandlingStrategy = DEFAULT_IO_EXCEPTION_HANDLING_STRATEGY,
        processMatch: (SearchResult) -> Unit
    )
}

/**
 * Represents an action of [IOException] processing. [callback] should is called for each occurred [IOException],
 * the context is unspecified.
 */
class IOExceptionHandlingStrategy(val strategy: Strategy, val callback: (File, IOException) -> Unit) {
    enum class Strategy { RETRY_THEN_IGNORE, RETRY_THEN_ABORT, ABORT, IGNORE }

    internal fun degrade(): IOExceptionHandlingStrategy {
        return when (strategy) {
            RETRY_THEN_IGNORE -> IOExceptionHandlingStrategy(IGNORE, callback)
            RETRY_THEN_ABORT -> IOExceptionHandlingStrategy(ABORT, callback)
            else -> throw IllegalStateException("Cannot degrade $strategy")
        }
    }
}

/**
 * Represents a result of the search via [Index.search]. [matchingText] is a piece of [File] content containing
 * at least a search text. [startIndex] (inclusive) and [endIndex] (exclusive) represent a position of the search text
 * in [matchingText]. [matchingText] is positioned in [File] on [lineNumber] line.
 */
data class SearchResult(
    val file: File,
    val matchingText: String,
    val lineNumber: Int,
    val startIndex: Int,
    val endIndex: Int
)
