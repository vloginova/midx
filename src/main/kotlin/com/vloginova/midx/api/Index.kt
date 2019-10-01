package com.vloginova.midx.api

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Represents an action of [IOException] processing. Should be called for each occurred [IOException], the context is
 * unspecified.
 */
typealias IOExceptionHandler = (File, IOException) -> Unit

val IGNORE_DO_NOTHING: IOExceptionHandler = { _, _ -> }
val ABORT_DO_NOTHING: IOExceptionHandler = { _, e -> throw e }

/**
 * Index represents a structure for effective searching of text data in the defined set of files.
 */
interface Index {
    /**
     * Searches for [text] in [Index]. In case of an I/O exception applies provided [ioExceptionHandler].
     * Executes flow operations on the provided [context], or on a default one, if not specified.
     */
    fun search(
        text: String,
        ignoreCase: Boolean = false,
        ioExceptionHandler: IOExceptionHandler = IGNORE_DO_NOTHING,
        context: CoroutineContext = EmptyCoroutineContext
    ): Flow<SearchResult>

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
