package com.vloginova.midx.util

import java.io.File

fun File.forEachFile(process: (File) -> Unit) {
    for (file in walk()) {
        if (!file.isFile) continue

        if (!file.canRead()) {
            println("Cannot process file ${file.path}, skip")
            continue
        }

        process(file)
    }
}
