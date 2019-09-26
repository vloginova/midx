package com.vloginova.midx.api

import java.io.File

data class SearchResult(val file: File, val matchingText: String, val startIndex: Int, val endIndex: Int)

interface Index {
    fun search(text: String, processMatch: (SearchResult) -> Unit)
}
