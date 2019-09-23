package com.vloginova.midx.api

data class SearchResult(val filePath: String, val matchingText: String, val startIndex: Int, val endIndex: Int)

interface Index {
    fun search(text: String, processMatch: (SearchResult) -> Unit)
}
