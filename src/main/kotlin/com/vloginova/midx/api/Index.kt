package com.vloginova.midx.api

import java.io.File

interface Index {
    fun search(text: String, processMatch: (String, String, Int) -> Unit)
}
