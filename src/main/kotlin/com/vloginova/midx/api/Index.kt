package com.vloginova.midx.api

import java.io.File

interface Index {
    // TODO: accept several files
    fun build(file: File)
    fun cancelBuild()
    // TODO: Match result as a separate type?
    fun search(text: String, processMatch: (String, String, Int) -> Unit)
}
