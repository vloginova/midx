package com.vloginova.midx.api

import java.io.File

interface Index {
    fun build()
    fun cancelBuild()
    // TODO: Match result as a separate type?
    fun search(text: String, processMatch: (String, String, Int) -> Unit)
    fun waitForBuildCompletion(): Boolean
}
